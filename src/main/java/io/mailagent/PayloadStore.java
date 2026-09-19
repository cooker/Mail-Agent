package io.mailagent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.util.*;

@Component
public class PayloadStore {
    private final Path directory;
    public PayloadStore(@Value("${app.data-dir}") String directory) throws IOException {
        this.directory = Path.of(directory).toAbsolutePath().resolve("spool");
        Files.createDirectories(this.directory);
        if (Files.getFileStore(this.directory).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(this.directory, PosixFilePermissions.fromString("rwx------"));
    }
    public String save(MimeMessage message) throws Exception {
        String name = UUID.randomUUID() + ".eml";
        Path temp = Files.createTempFile(directory, "incoming-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { message.writeTo(out); }
            Files.move(temp, path(name), StandardCopyOption.ATOMIC_MOVE);
            return name;
        } finally { Files.deleteIfExists(temp); }
    }
    public MimeMessage read(String name) throws Exception {
        try (InputStream input = Files.newInputStream(path(name))) {
            return new MimeMessage(Session.getInstance(new Properties()), input);
        }
    }
    public boolean exists(String name) { return name != null && Files.isRegularFile(path(name)); }
    public void delete(String name) throws IOException { if (name != null) Files.deleteIfExists(path(name)); }
    private Path path(String name) {
        if (name == null || !name.matches("[0-9a-f-]{36}\\.eml")) throw new IllegalArgumentException("无效邮件文件标识");
        return directory.resolve(name);
    }
}
