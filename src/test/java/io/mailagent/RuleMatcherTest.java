package io.mailagent;

import org.junit.jupiter.api.Test;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RuleMatcherTest {
    private final RuleMatcher matcher=new RuleMatcher();
    private ForwardRule rule() {
        ForwardRule rule=new ForwardRule(); rule.id=1L; rule.enabled=true; rule.subjectMode=SubjectMode.CONTAINS;
        rule.ignoreCase=true; rule.subjectPattern="账单"; rule.targets.add("archive@example.com"); return rule;
    }
    @Test void combinesConditionsWithAndAndRecipientsWithOr() {
        var r=rule(); r.accountIds.add(1L); r.recipients.addAll(Set.of("finance@example.com","team@example.com"));
        assertThat(matcher.matches(r,1L,"九月账单",Set.of("TEAM@EXAMPLE.COM"))).isTrue();
        assertThat(matcher.matches(r,2L,"九月账单",Set.of("team@example.com"))).isFalse();
        assertThat(matcher.matches(r,1L,"你好",Set.of("team@example.com"))).isFalse();
        assertThat(matcher.matches(r,1L,"九月账单",Set.of("someone@example.com"))).isFalse();
    }
    @Test void subjectModesAndNullAndCase() {
        var r=rule(); r.subjectPattern="Invoice";
        assertThat(matcher.matches(r,1L,"INVOICE 123",Set.of())).isTrue();
        r.ignoreCase=false; assertThat(matcher.matches(r,1L,"INVOICE 123",Set.of())).isFalse();
        r.ignoreCase=true; r.subjectMode=SubjectMode.EXACT;
        assertThat(matcher.matches(r,1L,"invoice",Set.of())).isTrue();
        assertThat(matcher.matches(r,1L,"invoice 123",Set.of())).isFalse();
        assertThat(matcher.matches(r,1L,null,Set.of())).isFalse();
        r.subjectMode=SubjectMode.REGEX; r.subjectPattern="^账单-[0-9]+$";
        assertThat(matcher.matches(r,1L,"账单-123",Set.of())).isTrue();
        assertThat(matcher.matches(r,1L,"新账单-123",Set.of())).isFalse();
        r.subjectPattern=""; assertThat(matcher.matches(r,1L,null,Set.of())).isTrue();
        r.enabled=false; assertThat(matcher.matches(r,1L,null,Set.of())).isFalse();
    }
    @Test void invalidAndUnsupportedRegexRejected() {
        var r=rule(); r.subjectMode=SubjectMode.REGEX; r.subjectPattern="[";
        assertThatThrownBy(()->matcher.validate(r)).isInstanceOf(IllegalArgumentException.class);
        r.subjectPattern="(?<=a)b"; assertThatThrownBy(()->matcher.validate(r)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unionDeduplicatesAndRemovesSource() {
        var a=rule(); var b=rule(); b.id=2L; b.targets.add("source@example.com"); b.targets.add("other@example.com");
        var result=matcher.match(List.of(a,b),1L,"账单",Set.of(),"SOURCE@example.com");
        assertThat(result.targets()).containsExactlyInAnyOrder("archive@example.com","other@example.com");
        assertThat(result.ruleIds()).containsExactly(1L,2L);
    }
    @Test void decodesEncodedChineseSubjectAndExtractsToAndCcGroups() throws Exception {
        String raw="From: sender@example.com\r\nTo: team@example.com\r\nCc: Group: finance@example.com, other@example.com;\r\nSubject: =?UTF-8?B?5Lmd5pyI6LSm5Y2V?=\r\n\r\nbody";
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(message.getSubject()).isEqualTo("九月账单");
        assertThat(AddressUtils.recipients(message)).containsExactlyInAnyOrder("team@example.com","finance@example.com","other@example.com");
        assertThat(matcher.matches(rule(),1L,message.getSubject(),AddressUtils.recipients(message))).isTrue();
    }
    @Test void addressValidationAndCanonicalization() {
        assertThat(AddressUtils.parse("A@Example.com; a@example.com\nb@example.com")).containsExactly("a@example.com","b@example.com");
        assertThatThrownBy(()->AddressUtils.parse("bad")).isInstanceOf(IllegalArgumentException.class);
    }
}
