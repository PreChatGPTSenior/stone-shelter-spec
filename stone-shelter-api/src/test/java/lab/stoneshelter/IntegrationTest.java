package lab.stoneshelter;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

abstract class IntegrationTest {
    @ServiceConnection
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector@sha256:78bf48b801e792f99e3ac62b5036fd3876e9be48afda16c1e331af1c75ceb2ff")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        // JVM-wide lifecycle: JUnit must not stop it after an individual test class.
        // Testcontainers' Ryuk cleans up the container when the test JVM exits.
        DATABASE.start();
    }

}
