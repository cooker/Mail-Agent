package io.mailagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class SecretCipherTest {
    @TempDir Path directory;
    @Test void generatesPrivateKeyAndReusesItAcrossRestarts() throws Exception {
        var cipher=new SecretCipher(directory.toString());
        Path file=directory.resolve(".credentials.key");
        byte[] key=Files.readAllBytes(file);
        String encrypted=cipher.encrypt("private-授权码");
        assertThat(encrypted).doesNotContain("private").isNotEqualTo(cipher.encrypt("private-授权码"));
        var restarted=new SecretCipher(directory.toString());
        assertThat(restarted.decrypt(encrypted)).isEqualTo("private-授权码");
        assertThat(Files.readAllBytes(file)).isEqualTo(key);
        if (Files.getFileStore(directory).supportsFileAttributeView("posix"))
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        byte[] tampered=Base64.getDecoder().decode(encrypted); tampered[15]^=1;
        assertThatThrownBy(()->restarted.decrypt(Base64.getEncoder().encodeToString(tampered)))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void neverOverwritesAnInvalidExistingKey() throws Exception {
        Path file=directory.resolve(".credentials.key"); Files.writeString(file,"invalid-key");
        assertThatThrownBy(()->new SecretCipher(directory.toString())).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(file)).isEqualTo("invalid-key");
    }
}
