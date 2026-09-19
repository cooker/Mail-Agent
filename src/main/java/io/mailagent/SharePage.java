package io.mailagent;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="share_pages")
public class SharePage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    public Long id;
    @Column(nullable=false, length=200)
    public String title;
    @Column(nullable=false, unique=true, length=64)
    public String tokenHash;
    @Column(length=512)
    public String tokenSecret;
    public Long accountId;
    @Column(nullable=false, length=1000)
    public String subjectKeyword;
    @Column(nullable=false, length=1000)
    public String recipientKeyword="";
    public boolean enabled;
    public Instant expiresAt;
    @Column(nullable=false)
    public Instant createdAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Long getAccountId() { return accountId; }
    public String getSubjectKeyword() { return subjectKeyword; }
    public String getRecipientKeyword() { return recipientKeyword; }
    public boolean getEnabled() { return enabled; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
