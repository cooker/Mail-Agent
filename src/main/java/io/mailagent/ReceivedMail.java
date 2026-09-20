package io.mailagent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="received_mail", uniqueConstraints=@UniqueConstraint(columnNames={"account_id","folder_name","uid_validity","imap_uid"}))
public class ReceivedMail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable=false)
    public boolean deleted;
    @Column(nullable=false)
    public Long accountId;
    @Column(nullable=false)
    public String folderName;
    
    public long uidValidity;
    
    public long imapUid;
    @Column(length=2048)
    public String subject;
    @Column(length=2048)
    public String sender;
    @Column(length=4000)
    public String recipients;
    
    public Instant receivedAt;
    
    public Instant processedAt;
    
    public String outcome;
    
    public String payloadName;
    @Column(length=2048)
    public String matchedRules;
    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public String getFolderName() { return folderName; }
    public long getUidValidity() { return uidValidity; }
    public long getImapUid() { return imapUid; }
    public String getSubject() { return subject; }
    public String getSender() { return sender; }
    public String getRecipients() { return recipients; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getOutcome() { return outcome; }
    public String getPayloadName() { return payloadName; }
    public String getMatchedRules() { return matchedRules; }
}
