package io.mailagent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class CredentialMigrationTest {
    @Test void legacyCiphertextIsPreservedButCannotBeUsedForAuthentication() throws Exception {
        String url="jdbc:h2:mem:credential_migration;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
        try (var connection=DriverManager.getConnection(url,"sa",""); var statement=connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO mail_accounts
                (name,email,imap_port,smtp_port,enabled,history_days,last_uid,uid_validity,reset_required,imap_secret,smtp_secret)
                VALUES ('existing','existing@example.com',993,465,TRUE,7,123,456,FALSE,'old-imap-ciphertext','old-smtp-ciphertext')
                """);
        }
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        try (var connection=DriverManager.getConnection(url,"sa",""); var statement=connection.createStatement();
             var rows=statement.executeQuery("SELECT * FROM mail_accounts")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("enabled")).isFalse();
            assertThat(rows.getString("imap_secret")).isNull();
            assertThat(rows.getString("smtp_secret")).isNull();
            assertThat(rows.getString("legacy_imap_secret")).isEqualTo("old-imap-ciphertext");
            assertThat(rows.getString("legacy_smtp_secret")).isEqualTo("old-smtp-ciphertext");
            assertThat(rows.getLong("last_uid")).isEqualTo(123);
            assertThat(rows.getLong("uid_validity")).isEqualTo(456);
            assertThat(rows.getString("last_error")).contains("重新输入");
        }
    }
}
