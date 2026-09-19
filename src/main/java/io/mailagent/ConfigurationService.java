package io.mailagent;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service
public class ConfigurationService {
    private final AccountRepository accounts; private final RuleRepository rules;
    private final SecretCipher cipher; private final RuleMatcher matcher; private final AccountLocks locks;
    public ConfigurationService(AccountRepository accounts, RuleRepository rules, SecretCipher cipher,
            RuleMatcher matcher, AccountLocks locks) {
        this.accounts=accounts; this.rules=rules; this.cipher=cipher; this.matcher=matcher; this.locks=locks;
    }
    public Account account(Long id) { return accounts.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    public ForwardRule rule(Long id) { return rules.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    public Account saveAccount(Long id, AccountForm f) {
        var lock=locks.forAccount(id==null ? 0L : id); lock.lock();
        try {
            Account a=id==null ? new Account() : account(id);
            boolean credentialsMissing=a.imapSecret==null || a.smtpSecret==null;
            String email=singleAddress(f.email);
            String imapHost=host(f.imapHost); String smtpHost=host(f.smtpHost);
            String imapUser=required(f.imapUsername,"IMAP 用户名",255);
            if (a.uidValidity!=null && (!Objects.equals(a.email,email) || !Objects.equals(a.imapHost,imapHost)
                    || !Objects.equals(a.imapUsername,imapUser)))
                throw new IllegalArgumentException("已同步账号不能更换邮箱身份，请新增邮箱账号；服务器端口、密码和 SMTP 配置仍可修改");
            a.name=required(f.name,"名称",100); a.email=email;
            a.imapHost=imapHost; a.imapPort=port(f.imapPort); a.imapTls=Objects.requireNonNullElse(f.imapTls,TlsMode.SSL);
            a.imapUsername=imapUser;
            a.smtpHost=smtpHost; a.smtpPort=port(f.smtpPort); a.smtpTls=Objects.requireNonNullElse(f.smtpTls,TlsMode.SSL);
            a.smtpUsername=required(f.smtpUsername,"SMTP 用户名",255);
            if (f.imapPassword!=null && !f.imapPassword.isBlank()) a.imapSecret=cipher.encrypt(required(f.imapPassword,"IMAP 密码",512));
            if (f.smtpPassword!=null && !f.smtpPassword.isBlank()) a.smtpSecret=cipher.encrypt(required(f.smtpPassword,"SMTP 密码",512));
            if (a.imapSecret==null || a.smtpSecret==null) throw new IllegalArgumentException("请填写 IMAP 和 SMTP 密码或授权码");
            a.historyDays=historyDays(f.historyDays);
            if (a.resetRequired && f.enabled) throw new IllegalArgumentException("请先重新选择同步范围再启用账号");
            if (credentialsMissing && !a.resetRequired) a.lastError=null;
            a.enabled=f.enabled; return accounts.save(a);
        } finally { lock.unlock(); }
    }
    public void toggle(Long id) {
        var lock=locks.forAccount(id); lock.lock();
        try { Account a=account(id); if (a.resetRequired) throw new IllegalArgumentException("请先重新选择同步范围");
            if (!a.enabled && (a.imapSecret==null || a.smtpSecret==null))
                throw new IllegalArgumentException("请先编辑邮箱，重新填写 IMAP 和 SMTP 密码或授权码");
            a.enabled=!a.enabled; accounts.save(a);
        } finally { lock.unlock(); }
    }
    public void reset(Long id,int days) {
        var lock=locks.forAccount(id); lock.lock();
        try {
            Account a=account(id);
            if (!a.resetRequired) throw new IllegalArgumentException("仅在 UIDVALIDITY 变化后需要重置同步范围");
            a.historyDays=historyDays(days); a.uidValidity=null; a.lastUid=0; a.receivedAfter=null;
            a.resetRequired=false; a.enabled=false; a.lastError=null; accounts.save(a);
        } finally { lock.unlock(); }
    }
    public ForwardRule saveRule(Long id,RuleForm f) {
        ForwardRule rule=id==null ? new ForwardRule() : rule(id);
        rule.name=required(f.name,"规则名称",100); rule.enabled=f.enabled;
        rule.accountIds=f.accountIds==null ? new LinkedHashSet<>() : new LinkedHashSet<>(f.accountIds);
        if (accounts.findAllById(rule.accountIds).size()!=rule.accountIds.size()) throw new IllegalArgumentException("选择的邮箱账号不存在");
        rule.subjectMode=Objects.requireNonNullElse(f.subjectMode,SubjectMode.CONTAINS);
        rule.subjectPattern=Objects.requireNonNullElse(f.subjectPattern,"").strip(); rule.ignoreCase=f.ignoreCase;
        rule.recipients=AddressUtils.parse(f.recipients); rule.targets=AddressUtils.parse(f.targets); matcher.validate(rule);
        return rules.save(rule);
    }
    static String required(String s,String name,int max) {
        if (s==null || s.isBlank() || s.length()>max || s.contains("\r") || s.contains("\n"))
            throw new IllegalArgumentException(name+"不能为空，且不能超过 "+max+" 个字符或包含换行");
        return s;
    }
    private String singleAddress(String text) {
        Set<String> values=AddressUtils.parse(text);
        if (values.size()!=1) throw new IllegalArgumentException("请输入一个完整的来源邮箱地址");
        return values.iterator().next();
    }
    private String host(String text) {
        String host=required(text,"服务器地址",253).strip();
        if (!host.matches("[a-zA-Z0-9._:-]+")) throw new IllegalArgumentException("服务器地址只填写主机名或 IP，不包含协议和路径");
        return host;
    }
    private int port(int p) { if (p<1 || p>65535) throw new IllegalArgumentException("端口必须在 1–65535 之间"); return p; }
    private int historyDays(int days) { if (days<0 || days>3650) throw new IllegalArgumentException("回溯天数须在 0–3650 之间，0 表示仅新邮件"); return days; }
}
