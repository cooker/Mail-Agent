package io.mailagent;

import com.google.re2j.Pattern;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class RuleMatcher {
    public record Match(Set<String> targets, List<Long> ruleIds) {}
    public boolean matches(ForwardRule rule, Long accountId, String subject, Set<String> recipients) {
        if (!rule.enabled) return false;
        if (!rule.accountIds.isEmpty() && !rule.accountIds.contains(accountId)) return false;
        if (!rule.recipients.isEmpty() && recipients.stream().map(AddressUtils::normalize).noneMatch(rule.recipients::contains)) return false;
        String pattern = Objects.requireNonNullElse(rule.subjectPattern, "");
        if (pattern.isBlank()) return true;
        String value = Objects.requireNonNullElse(subject, "");
        if (rule.subjectMode == SubjectMode.REGEX)
            return Pattern.compile(pattern, rule.ignoreCase ? Pattern.CASE_INSENSITIVE : 0).matcher(value).find();
        if (rule.ignoreCase) { pattern = pattern.toLowerCase(Locale.ROOT); value = value.toLowerCase(Locale.ROOT); }
        return rule.subjectMode == SubjectMode.EXACT ? value.equals(pattern) : value.contains(pattern);
    }
    public Match match(List<ForwardRule> rules, Long accountId, String subject, Set<String> recipients, String source) {
        Set<String> targets = new LinkedHashSet<>(); List<Long> ids = new ArrayList<>();
        for (ForwardRule rule : rules) {
            if (matches(rule, accountId, subject, recipients)) { targets.addAll(rule.targets); ids.add(rule.id); }
        }
        targets.remove(AddressUtils.normalize(source));
        return new Match(targets, ids);
    }
    public void validate(ForwardRule rule) {
        if (rule.subjectPattern != null && rule.subjectPattern.length() > 1000)
            throw new IllegalArgumentException("主题条件最多 1000 个字符");
        if (rule.subjectMode == SubjectMode.REGEX && rule.subjectPattern != null && !rule.subjectPattern.isBlank()) {
            try { Pattern.compile(rule.subjectPattern, rule.ignoreCase ? Pattern.CASE_INSENSITIVE : 0); }
            catch (RuntimeException e) { throw new IllegalArgumentException("正则表达式无效：使用 RE2 语法，不支持回溯引用和前后查找"); }
        }
        if (rule.targets.isEmpty()) throw new IllegalArgumentException("至少配置一个转发目标");
    }
}
