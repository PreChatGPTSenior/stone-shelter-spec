package lab.stoneshelter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StonePhotoDraftMigrationTest extends IntegrationTest {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcConnectionDetails jdbcConnectionDetails;

    @Test
    void upgradesExistingStonePhotosAndCleanupWithoutChangingTheirIdentityOrKeys() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        try (Connection connection = DriverManager.getConnection(jdbcConnectionDetails.getJdbcUrl(),
                jdbcConnectionDetails.getUsername(), jdbcConnectionDetails.getPassword())) {
            connection.setSchema(schema);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            try (Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(3, new Contexts(), new LabelExpression());
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO stone (id, name, stone_type, size, adoption_status, admission_date, photo_url)
                            VALUES (41, 'Legacy', 'GRANITE', 'SMALL', 'AVAILABLE', now(), 'https://example.org/legacy.png'),
                                   (42, 'Other legacy', 'MARBLE', 'MEDIUM', 'AVAILABLE', now(), NULL)
                            """);
                    statement.executeUpdate("INSERT INTO stone_photo (id, stone_id, object_key, added_at, position) VALUES (51, 41, '41/original-image', now(), 0)");
                    statement.executeUpdate("INSERT INTO photo_cleanup (object_key, available_at) VALUES ('old-orphan', now())");
                }
                liquibase.update(new Contexts(), new LabelExpression());
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet resultSet = statement.executeQuery("SELECT id, photo_url, storage_uuid FROM stone ORDER BY id")) {
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getLong("id")).isEqualTo(41);
                        assertThat(resultSet.getString("photo_url")).isEqualTo("https://example.org/legacy.png");
                        UUID firstStorageUuid = resultSet.getObject("storage_uuid", UUID.class);
                        assertThat(firstStorageUuid).isNotNull();
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getLong("id")).isEqualTo(42);
                        assertThat(resultSet.getObject("storage_uuid", UUID.class)).isNotNull().isNotEqualTo(firstStorageUuid);
                    }
                    try (ResultSet resultSet = statement.executeQuery("SELECT * FROM stone_photo WHERE id = 51")) {
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getLong("stone_id")).isEqualTo(41);
                        assertThat(resultSet.getString("object_key")).isEqualTo("41/original-image");
                        assertThat(resultSet.getBoolean("copy_ready")).isTrue();
                        assertThat(resultSet.getObject("draft_id")).isNull();
                        assertThat(resultSet.getInt("position")).isZero();
                    }
                    try (ResultSet resultSet = statement.executeQuery("SELECT bucket_type FROM photo_cleanup WHERE object_key = 'old-orphan'")) {
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getString("bucket_type")).isEqualTo("PERMANENT");
                    }
                    statement.executeUpdate("INSERT INTO photo_cleanup (object_key, bucket_type, available_at) VALUES ('old-orphan', 'DRAFT', now())");
                    try (ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM photo_cleanup WHERE object_key = 'old-orphan'")) {
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getInt(1)).isEqualTo(2);
                    }
                }
            }
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
