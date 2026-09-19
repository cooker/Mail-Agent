package io.mailagent;
import java.util.*;
public class RuleForm {
    public String name = "";
    public String getName() { return name; }
    public boolean enabled = true;
    public boolean getEnabled() { return enabled; }
    public Set<Long> accountIds = new LinkedHashSet<>();
    public Set<Long> getAccountIds() { return accountIds; }
    public SubjectMode subjectMode = SubjectMode.CONTAINS;
    public SubjectMode getSubjectMode() { return subjectMode; }
    public String subjectPattern = "";
    public String getSubjectPattern() { return subjectPattern; }
    public boolean ignoreCase = true;
    public boolean getIgnoreCase() { return ignoreCase; }
    public String recipients = "";
    public String getRecipients() { return recipients; }
    public String targets = "";
    public String getTargets() { return targets; }
    public static RuleForm from(ForwardRule rule) {
        RuleForm f=new RuleForm(); f.name=rule.name; f.enabled=rule.enabled; f.accountIds=new LinkedHashSet<>(rule.accountIds);
        f.subjectMode=rule.subjectMode; f.subjectPattern=rule.subjectPattern; f.ignoreCase=rule.ignoreCase;
        f.recipients=String.join(", ",rule.recipients); f.targets=String.join(", ",rule.targets); return f;
    }
}
