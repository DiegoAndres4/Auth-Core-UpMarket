package com.pamplona.authcore.integration;

import com.jayway.jsonpath.JsonPath;
import com.pamplona.authcore.application.ports.out.DeletedUserRepositoryPort;
import com.pamplona.authcore.application.ports.out.EmailVerificationTokenRepositoryPort;
import com.pamplona.authcore.application.ports.out.UserRepositoryPort;
import com.pamplona.authcore.domain.DeletedUser;
import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.support.CapturingEmailSender;
import com.pamplona.authcore.support.TestEmailConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baja de cuentas: el registro se archiva en deleted_users y el usuario activo
 * (con sus tokens) desaparece, a traves de la interfaz REST.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-para-pruebas-de-integracion-1234567890-abcdef",
        "authcore.admin.password=admin-test-password",
        "authcore.public-url=http://localhost:5173",
        "spring.datasource.url=jdbc:h2:mem:authcore;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.mail.username="
})
@Import(TestEmailConfig.class)
@Transactional
class AccountDeletionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CapturingEmailSender emailSender;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private DeletedUserRepositoryPort deletedUserRepository;

    @Autowired
    private EmailVerificationTokenRepositoryPort tokenRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        emailSender.clear();
    }

    @Test
    void eliminarLaCuentaArchivaElRegistroYBorraUsuarioYTokens() throws Exception {
        long originalId = registerAndVerify("ana@x.co", "secreto1");
        String rawToken = rawToken(emailSender.sentTo("ana@x.co").get(0).link());
        String tokenHash = sha256Hex(rawToken);
        String jwt = login("ana@x.co", "secreto1");
        assertTrue(userRepository.findByUsername("ana@x.co").isPresent());
        assertTrue(tokenRepository.findByTokenHash(tokenHash).isPresent());

        mockMvc.perform(delete("/api/auth/users/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());

        assertTrue(userRepository.findByUsername("ana@x.co").isEmpty());
        assertTrue(tokenRepository.findByTokenHash(tokenHash).isEmpty());

        DeletedUser archived = archivedByOriginalId(originalId);
        assertEquals("ana@x.co", archived.username());
        assertEquals(Set.of(Role.USER), archived.roles());
        assertTrue(archived.emailVerified());
        assertFalse(archived.blocked());
        assertNull(archived.createdAt());
        assertNotNull(archived.deletedAt());
        assertEquals("SELF", archived.deletedBy());
        assertEquals("BAJA_USUARIO", archived.reason());
    }

    @Test
    void registrarDeNuevoElMismoCorreoCreaCuentaNuevaYNoTocaElArchivo() throws Exception {
        long originalId = registerAndVerify("ana@x.co", "secreto1");
        deleteMe(login("ana@x.co", "secreto1"));
        DeletedUser before = archivedByOriginalId(originalId);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("ana@x.co", "secreto1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andReturn();

        long newId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertNotEquals(originalId, newId);
        assertEquals(before, archivedByOriginalId(originalId));
        assertEquals(1, deletedUserRepository.findAllNewestFirst().size());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ana@x.co", "secreto1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NO_VERIFICADO"));
    }

    @Test
    void loginConCredencialesDeCuentaEliminadaDevuelve401() throws Exception {
        registerAndVerify("ana@x.co", "secreto1");
        deleteMe(login("ana@x.co", "secreto1"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ana@x.co", "secreto1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contrasena incorrectos"));
    }

    @Test
    void adminListaLosArchivadosMasRecientesPrimeroYNoAdminRecibe403() throws Exception {
        long anaId = registerAndVerify("ana@x.co", "secreto1");
        deleteMe(login("ana@x.co", "secreto1"));
        long betoId = registerAndVerify("beto@x.co", "secreto1");
        deleteMe(login("beto@x.co", "secreto1"));

        registerAndVerify("carla@x.co", "secreto1");
        String carlaToken = login("carla@x.co", "secreto1");

        mockMvc.perform(get("/api/auth/users/deleted")
                        .header("Authorization", "Bearer " + carlaToken))
                .andExpect(status().isForbidden());

        MvcResult archivedResult = mockMvc.perform(get("/api/auth/users/deleted")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> archived = JsonPath.read(
                archivedResult.getResponse().getContentAsString(), "$");
        assertEquals(2, archived.size());
        assertEquals(betoId, ((Number) archived.get(0).get("originalUserId")).longValue());
        assertEquals(anaId, ((Number) archived.get(1).get("originalUserId")).longValue());
        assertEquals("beto@x.co", archived.get(0).get("username"));
        assertEquals("SELF", archived.get(0).get("deletedBy"));
        assertEquals("BAJA_USUARIO", archived.get(0).get("reason"));
        assertEquals(List.of("USER"), archived.get(0).get("roles"));

        MvcResult activeResult = mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        String activeBody = activeResult.getResponse().getContentAsString();
        assertFalse(activeBody.contains("ana@x.co"));
        assertFalse(activeBody.contains("beto@x.co"));
        assertTrue(activeBody.contains("carla@x.co"));
    }

    private long registerAndVerify(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, password)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        String link = emailSender.sentTo(username).get(emailSender.sentTo(username).size() - 1).link();
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken(link) + "\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private void deleteMe(String jwt) throws Exception {
        mockMvc.perform(delete("/api/auth/users/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNoContent());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String adminToken() throws Exception {
        return login("admin", "admin-test-password");
    }

    private DeletedUser archivedByOriginalId(long originalId) {
        return deletedUserRepository.findAllNewestFirst().stream()
                .filter(deleted -> deleted.originalUserId().equals(originalId))
                .findFirst()
                .orElseThrow();
    }

    private static String registerBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String rawToken(String link) {
        int index = link.indexOf("token=");
        return link.substring(index + "token=".length());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
