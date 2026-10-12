package com.bcosmos.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;


@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Environment environment;

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void testProfileKeepsGlobalSettingsAndNeverTheDevelopmentDatabase() throws Exception {
		assertThat(this.environment.acceptsProfiles(Profiles.of("test"))).isTrue();

		// application.yml: the same in every environment.
		assertThat(this.environment.getProperty("spring.application.name")).isEqualTo("backend");
		assertThat(this.environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(this.environment.getProperty("spring.liquibase.change-log"))
				.isEqualTo("classpath:db/changelog/db.changelog-master.yaml");

		// application-test.yml on the test classpath.
		assertThat(this.environment.getProperty("bcosmos.repository.limits.max-files", Integer.class)).isEqualTo(500);
		assertThat(this.environment.getProperty("bcosmos.repository.allowed-roots")).contains("sample-data");

		// application-dev.yml must not be active: no localhost URL from a profile file, and the connection comes
		// from the container instead.
		assertThat(this.environment.getProperty("spring.datasource.url")).isNull();
		try (Connection connection = this.dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL())
					.isNotEqualTo("jdbc:postgresql://localhost:5432/bcosmos");
		}
	}

	@Test
	void liquibaseEnablesPgvector() {
		String extVersion = this.jdbcTemplate.queryForObject(
				"SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
		assertThat(extVersion).isNotBlank();
	}

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
