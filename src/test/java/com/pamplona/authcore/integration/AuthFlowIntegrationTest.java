package com.pamplona.authcore.integration;

import com.jayway.jsonpath.JsonPath;
import com.pamplona.authcore.application.ports.out.EmailVerificationTokenRepositoryPort;
import com.pamplona.authcore.application.ports.out.PasswordHasherPort;
import com.pamplona.authcore.application.ports.out.UserRepositoryPort;
import com.pamplona.authcore.domain.EmailVerificationToken;
import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.domain.User;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de verificacion de correo a traves de la interfaz REST publica.
 * Usa H2 en memoria y un adaptador de correo que captura los envios (sin red).
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
class AuthFlowIntegrationTest {

    private static final String PUBLIC_URL = "http://localhost:5173";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CapturingEmailSender emailSender;

    @Autowired
    private UserRepositoryPort userRepository;

    @Autowired
    private PasswordHasherPort passwordHasher;

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
    void registrarDevuelveUsuarioNoVerificadoSinTokenYEnviaCorreo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("ana@x.co", "secreto1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("ana@x.co"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        long userId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        assertFalse(userRepository.findById(userId).orElseThrow().isEmailVerified());

        assertEquals(1, emailSender.sentTo("ana@x.co").size());
        String link = emailSender.sentTo("ana@x.co").get(0).link();
        assertTrue(link.startsWith(PUBLIC_URL + "/#/verificar?token="));

        String rawToken = rawToken(link);
        EmailVerificationToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken)).orElseThrow();
        assertEquals(64, token.getTokenHash().length());
        assertFalse(token.isUsed());
        assertTrue(token.getExpiresAt().isAfter(Instant.now().plusSeconds(23 * 3600)));
    }

    @Test
    void loginNoVerificadoDevuelve403EmailNoVerificado() throws Exception {
        register("ana@x.co", "secreto1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ana@x.co", "secreto1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NO_VERIFICADO"));
    }

    @Test
    void loginConPasswordIncorrectaDevuelve401() throws Exception {
        register("ana@x.co", "secreto1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ana@x.co", "otra-clave")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verificarCorreoPermiteLogin() throws Exception {
        registerAndVerify("ana@x.co", "secreto1");

        String token = login("ana@x.co", "secreto1");
        assertFalse(token.isBlank());
    }

    @Test
    void verificarTokenUsadoDevuelveTokenInvalido() throws Exception {
        register("ana@x.co", "secreto1");
        String token = rawToken(emailSender.sentTo("ana@x.co").get(0).link());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALIDO"));
    }

    @Test
    void verificarTokenBasuraDevuelveTokenInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("esto-no-es-un-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALIDO"));
    }

    @Test
    void verificarTokenExpiradoDevuelveTokenExpirado() throws Exception {
        long userId = register("ana@x.co", "secreto1");
        String rawToken = "token-expirado-de-prueba";
        tokenRepository.save(new EmailVerificationToken(
                null,
                userId,
                sha256Hex(rawToken),
                Instant.now().minusSeconds(60),
                null,
                Instant.now().minusSeconds(120)));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRADO"));
    }

    @Test
    void reenvioUsuarioDesconocidoDevuelve202SinCorreo() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameBody("nadie@x.co")))
                .andExpect(status().isAccepted());

        assertTrue(emailSender.sent().isEmpty());
    }

    @Test
    void reenvioUsuarioNoVerificadoSinTokenPrevioEnviaCorreo() throws Exception {
        userRepository.save(new User(
                null, "pendiente@x.co", passwordHasher.hash("secreto1"), Set.of(Role.USER), false, false));

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameBody("pendiente@x.co")))
                .andExpect(status().isAccepted());

        assertEquals(1, emailSender.sentTo("pendiente@x.co").size());
    }

    @Test
    void reenvioDentroDeSesentaSegundosNoEnviaOtroCorreo() throws Exception {
        register("ana@x.co", "secreto1");
        assertEquals(1, emailSender.sentTo("ana@x.co").size());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameBody("ana@x.co")))
                .andExpect(status().isAccepted());

        assertEquals(1, emailSender.sentTo("ana@x.co").size());
    }

    @Test
    void adminBootstrapPuedeIniciarSesionPorqueEstaVerificado() throws Exception {
        String token = login("admin", "admin-test-password");
        assertFalse(token.isBlank());

        mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginConCuentaBloqueadaDevuelve403CuentaBloqueada() throws Exception {
        userRepository.save(new User(
                null, "bloqueado@x.co", passwordHasher.hash("secreto1"), Set.of(Role.USER), true, true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("bloqueado@x.co", "secreto1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void eliminarCuentaBorraTambienLosTokensDeVerificacion() throws Exception {
        registerAndVerify("ana@x.co", "secreto1");
        String token = login("ana@x.co", "secreto1");
        String rawToken = rawToken(emailSender.sentTo("ana@x.co").get(0).link());
        String hash = sha256Hex(rawToken);
        assertTrue(tokenRepository.findByTokenHash(hash).isPresent());

        mockMvc.perform(delete("/api/auth/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertTrue(tokenRepository.findByTokenHash(hash).isEmpty());
    }

    private long register(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void registerAndVerify(String username, String password) throws Exception {
        register(username, password);
        String token = rawToken(emailSender.sentTo(username).get(0).link());
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String registerBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String tokenBody(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private String usernameBody(String username) {
        return "{\"username\":\"" + username + "\"}";
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
