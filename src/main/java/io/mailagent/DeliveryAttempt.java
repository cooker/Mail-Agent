package io.mailagent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="delivery_attempts")
public class DeliveryAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable=false)
    public Long deliveryId;
    
    public Instant attemptedAt;
    
    public String outcome;
    @Column(length=512)
    public String detail;
    public Long getId() { return id; }
    public Long getDeliveryId() { return deliveryId; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
}
