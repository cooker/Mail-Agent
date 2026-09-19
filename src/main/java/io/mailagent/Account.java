package io.mailagent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="mail_accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable=false)
    public String name;
    @Column(nullable=false)
    public String email;
    @Column(nullable=false)
    public String imapHost;
    
    public int imapPort;
    @Enumerated(EnumType.STRING)
    public TlsMode imapTls;
    
    public String imapUsername;
    @Column(length=4096)
    public String imapSecret;
    
    public String smtpHost;
    
    public int smtpPort;
    @Enumerated(EnumType.STRING)
    public TlsMode smtpTls;
    
    public String smtpUsername;
    @Column(length=4096)
    public String smtpSecret;
    
    public boolean enabled;
    
    public int historyDays;
    
    public Long uidValidity;
    
    public long lastUid;
    
    public Instant receivedAfter;
    
    public Instant lastChecked;
    @Column(length=512)
    public String lastError;
    
    public boolean resetRequired;
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getImapHost() { return imapHost; }
    public int getImapPort() { return imapPort; }
    public TlsMode getImapTls() { return imapTls; }
    public String getImapUsername() { return imapUsername; }
    public String getImapSecret() { return imapSecret; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public TlsMode getSmtpTls() { return smtpTls; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpSecret() { return smtpSecret; }
    public boolean getEnabled() { return enabled; }
    public int getHistoryDays() { return historyDays; }
    public Long getUidValidity() { return uidValidity; }
    public long getLastUid() { return lastUid; }
    public Instant getReceivedAfter() { return receivedAfter; }
    public Instant getLastChecked() { return lastChecked; }
    public String getLastError() { return lastError; }
    public boolean getResetRequired() { return resetRequired; }
}
