package io.mailagent;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DeliveryService {
    private final DeliveryRepository deliveries; private final AccountRepository accounts; private final MailRepository mails;
    private final AttemptRepository attempts; private final MailGateway gateway; private final PayloadStore payloads;
    private final ForwardComposer composer; private final TransactionTemplate tx; private final AccountLocks locks;
    public DeliveryService(DeliveryRepository deliveries, AccountRepository accounts, MailRepository mails,
            AttemptRepository attempts, MailGateway gateway, PayloadStore payloads, ForwardComposer composer,
            TransactionTemplate tx, AccountLocks locks) {
        this.deliveries=deliveries; this.accounts=accounts; this.mails=mails; this.attempts=attempts;
        this.gateway=gateway; this.payloads=payloads; this.composer=composer; this.tx=tx; this.locks=locks;
    }
    public void send(Long id) {
        Delivery initial=deliveries.findById(id).orElseThrow();
        ReentrantLock lock=locks.forAccount(initial.accountId);
        if (!lock.tryLock()) return;
        try {
            Delivery delivery=deliveries.findById(id).orElseThrow();
            Account account=accounts.findById(delivery.accountId).orElseThrow();
            if (!account.enabled || (delivery.status!=DeliveryStatus.PENDING && delivery.status!=DeliveryStatus.RETRY)
                    || delivery.nextAttempt.isAfter(Instant.now())) return;
            ReceivedMail mail=mails.findById(delivery.mailId).orElseThrow();
            MimeMessage message;
            try { message=composer.compose(payloads.read(mail.payloadName),account,delivery,mail.payloadName); }
            catch (Exception e) { finish(delivery,DeliveryStatus.FAILED,"无法读取或构建邮件内容，请检查任务文件",null); return; }
            delivery.status=DeliveryStatus.SENDING; delivery.attempts++; delivery.updatedAt=Instant.now();
            deliveries.saveAndFlush(delivery); // Commit before network I/O. A crash from here is indeterminate.
            Transport transport=null;
            boolean submitted=false;
            try {
                transport=gateway.openSmtp(account);
                submitted=true;
                transport.sendMessage(message,message.getAllRecipients());
                finish(delivery,DeliveryStatus.SENT,null,null);
            } catch (Exception e) {
                if (e instanceof AuthenticationFailedException) {
                    account.enabled=false; account.lastError="SMTP 认证失败，账号已暂停，请检查凭据"; accounts.save(account);
                    finish(delivery,DeliveryStatus.FAILED,"SMTP 认证失败，修正凭据后可手动重试",null);
                } else {
                    int code=smtpCode(e);
                    if (code>=500 && code<600) finish(delivery,DeliveryStatus.FAILED,"SMTP 永久拒绝（"+code+"）",null);
                    else if ((code>=400 && code<500) || !submitted) retryLater(delivery,code);
                    else finish(delivery,DeliveryStatus.UNKNOWN,"发送过程中断，无法确认服务器是否接收；请核查目标邮箱后重试",null);
                }
            } finally { if (transport!=null) try { transport.close(); } catch (Exception ignored) {} }
        } finally { lock.unlock(); }
    }
    private void retryLater(Delivery delivery, int code) {
        if (delivery.retryCount>=3) { finish(delivery,DeliveryStatus.FAILED,"自动重试已耗尽，可手动重试",null); return; }
        int minutes=new int[]{1,5,15}[delivery.retryCount++];
        finish(delivery,DeliveryStatus.RETRY,code>0 ? "SMTP 临时拒绝（"+code+"）" : "SMTP 连接失败，尚未提交邮件",
                Instant.now().plus(Duration.ofMinutes(minutes)));
    }
    static int smtpCode(Throwable error) {
        Set<Throwable> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        while (error!=null && visited.add(error)) {
            if (error instanceof SMTPSendFailedException smtp) return smtp.getReturnCode();
            if (error instanceof SMTPAddressFailedException smtp) return smtp.getReturnCode();
            error=error instanceof MessagingException me && me.getNextException()!=null ? me.getNextException() : error.getCause();
        }
        return 0;
    }
    private void finish(Delivery delivery, DeliveryStatus state, String detail, Instant next) {
        tx.executeWithoutResult(status -> {
            delivery.status=state; delivery.lastError=detail; delivery.nextAttempt=next; delivery.updatedAt=Instant.now(); deliveries.save(delivery);
            DeliveryAttempt attempt=new DeliveryAttempt(); attempt.deliveryId=delivery.id; attempt.attemptedAt=Instant.now();
            attempt.outcome=state.name(); attempt.detail=detail; attempts.save(attempt);
        });
    }
    public void recoverInterrupted() {
        for (Delivery delivery:deliveries.findByStatus(DeliveryStatus.SENDING))
            finish(delivery,DeliveryStatus.UNKNOWN,"上次进程中断，发送结果未知；请核查目标邮箱后重试",null);
    }
    public void retry(Long id) {
        Delivery initial=deliveries.findById(id).orElseThrow();
        ReentrantLock lock=locks.forAccount(initial.accountId); lock.lock();
        try {
            Delivery task=deliveries.findById(id).orElseThrow();
            if (task.status!=DeliveryStatus.FAILED && task.status!=DeliveryStatus.UNKNOWN)
                throw new IllegalArgumentException("只有失败或结果未知的任务可以手动重试");
            ReceivedMail mail=mails.findById(task.mailId).orElseThrow();
            if (!payloads.exists(mail.payloadName)) throw new IllegalArgumentException("邮件任务文件不存在，无法重试");
            task.retryCount=0; finish(task,DeliveryStatus.PENDING,"管理员请求重新发送",Instant.now());
        } finally { lock.unlock(); }
    }
}
