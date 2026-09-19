package io.mailagent;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class ShareService {
    private final SharePageRepository shares;
    private final AccountRepository accounts;
    private final MailRepository mails;
    private final SecretCipher cipher;
    private final SecureRandom random=new SecureRandom();

    public ShareService(SharePageRepository shares,AccountRepository accounts,MailRepository mails,SecretCipher cipher) {
        this.shares=shares; this.accounts=accounts; this.mails=mails; this.cipher=cipher;
    }

    public record Created(SharePage page,String token) {}

    public Created create(ShareForm form) {
        String token=newToken();
        SharePage page=new SharePage(); apply(page,form);
        page.enabled=true; page.createdAt=Instant.now();
        page.tokenHash=hash(token); page.tokenSecret=cipher.encrypt(token); shares.save(page);
        return new Created(page,token);
    }

    public void update(Long id,ShareForm form) {
        SharePage page=find(id); apply(page,form); shares.save(page);
    }

    public SharePage find(Long id) {
        return shares.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public SharePage requireActive(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        SharePage page=shares.findByTokenHashAndEnabledTrue(hash(token)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (page.expiresAt!=null && !page.expiresAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.GONE,"分享已过期");
        return page;
    }

    public Page<ReceivedMail> mails(SharePage share,int page) {
        return mails.findAll(specification(share),PageRequest.of(Math.max(0,page),20,
                Sort.by("receivedAt").descending().and(Sort.by("id").descending())));
    }

    public ReceivedMail requireMail(SharePage share,Long mailId) {
        return mails.findOne(specification(share).and((root,query,cb)->cb.equal(root.get("id"),mailId)))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Specification<ReceivedMail> specification(SharePage share) {
        return (root,query,cb)-> {
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (share.accountId!=null) conditions.add(cb.equal(root.get("accountId"),share.accountId));
            conditions.add(cb.isNotNull(root.get("payloadName")));
            var subjectExpression=cb.lower(cb.coalesce(root.<String>get("subject"),""));
            var subjectConditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            terms(share.subjectKeyword).forEach(value->subjectConditions.add(cb.like(subjectExpression,likePattern(value),'\\')));
            conditions.add(cb.or(subjectConditions.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            if (share.recipientKeyword!=null && !share.recipientKeyword.isBlank()) {
                var recipientExpression=cb.lower(cb.coalesce(root.<String>get("recipients"),""));
                var recipientConditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
                terms(share.recipientKeyword).forEach(value->recipientConditions.add(cb.like(recipientExpression,likePattern(value),'\\')));
                conditions.add(cb.or(recipientConditions.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public void toggle(Long id) {
        SharePage page=find(id);
        page.enabled=!page.enabled; shares.save(page);
    }

    public void delete(Long id) {
        SharePage page=find(id);
        shares.delete(page);
    }

    public void regenerate(Long id) {
        SharePage page=find(id);
        String token=newToken(); page.tokenHash=hash(token); page.tokenSecret=cipher.encrypt(token); shares.save(page);
    }

    public Optional<String> token(SharePage page) {
        if (page.tokenSecret==null || page.tokenSecret.isBlank()) return Optional.empty();
        String token=cipher.decrypt(page.tokenSecret);
        if (!hash(token).equals(page.tokenHash)) throw new IllegalStateException("分享令牌数据不一致");
        return Optional.of(token);
    }

    private String newToken() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void apply(SharePage page,ShareForm form) {
        String title=ConfigurationService.required(form.title,"分享名称",200);
        String keyword=normalizeTerms(form.subjectKeyword,"主题关键词",false,false);
        String recipient=normalizeTerms(form.recipientKeyword,"收件人筛选条件",true,true);
        if (form.accountId!=null && !accounts.existsById(form.accountId)) throw new IllegalArgumentException("选择的邮箱账号不存在");
        if (form.expiresOn!=null && form.expiresOn.isBefore(LocalDate.now())) throw new IllegalArgumentException("过期日期不能早于今天");
        page.title=title; page.subjectKeyword=keyword; page.recipientKeyword=recipient; page.accountId=form.accountId;
        page.expiresAt=form.expiresOn==null ? null : form.expiresOn.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private String likePattern(String value) {
        String escaped=value.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        return "%"+escaped+"%";
    }

    private String normalizeTerms(String value,String label,boolean optional,boolean recipientSeparators) {
        String raw=Objects.requireNonNullElse(value,"");
        if (raw.length()>1000) throw new IllegalArgumentException(label+"过长");
        String separator=recipientSeparators ? "[,;\\r\\n]+" : "\\R+";
        Map<String,String> unique=new LinkedHashMap<>();
        for (String item:raw.split(separator)) {
            String term=item.strip();
            if (!term.isEmpty()) unique.putIfAbsent(term.toLowerCase(Locale.ROOT),term);
        }
        if (unique.isEmpty() && !optional) throw new IllegalArgumentException(label+"不能为空");
        return String.join("\n",unique.values());
    }

    private List<String> terms(String value) {
        return Arrays.stream(Objects.requireNonNullElse(value,"").split("\\R+"))
                .map(String::strip).filter(term->!term.isEmpty()).toList();
    }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
