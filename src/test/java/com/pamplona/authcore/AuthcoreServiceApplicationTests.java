package com.pamplona.authcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Prueba de integración: solo corre si se define RUN_DB_TESTS=true y hay un MySQL accesible (ver application.yml). */
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@SpringBootTest(properties = {
		"JWT_SECRET=test-secret-para-pruebas-de-integracion-1234567890",
		"authcore.admin.password=test-admin-password"
})
class AuthcoreServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
