package com.pamplona.authcore.integration;

import com.jayway.jsonpath.JsonPath;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloqueo de usuarios por parte de un ADMIN, a traves de la interfaz REST.
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
class AdminBlockingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CapturingEmailSender emailSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        emailSender.clear();
    }

    @Test
    void listarUsuariosIncluyeEmailVerifiedYBlocked() throws Exception {
        String adminToken = adminToken();

        MvcResult result = mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> admin = findUser(result.getResponse().getContentAsString(), "admin");
        assertEquals(Boolean.TRUE, admin.get("emailVerified"));
        assertEquals(Boolean.FALSE, admin.get("blocked"));
    }

    @Test
    void usuarioNoAdminNoPuedeBloquear() throws Exception {
        long userId = registerAndVerify("ana@x.co", "secreto1");
        String userToken = login("ana@x.co", "secreto1");

        mockMvc.perform(patch("/api/auth/users/" + userId + "/blocked")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminBloqueaUsuarioYSuLoginDevuelveCuentaBloqueada() throws Exception {
        long userId = registerAndVerify("ana@x.co", "secreto1");

        mockMvc.perform(patch("/api/auth/users/" + userId + "/blocked")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.emailVerified").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("ana@x.co", "secreto1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void adminDesbloqueaUsuarioYPuedeVolverAEntrar() throws Exception {
        long userId = registerAndVerify("ana@x.co", "secreto1");
        String adminToken = adminToken();

        mockMvc.perform(patch("/api/auth/users/" + userId + "/blocked")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/auth/users/" + userId + "/blocked")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));

        String token = login("ana@x.co", "secreto1");
        assertFalse(token.isBlank());
    }

    @Test
    void adminNoPuedeBloquearseASiMismo() throws Exception {
        String adminToken = adminToken();
        long adminId = userIdByUsername(adminToken, "admin");

        mockMvc.perform(patch("/api/auth/users/" + adminId + "/blocked")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_PUEDES_BLOQUEARTE"));
    }

    @Test
    void adminNoPuedeBloquearAOtroAdmin() throws Exception {
        long otherAdminId = registerAndVerify("otro-admin@x.co", "secreto1");
        String adminToken = adminToken();

        mockMvc.perform(post("/api/auth/users/" + otherAdminId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/auth/users/" + otherAdminId + "/blocked")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_SE_BLOQUEA_ADMIN"));
    }

    @Test
    void bloquearUsuarioInexistenteDevuelve404() throws Exception {
        mockMvc.perform(patch("/api/auth/users/999999/blocked")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocked\":true}"))
                .andExpect(status().isNotFound());
    }

    private String adminToken() throws Exception {
        return login("admin", "admin-test-password");
    }

    private long registerAndVerify(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, password)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        String link = emailSender.sentTo(username).get(0).link();
        String rawToken = link.substring(link.indexOf("token=") + "token=".length());
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        return id;
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long userIdByUsername(String adminToken, String username) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> user = findUser(result.getResponse().getContentAsString(), username);
        return ((Number) user.get("id")).longValue();
    }

    private static Map<String, Object> findUser(String body, String username) {
        List<Map<String, Object>> users = JsonPath.read(body, "$");
        return users.stream()
                .filter(user -> username.equals(user.get("username")))
                .findFirst()
                .orElseThrow();
    }

    private static String registerBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
