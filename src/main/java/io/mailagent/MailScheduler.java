package io.mailagent;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

@Component
public class MailScheduler {
    private static final Logger log=LoggerFactory.getLogger(MailScheduler.class);
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final AccountRepository accounts; private final DeliveryRepository deliveries;
    private final MailPollingService polling; private final DeliveryService sending;
    private final boolean enabled; private volatile boolean ready;
    public MailScheduler(AccountRepository accounts, DeliveryRepository deliveries, MailPollingService polling,
            DeliveryService sending, @Value("${app.scheduling-enabled:true}") boolean enabled) {
        this.accounts=accounts; this.deliveries=deliveries; this.polling=polling; this.sending=sending; this.enabled=enabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() { sending.recoverInterrupted(); ready=true; }
    @Scheduled(fixedDelayString="${app.poll-delay-ms:60000}", initialDelay=2000)
    public void poll() {
        if (!enabled || !ready) return;
        for (Account account:accounts.findByEnabledTrueOrderById()) run(() -> polling.poll(account.id));
    }
    @Scheduled(fixedDelayString="${app.send-delay-ms:5000}", initialDelay=3000)
    public void send() {
        if (!enabled || !ready) return;
        var due=deliveries.findDue(List.of(DeliveryStatus.PENDING,DeliveryStatus.RETRY),Instant.now(),PageRequest.of(0,100));
        // One worker per account processes its due targets serially; accounts remain independent.
        due.stream().collect(java.util.stream.Collectors.groupingBy(d -> d.accountId)).values()
                .forEach(group -> run(() -> group.forEach(d -> sending.send(d.id))));
    }
    private void run(Runnable action) {
        workers.submit(() -> { try { action.run(); } catch (Exception e) {
            log.warn("后台任务失败，类型={}；未输出服务器响应或凭据",e.getClass().getSimpleName());
        }});
    }
    @PreDestroy public void stop() { ready=false; workers.close(); }
}
