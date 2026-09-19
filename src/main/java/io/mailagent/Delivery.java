package io.mailagent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="deliveries", uniqueConstraints=@UniqueConstraint(columnNames={"mail_id","target"}))
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable=false)
    public Long mailId;
    @Column(nullable=false)
    public Long accountId;
    @Column(nullable=false)
    public String target;
    @Enumerated(EnumType.STRING)
    public DeliveryStatus status;
    
    public int attempts;
    
    public int retryCount;
    
    public Instant nextAttempt;
    
    public Instant updatedAt;
    @Column(length=512)
    public String lastError;
    public Long getId() { return id; }
    public Long getMailId() { return mailId; }
    public Long getAccountId() { return accountId; }
    public String getTarget() { return target; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextAttempt() { return nextAttempt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getLastError() { return lastError; }
}
