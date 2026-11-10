package com.pamplona.authcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Prueba de integración: solo corre si se define RUN_DB_TESTS=true y hay un MySQL accesible (ver application.yml). */
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@SpringBootTest
class AuthcoreServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
