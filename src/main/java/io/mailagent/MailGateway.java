package io.mailagent;

import jakarta.mail.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import java.util.Properties;

@Component
public class MailGateway {
    private final SecretCipher cipher;
    public MailGateway(SecretCipher cipher) { this.cipher=cipher; }
    protected Properties properties(String protocol, TlsMode mode) {
        Properties p = new Properties();
        String prefix = "mail." + protocol + ".";
        p.setProperty(prefix + "connectiontimeout", "10000");
        p.setProperty(prefix + "timeout", "30000");
        p.setProperty(prefix + "writetimeout", "30000");
        p.setProperty(prefix + "ssl.checkserveridentity", "true");
        p.setProperty(prefix + "ssl.enable", String.valueOf(mode == TlsMode.SSL));
        p.setProperty(prefix + "starttls.enable", String.valueOf(mode == TlsMode.STARTTLS));
        p.setProperty(prefix + "starttls.required", String.valueOf(mode == TlsMode.STARTTLS));
        p.setProperty(prefix + "auth", "true");
        p.setProperty("mail.imap.peek", "true");
        p.setProperty("mail.smtp.sendpartial", "false");
        p.setProperty("mail.smtp.quitwait", "false");
        return p;
    }
    public Store openImap(Account account) throws MessagingException {
        Store store = Session.getInstance(properties("imap", account.imapTls)).getStore("imap");
        try {
            store.connect(account.imapHost, account.imapPort, account.imapUsername, cipher.decrypt(account.imapSecret));
            return store;
        } catch (RuntimeException | MessagingException e) { try { store.close(); } catch (Exception ignored) {} throw e; }
    }
    public Transport openSmtp(Account account) throws MessagingException {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.smtpHost); sender.setPort(account.smtpPort);
        sender.setUsername(account.smtpUsername); sender.setPassword(cipher.decrypt(account.smtpSecret));
        sender.setJavaMailProperties(properties("smtp", account.smtpTls));
        Transport transport = sender.getSession().getTransport("smtp");
        try {
            transport.connect(sender.getHost(), sender.getPort(), sender.getUsername(), sender.getPassword());
            return transport;
        } catch (RuntimeException | MessagingException e) { try { transport.close(); } catch (Exception ignored) {} throw e; }
    }
    public void test(Account account) throws MessagingException {
        try (Store store = openImap(account)) {
            Folder inbox = store.getFolder("INBOX");
            try { inbox.open(Folder.READ_ONLY); }
            finally { if (inbox.isOpen()) inbox.close(false); }
        }
        try (Transport ignored = openSmtp(account)) { /* Authenticate only; no message sent. */ }
    }
}
