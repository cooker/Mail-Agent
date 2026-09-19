package io.mailagent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="forward_rules")
public class ForwardRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable=false)
    public String name;
    
    public boolean enabled;
    @Enumerated(EnumType.STRING)
    public SubjectMode subjectMode;
    @Column(length=1000)
    public String subjectPattern;
    
    public boolean ignoreCase;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="rule_accounts",joinColumns=@JoinColumn(name="rule_id")) @Column(name="account_id")
    public Set<Long> accountIds = new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="rule_recipients",joinColumns=@JoinColumn(name="rule_id")) @Column(name="address")
    public Set<String> recipients = new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="rule_targets",joinColumns=@JoinColumn(name="rule_id")) @Column(name="address")
    public Set<String> targets = new LinkedHashSet<>();
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean getEnabled() { return enabled; }
    public SubjectMode getSubjectMode() { return subjectMode; }
    public String getSubjectPattern() { return subjectPattern; }
    public boolean getIgnoreCase() { return ignoreCase; }
    public Set<Long> getAccountIds() { return accountIds; }
    public Set<String> getRecipients() { return recipients; }
    public Set<String> getTargets() { return targets; }
    public boolean getMatchAll() { return accountIds.isEmpty() && recipients.isEmpty() && (subjectPattern == null || subjectPattern.isBlank()); }
}
