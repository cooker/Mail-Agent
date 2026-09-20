package io.mailagent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.io.IOException;

@Service
public class LocalMailService {
    private final MailRepository mails;
    private final DeliveryRepository deliveries;
    private final PayloadStore payloads;
    private final AccountLocks locks;
    private final TransactionTemplate tx;
    public LocalMailService(MailRepository mails,DeliveryRepository deliveries,PayloadStore payloads,
            AccountLocks locks,TransactionTemplate tx) {
        this.mails=mails; this.deliveries=deliveries; this.payloads=payloads; this.locks=locks; this.tx=tx;
    }
    public void delete(Long id) {
        ReceivedMail initial=mails.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var lock=locks.forAccount(initial.accountId);
        if (!lock.tryLock()) throw new IllegalArgumentException("该邮箱正在收取或发送，请稍后重试删除");
        try {
            // Commit the tombstone first: a file cleanup failure cannot expose or resend this mail.
            String payload=tx.execute(status -> {
                ReceivedMail mail=mails.findById(id).orElseThrow();
                mail.deleted=true; mail.subject=null; mail.sender=null; mail.recipients=null; mail.matchedRules=null;
                mail.outcome="已在本地删除"; mails.save(mail);
                for (Delivery task:deliveries.findByMailIdOrderById(id)) {
                    if (task.status==DeliveryStatus.PENDING || task.status==DeliveryStatus.RETRY) {
                        task.status=DeliveryStatus.FAILED; task.nextAttempt=null;
                        task.lastError="邮件已在本地删除，转发已停止"; task.updatedAt=Instant.now(); deliveries.save(task);
                    }
                }
                return mail.payloadName;
            });
            try { payloads.delete(payload); }
            catch (IOException e) { throw new IllegalArgumentException("邮件已隐藏且停止转发，但文件清理失败，请修复存储权限后重试删除"); }
            tx.executeWithoutResult(status -> {
                ReceivedMail mail=mails.findById(id).orElseThrow(); mail.payloadName=null; mails.save(mail);
            });
        } finally { lock.unlock(); }
    }
}
