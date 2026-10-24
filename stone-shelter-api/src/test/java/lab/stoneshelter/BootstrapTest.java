package lab.stoneshelter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.ollama.base-url=http://127.0.0.1:1")
@AutoConfigureRestTestClient
class BootstrapTest extends IntegrationTest {
    @Autowired
    private RestTestClient restClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startsAndReportsHealthy() {
        restClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void liquibaseEnablesVectorWithoutCreatingVectorStoreTables() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class))
                .isEqualTo("0.8.6");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id = '0014-enable-vector-extension'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE udt_name = 'vector'", Integer.class))
                .isZero();
    }
}
