package com.bcosmos.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 0 acceptance, re-run against the real application context after the phase 1 wiring landed.
 *
 * <p>This test intentionally boots the <em>whole</em> application, not a trimmed-down slice: the analysis job
 * wiring (run service, runner, dispatcher) is only proven to be acyclic when the real context starts. Excluding
 * those beans here would hide exactly the failure this test exists to catch.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	/** Phase 0 done-when: the first Liquibase changeset really enabled pgvector. */
	@Test
	void liquibaseEnablesPgvector() {
		String extVersion = this.jdbcTemplate.queryForObject(
				"SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
		assertThat(extVersion).isNotBlank();
	}

	/** Phase 0 done-when: GET /actuator/health returns UP. */
	@Test
	void healthEndpointIsUp() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/actuator/health")).GET().build();
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body().replaceAll("\\s+", "")).contains("\"status\":\"UP\"");
		}
	}

}
