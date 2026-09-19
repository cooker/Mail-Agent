package io.mailagent;
import java.util.*;
public class AccountForm {
    public String name = "";
    public String getName() { return name; }
    public String email = "";
    public String getEmail() { return email; }
    public String imapHost = "";
    public String getImapHost() { return imapHost; }
    public int imapPort = 993;
    public int getImapPort() { return imapPort; }
    public TlsMode imapTls = TlsMode.SSL;
    public TlsMode getImapTls() { return imapTls; }
    public String imapUsername = "";
    public String getImapUsername() { return imapUsername; }
    public String imapPassword = "";
    public String getImapPassword() { return imapPassword; }
    public String smtpHost = "";
    public String getSmtpHost() { return smtpHost; }
    public int smtpPort = 465;
    public int getSmtpPort() { return smtpPort; }
    public TlsMode smtpTls = TlsMode.SSL;
    public TlsMode getSmtpTls() { return smtpTls; }
    public String smtpUsername = "";
    public String getSmtpUsername() { return smtpUsername; }
    public String smtpPassword = "";
    public String getSmtpPassword() { return smtpPassword; }
    public int historyDays = 0;
    public int getHistoryDays() { return historyDays; }
    public boolean enabled = false;
    public boolean getEnabled() { return enabled; }
    public static AccountForm from(Account account) {
        AccountForm f = new AccountForm();
        f.name=account.name;
        f.email=account.email;
        f.imapHost=account.imapHost;
        f.imapPort=account.imapPort;
        f.imapTls=account.imapTls;
        f.imapUsername=account.imapUsername;
        f.smtpHost=account.smtpHost;
        f.smtpPort=account.smtpPort;
        f.smtpTls=account.smtpTls;
        f.smtpUsername=account.smtpUsername;
        f.historyDays=account.historyDays;
        f.enabled=account.enabled;
        return f;
    }
}
