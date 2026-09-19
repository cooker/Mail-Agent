package io.mailagent;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Controller
public class AdminController {
    private final AccountRepository accounts; private final RuleRepository rules; private final MailRepository mails;
    private final DeliveryRepository deliveries; private final AttemptRepository attempts; private final ConfigurationService config;
    private final DeliveryService sender; private final MailGateway gateway; private final RuleMatcher matcher; private final MailContentService contents;
    public AdminController(AccountRepository accounts,RuleRepository rules,MailRepository mails,DeliveryRepository deliveries,
            AttemptRepository attempts,ConfigurationService config,DeliveryService sender,MailGateway gateway,RuleMatcher matcher,
            MailContentService contents) {
        this.accounts=accounts; this.rules=rules; this.mails=mails; this.deliveries=deliveries; this.attempts=attempts;
        this.config=config; this.sender=sender; this.gateway=gateway; this.matcher=matcher; this.contents=contents;
    }
    @InitBinder public void bind(WebDataBinder binder) { binder.initDirectFieldAccess(); }
    @ModelAttribute("allAccounts") public List<Account> allAccounts() { return accounts.findAll(Sort.by("id")); }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/") public String dashboard(Model model) {
        model.addAttribute("accountCount",accounts.count()); model.addAttribute("ruleCount",rules.count());
        model.addAttribute("mailCount",mails.count()); model.addAttribute("sentCount",deliveries.countByStatus(DeliveryStatus.SENT));
        model.addAttribute("unknownCount",deliveries.countByStatus(DeliveryStatus.UNKNOWN));
        model.addAttribute("failedCount",deliveries.countByStatus(DeliveryStatus.FAILED));
        model.addAttribute("recent",mails.findAll(PageRequest.of(0,8,Sort.by("processedAt").descending())).getContent());
        return "dashboard";
    }
    @GetMapping("/accounts") public String accountList() { return "accounts"; }
    @GetMapping("/accounts/new") public String accountNew(Model model) {
        model.addAttribute("form",new AccountForm()); model.addAttribute("id",null); return "account-form";
    }
    @GetMapping("/accounts/{id}/edit") public String accountEdit(@PathVariable Long id,Model model) {
        model.addAttribute("form",AccountForm.from(config.account(id))); model.addAttribute("id",id); return "account-form";
    }
    @PostMapping("/accounts/save") public String accountSave(@RequestParam(required=false) Long id,@ModelAttribute AccountForm form,RedirectAttributes flash) {
        config.saveAccount(id,form); flash.addFlashAttribute("notice","邮箱配置已保存；新账号请先配置规则，再启用同步。"); return "redirect:/accounts";
    }
    @PostMapping("/accounts/{id}/toggle") public String toggle(@PathVariable Long id) { config.toggle(id); return "redirect:/accounts"; }
    @PostMapping("/accounts/{id}/reset") public String reset(@PathVariable Long id,@RequestParam int historyDays,RedirectAttributes flash) {
        config.reset(id,historyDays); flash.addFlashAttribute("notice","同步范围已重置，请手动启用账号。"); return "redirect:/accounts";
    }
    @PostMapping("/accounts/{id}/test") public String testConnection(@PathVariable Long id,RedirectAttributes flash) {
        try { gateway.test(config.account(id)); flash.addFlashAttribute("notice","IMAP 与 SMTP 连接认证成功，未发送测试邮件。"); }
        catch (Exception e) { flash.addFlashAttribute("error","连接测试失败（"+e.getClass().getSimpleName()+"），请检查服务器、TLS 和凭据。"); }
        return "redirect:/accounts";
    }
    @GetMapping("/rules") public String ruleList(Model model) { model.addAttribute("rules",rules.findAll(Sort.by("id"))); return "rules"; }
    @GetMapping("/rules/new") public String ruleNew(Model model) { model.addAttribute("form",new RuleForm()); model.addAttribute("id",null); return "rule-form"; }
    @GetMapping("/rules/{id}/edit") public String ruleEdit(@PathVariable Long id,Model model) {
        model.addAttribute("form",RuleForm.from(config.rule(id))); model.addAttribute("id",id); return "rule-form";
    }
    @PostMapping("/rules/save") public String ruleSave(@RequestParam(required=false) Long id,@ModelAttribute RuleForm form,RedirectAttributes flash) {
        config.saveRule(id,form); flash.addFlashAttribute("notice","规则已保存，仅影响尚未处理的邮件。"); return "redirect:/rules";
    }
    @PostMapping("/rules/{id}/toggle") public String ruleToggle(@PathVariable Long id) {
        ForwardRule rule=config.rule(id); rule.enabled=!rule.enabled; rules.save(rule); return "redirect:/rules";
    }
    @GetMapping("/rules/test") public String matchTest() { return "rule-test"; }
    @PostMapping("/rules/test") public String matchTest(@RequestParam Long accountId,@RequestParam(defaultValue="") String subject,
            @RequestParam(defaultValue="") String recipients,Model model) {
        Account a=config.account(accountId);
        RuleMatcher.Match result=matcher.match(rules.findByEnabledTrueOrderById(),accountId,subject,AddressUtils.parse(recipients),a.email);
        model.addAttribute("result",result); model.addAttribute("accountId",accountId); model.addAttribute("subject",subject);
        model.addAttribute("recipients",recipients); return "rule-test";
    }
    @GetMapping("/records") public String records(@RequestParam(required=false) Long accountId,
            @RequestParam(required=false) DeliveryStatus status,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,@RequestParam(required=false) String sender,
            @RequestParam(required=false) String recipient,@RequestParam(required=false) String subject,
            @RequestParam(defaultValue="0") int page,Model model) {
        if (from!=null && to!=null && from.isAfter(to)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        String senderFilter=mailFilter(sender,"发件人",500);
        String recipientFilter=mailFilter(recipient,"收件人",500);
        String subjectFilter=mailFilter(subject,"主题",1000);
        Specification<ReceivedMail> spec=(root,query,cb)-> {
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (accountId!=null) conditions.add(cb.equal(root.get("accountId"),accountId));
            if (!senderFilter.isEmpty()) conditions.add(cb.like(cb.lower(root.<String>get("sender")),likePattern(senderFilter),'\\'));
            if (!recipientFilter.isEmpty()) conditions.add(cb.like(cb.lower(root.<String>get("recipients")),likePattern(recipientFilter),'\\'));
            if (!subjectFilter.isEmpty()) conditions.add(cb.like(cb.lower(root.<String>get("subject")),likePattern(subjectFilter),'\\'));
            if (from!=null) conditions.add(cb.greaterThanOrEqualTo(root.get("processedAt"),from.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            if (to!=null) conditions.add(cb.lessThan(root.get("processedAt"),to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            if (status!=null) {
                var sub=query.subquery(Long.class); var d=sub.from(Delivery.class);
                sub.select(d.get("mailId")).where(cb.equal(d.get("mailId"),root.get("id")),cb.equal(d.get("status"),status));
                conditions.add(cb.exists(sub));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var results=mails.findAll(spec,PageRequest.of(Math.max(0,page),20,Sort.by("processedAt").descending().and(Sort.by("id").descending())));
        Map<Long,List<Delivery>> tasks=new HashMap<>(); results.forEach(m->tasks.put(m.id,deliveries.findByMailIdOrderById(m.id)));
        model.addAttribute("results",results); model.addAttribute("tasks",tasks); model.addAttribute("accountId",accountId);
        model.addAttribute("status",status); model.addAttribute("from",from); model.addAttribute("to",to);
        model.addAttribute("sender",senderFilter); model.addAttribute("recipient",recipientFilter);
        model.addAttribute("subject",subjectFilter); return "records";
    }
    private static String mailFilter(String value,String label,int maxLength) {
        String normalized=value==null ? "" : value.strip();
        if (normalized.length()>maxLength) throw new IllegalArgumentException(label+"筛选条件过长");
        return normalized;
    }
    private static String likePattern(String value) {
        return "%"+value.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    }
    @GetMapping("/records/{id}") public String record(@PathVariable Long id,Model model) {
        ReceivedMail mail=mails.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var tasks=deliveries.findByMailIdOrderById(id); Map<Long,List<DeliveryAttempt>> history=new HashMap<>();
        tasks.forEach(t->history.put(t.id,attempts.findByDeliveryIdOrderByIdDesc(t.id)));
        model.addAttribute("mail",mail); model.addAttribute("tasks",tasks); model.addAttribute("history",history);
        model.addAttribute("content",contents.read(mail.payloadName)); return "record";
    }
    @PostMapping("/deliveries/{id}/retry") public String retry(@PathVariable Long id,@RequestParam(defaultValue="false") boolean confirmed,RedirectAttributes flash) {
        if (!confirmed) throw new IllegalArgumentException("请先核查目标邮箱，并勾选确认重试；重复发送可能产生重复邮件");
        Delivery task=deliveries.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        sender.retry(id); flash.addFlashAttribute("notice","任务已重新入队；对应邮箱启用后自动发送。"); return "redirect:/records/"+task.mailId;
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException e,Model model) { model.addAttribute("message",e.getMessage()); return "error"; }
}
