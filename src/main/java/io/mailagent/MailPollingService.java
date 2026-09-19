package io.mailagent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MailPollingService {
    private final AccountRepository accounts; private final RuleRepository rules; private final MailRepository mails;
    private final DeliveryRepository deliveries; private final MailGateway gateway; private final RuleMatcher matcher;
    private final PayloadStore payloads; private final ForwardComposer composer; private final TransactionTemplate tx;
    private final AccountLocks locks;
    public MailPollingService(AccountRepository accounts, RuleRepository rules, MailRepository mails,
            DeliveryRepository deliveries, MailGateway gateway, RuleMatcher matcher, PayloadStore payloads,
            ForwardComposer composer, TransactionTemplate tx, AccountLocks locks) {
        this.accounts=accounts; this.rules=rules; this.mails=mails; this.deliveries=deliveries; this.gateway=gateway;
        this.matcher=matcher; this.payloads=payloads; this.composer=composer; this.tx=tx; this.locks=locks;
    }
    public void poll(Long id) {
        ReentrantLock lock = locks.forAccount(id);
        if (!lock.tryLock()) return;
        try {
            Account account = accounts.findById(id).orElseThrow();
            if (!account.enabled || account.resetRequired) return;
            try (Store store = gateway.openImap(account)) {
                Folder inbox = store.getFolder("INBOX");
                try {
                    inbox.open(Folder.READ_ONLY);
                    if (!(inbox instanceof UIDFolder uidFolder)) throw new MessagingException("IMAP UID unavailable");
                    long validity = uidFolder.getUIDValidity();
                    if (account.uidValidity != null && account.uidValidity != validity) {
                        account.enabled = false; account.resetRequired = true;
                        account.lastError = "邮箱 UIDVALIDITY 已变化，请重新选择同步范围";
                        account.lastChecked = Instant.now(); accounts.save(account); return;
                    }
                    if (account.uidValidity == null) {
                        // Capture high-water mark before processing. New arrivals remain above this mark.
                        long high = uidFolder.getUIDNext() - 1;
                        if (high < 0) high = inbox.getMessageCount() == 0 ? 0 : uidFolder.getUID(inbox.getMessage(inbox.getMessageCount()));
                        account.uidValidity = validity;
                        account.lastUid = account.historyDays == 0 ? high : 0;
                        account.receivedAfter = account.historyDays == 0 ? null : Instant.now().minus(Duration.ofDays(account.historyDays));
                        accounts.save(account);
                    }
                    List<ForwardRule> snapshot = rules.findByEnabledTrueOrderById();
                    Message[] messages = uidFolder.getMessagesByUID(account.lastUid + 1, UIDFolder.LASTUID);
                    int processed = 0;
                    for (Message message : messages) {
                        if (message == null || message.isExpunged()) continue;
                        long uid = uidFolder.getUID(message);
                        if (uid <= account.lastUid) continue;
                        process(account, uid, (MimeMessage) message, snapshot);
                        if (++processed >= 100) break;
                    }
                    account.lastChecked = Instant.now(); account.lastError = null; accounts.save(account);
                } finally { if (inbox.isOpen()) inbox.close(false); }
            } catch (Exception failure) {
                account.lastChecked = Instant.now();
                if (failure instanceof AuthenticationFailedException) { account.enabled=false; account.lastError="IMAP 认证失败，账号已暂停，请检查凭据"; }
                else account.lastError="收取失败（" + failure.getClass().getSimpleName() + "），下次轮询重试；请检查连接和本地存储";
                accounts.save(account);
            }
        } finally { lock.unlock(); }
    }
    private void process(Account account, long uid, MimeMessage message, List<ForwardRule> snapshot) throws Exception {
        if (mails.existsByAccountIdAndFolderNameAndUidValidityAndImapUid(account.id,"INBOX",account.uidValidity,uid)) {
            account.lastUid=uid; accounts.save(account); return;
        }
        Instant received = message.getReceivedDate() == null ? Instant.now() : message.getReceivedDate().toInstant();
        boolean historicalSkip = account.receivedAfter != null && received.isBefore(account.receivedAfter);
        boolean loop = composer.forwarded(message);
        Set<String> recipients = AddressUtils.recipients(message);
        String subject = message.getSubject();
        RuleMatcher.Match match = historicalSkip || loop ? new RuleMatcher.Match(Set.of(), List.of())
                : matcher.match(snapshot, account.id, subject, recipients, account.email);
        ReceivedMail mail = new ReceivedMail();
        mail.accountId=account.id; mail.folderName="INBOX"; mail.uidValidity=account.uidValidity; mail.imapUid=uid;
        mail.subject=AddressUtils.clip(subject,2048); mail.sender=AddressUtils.clip(AddressUtils.display(message.getFrom()),2048);
        mail.recipients=AddressUtils.clip(String.join(", ",recipients),4000); mail.receivedAt=received; mail.processedAt=Instant.now();
        mail.matchedRules=AddressUtils.clip(match.ruleIds().toString(),2048);
        mail.outcome=historicalSkip ? "超出回溯范围" : loop ? "跳过系统转发邮件" : match.targets().isEmpty() ? "无转发目标" : "已创建转发任务";
        // Keep every in-range message so its body and attachments remain available to the mailbox and share views.
        if (!historicalSkip) mail.payloadName=payloads.save(message);
        try {
            tx.executeWithoutResult(status -> {
                mails.save(mail);
                for (String target : match.targets()) {
                    Delivery task=new Delivery(); task.mailId=mail.id; task.accountId=account.id; task.target=target;
                    task.status=DeliveryStatus.PENDING; task.nextAttempt=Instant.now(); task.updatedAt=Instant.now(); deliveries.save(task);
                }
                account.lastUid=uid; accounts.save(account);
            });
        } catch (RuntimeException e) {
            // Reload before propagating, so the error handler cannot accidentally advance a rolled-back cursor.
            account.lastUid=accounts.findById(account.id).orElseThrow().lastUid;
            if (mail.payloadName != null) payloads.delete(mail.payloadName);
            throw e;
        }
    }
}
