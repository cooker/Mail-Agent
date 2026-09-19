package io.mailagent;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;

@Component
public class SecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.data-dir}") String dataDir) {
        try {
            Path directory=Path.of(dataDir).toAbsolutePath();
            Files.createDirectories(directory);
            Path keyFile=directory.resolve(".credentials.key");
            boolean posix=Files.getFileStore(directory).supportsFileAttributeView("posix");
            var permissions=PosixFilePermissions.fromString("rw-------");
            FileAttribute<?>[] attributes=posix
                    ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(permissions)} : new FileAttribute<?>[0];
            try (var channel=Files.newByteChannel(keyFile,
                    Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),attributes)) {
                byte[] generated=new byte[32]; random.nextBytes(generated);
                ByteBuffer buffer=ByteBuffer.wrap(Base64.getEncoder().encode(generated));
                while (buffer.hasRemaining()) channel.write(buffer);
            } catch (FileAlreadyExistsException ignored) { /* Reuse the same key across restarts. */ }
            if (posix) Files.setPosixFilePermissions(keyFile,permissions);
            byte[] bytes=Base64.getDecoder().decode(Files.readString(keyFile).strip());
            if (bytes.length!=32) throw new IllegalArgumentException();
            key=new SecretKeySpec(bytes,"AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法读取或创建本地密钥文件，请检查 DATA_DIR/.credentials.key 及目录权限；不要删除已有密钥");
        }
    }
    public String encrypt(String value) {
        try {
            byte[] nonce=new byte[12]; random.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException("凭据加密失败"); }
    }
    public String decrypt(String value) {
        try {
            byte[] data=Base64.getDecoder().decode(value);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,data,0,12));
            return new String(cipher.doFinal(data,12,data.length-12),StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("凭据解密失败，请恢复本地密钥文件或重新录入邮箱凭据"); }
    }
}
