package io.mailagent;

import com.icegreen.greenmail.util.*;
import com.icegreen.greenmail.user.GreenMailUser;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1",
        "app.admin-username=admin","app.admin-password=integration-password",
        "app.scheduling-enabled=false"})
@AutoConfigureMockMvc
@Import(MailAgentIntegrationTest.TestConfig.class)
class MailAgentIntegrationTest {
    static GreenMail green=new GreenMail(new ServerSetup[]{new ServerSetup(0,"127.0.0.1",ServerSetup.PROTOCOL_IMAPS),
            new ServerSetup(0,"127.0.0.1",ServerSetup.PROTOCOL_SMTPS)});
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir",()->"./target/test-data/"+UUID.randomUUID());
    }
    @BeforeAll static void startMail() { green.start(); }
    @AfterAll static void stopMail() { green.stop(); }
    @Autowired AccountRepository accounts; @Autowired RuleRepository rules; @Autowired MailRepository mails;
    @Autowired DeliveryRepository deliveries; @Autowired AttemptRepository attempts; @Autowired ConfigurationService config;
    @Autowired MailPollingService polling; @Autowired DeliveryService sending; @Autowired PayloadStore payloads;
    @Autowired SharePageRepository sharePages; @Autowired ShareService shareService; @Autowired MailContentService mailContents;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired TestGateway gateway; @Autowired MockMvc mvc; @Autowired ForwardComposer composer;
    private GreenMailUser source; private GreenMailUser archive;
    @BeforeEach void reset() throws Exception {
        attempts.deleteAll(); deliveries.deleteAll(); mails.deleteAll(); rules.deleteAll(); sharePages.deleteAll(); accounts.deleteAll();
        green.reset(); gateway.failure="";
        source=green.setUser("source@example.com","source@example.com","secret-pass");
        archive=green.setUser("archive@example.com","archive@example.com","secret-pass");
        green.setUser("other@example.com","other@example.com","secret-pass");
    }
    Account account(String email,int history) {
        AccountForm f=new AccountForm(); f.name=email; f.email=email; f.imapHost="127.0.0.1"; f.imapPort=green.getImaps().getPort();
        f.smtpHost="127.0.0.1"; f.smtpPort=green.getSmtps().getPort(); f.imapUsername=email; f.smtpUsername=email;
        f.imapPassword="secret-pass"; f.smtpPassword="secret-pass"; f.historyDays=history; f.enabled=true;
        return config.saveAccount(null,f);
    }
    ForwardRule rule(String targets) {
        RuleForm f=new RuleForm(); f.name="账单转发"; f.subjectPattern="账单"; f.targets=targets; return config.saveRule(null,f);
    }
    MimeMessage message(String subject) throws Exception {
        MimeMessage m=new MimeMessage(Session.getInstance(new Properties()));
        m.setFrom("vendor@example.com"); m.setRecipients(Message.RecipientType.TO,"source@example.com");
        m.setSubject(subject,"UTF-8"); m.setSentDate(Date.from(Instant.now().minus(Duration.ofDays(90))));
        m.setText("中文正文","UTF-8"); m.saveChanges(); return m;
    }
    Delivery pending(Account account) throws Exception {
        rule("archive@example.com"); source.deliver(message("账单")); polling.poll(account.id);
        assertThat(deliveries.findAll()).hasSize(1); return deliveries.findAll().getFirst();
    }
    @Test void historyMimeForwardingDedupAndReadonlySource() throws Exception {
        Account a=account("source@example.com",7); rule("archive@example.com, other@example.com, source@example.com"); rule("archive@example.com");
        MimeMessage original=message("九月账单"); original.setReplyTo(InternetAddress.parse("reply@example.com"));
        MimeMultipart related=new MimeMultipart("related");
        MimeBodyPart html=new MimeBodyPart(); html.setContent("<p>中文正文<img src=\"cid:logo\"></p>","text/html; charset=UTF-8"); related.addBodyPart(html);
        MimeBodyPart image=new MimeBodyPart(); image.setDataHandler(new DataHandler(new ByteArrayDataSource(new byte[]{1,2,3},"image/png")));
        image.setHeader("Content-ID","<logo>"); image.setDisposition(Part.INLINE); related.addBodyPart(image);
        MimeBodyPart content=new MimeBodyPart(); content.setContent(related);
        MimeBodyPart attachment=new MimeBodyPart(); attachment.setDataHandler(new DataHandler(new ByteArrayDataSource("账单附件".getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/octet-stream")));
        attachment.setFileName("账单.txt"); attachment.setDisposition(Part.ATTACHMENT);
        MimeMultipart mixed=new MimeMultipart(); mixed.addBodyPart(content); mixed.addBodyPart(attachment); original.setContent(mixed); original.saveChanges(); source.deliver(original);
        gateway.test(a);
        polling.poll(a.id); polling.poll(a.id);
        assertThat(mails.count()).isEqualTo(1); assertThat(deliveries.count()).isEqualTo(2);
        ReceivedMail stored=mails.findAll().getFirst(); assertThat(payloads.exists(stored.payloadName)).isTrue();
        List<String> storedMime=new ArrayList<>(); inspectMime(payloads.read(stored.payloadName),storedMime);
        assertThat(storedMime).as("spooled source MIME").contains("<logo>");
        for (Delivery task:deliveries.findAll()) sending.send(task.id);
        assertThat(deliveries.findAll()).allMatch(d->d.status==DeliveryStatus.SENT);
        assertThat(payloads.exists(stored.payloadName)).isTrue();
        var mailView=mailContents.read(stored.payloadName);
        assertThat(mailView.available()).isTrue();
        assertThat(mailView.text()).contains("中文正文").doesNotContain("<img");
        assertThat(mailView.html()).contains("<p>中文正文</p>").doesNotContain("<img","cid:logo");
        assertThat(mailView.attachments()).extracting(MailContentService.Attachment::name).contains("账单.txt");
        assertThat(mailContents.download(stored.payloadName,0).bytes())
                .isEqualTo("账单附件".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ShareForm attachmentShare=new ShareForm(); attachmentShare.title="附件分享"; attachmentShare.accountId=a.id; attachmentShare.subjectKeyword="账单";
        ShareService.Created shared=shareService.create(attachmentShare);
        mvc.perform(get("/records/"+stored.id).with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<p>中文正文</p>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("cid:logo"))));
        mvc.perform(get("/s/"+shared.token()+"/mail/"+stored.id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<p>中文正文</p>")));
        mvc.perform(get("/s/"+shared.token()+"/mail/"+stored.id+"/attachments/0"))
                .andExpect(status().isOk()).andExpect(content().bytes("账单附件".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try (Store store=gateway.openImap(account("archive@example.com",0))) {
            Folder inbox=store.getFolder("INBOX"); inbox.open(Folder.READ_ONLY); assertThat(inbox.getMessageCount()).isEqualTo(1);
            MimeMessage forwarded=(MimeMessage)inbox.getMessage(1);
            assertThat(forwarded.getSubject()).isEqualTo("Fwd: 九月账单");
            assertThat(((InternetAddress)forwarded.getFrom()[0]).getAddress()).isEqualTo(a.email);
            assertThat(((InternetAddress)forwarded.getReplyTo()[0]).getAddress()).isEqualTo("reply@example.com");
            assertThat(composer.forwarded(forwarded)).isTrue();
            java.io.ByteArrayOutputStream receivedRaw=new java.io.ByteArrayOutputStream(); forwarded.writeTo(receivedRaw);
            MimeMessage reparsed=new MimeMessage(Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(receivedRaw.toByteArray()));
            List<String> seen=new ArrayList<>(); inspectMime(reparsed,seen);
            assertThat(seen).contains("账单.txt","<logo>","账单附件");
            assertThat(seen.stream().anyMatch(v->v.contains("cid:logo"))).isTrue(); inbox.close(false);
        }
        try (Store store=gateway.openImap(a)) {
            Folder inbox=store.getFolder("INBOX"); inbox.open(Folder.READ_ONLY);
            assertThat(inbox.getMessage(1).isSet(Flags.Flag.SEEN)).isFalse(); assertThat(inbox.getMessageCount()).isEqualTo(1); inbox.close(false);
        }
    }
    static void inspectMime(Part part,List<String> seen) throws Exception {
        if (part.getFileName()!=null) seen.add(part.getFileName());
        String[] cid=part.getHeader("Content-ID"); if (cid!=null) seen.addAll(Arrays.asList(cid));
        Object content=part.getContent();
        if (content instanceof Multipart mp) for (int i=0;i<mp.getCount();i++) inspectMime(mp.getBodyPart(i),seen);
        else if (content instanceof String s) seen.add(s);
        else if (content instanceof java.io.InputStream stream) try(stream) { seen.add(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)); }
    }
    @Test void failedTransactionDoesNotAdvanceCursorOrLeavePartialTasks() throws Exception {
        Account a=account("source@example.com",1); rule("archive@example.com"); source.deliver(message("账单"));
        jdbc.execute("alter table deliveries add constraint test_reject_target check (target <> 'archive@example.com')");
        try {
            polling.poll(a.id);
            assertThat(mails.count()).isZero(); assertThat(deliveries.count()).isZero();
            assertThat(accounts.findById(a.id).orElseThrow().lastUid).isZero();
            assertThat(accounts.findById(a.id).orElseThrow().lastError).isNotBlank();
        } finally { jdbc.execute("alter table deliveries drop constraint test_reject_target"); }
        polling.poll(a.id); assertThat(mails.count()).isEqualTo(1); assertThat(deliveries.count()).isEqualTo(1);
        assertThat(accounts.findById(a.id).orElseThrow().lastError).isNull();
    }
    @Test void historicalWindowUsesServerDateAndContinuesAcrossBatches() throws Exception {
        Account a=account("source@example.com",7); rule("archive@example.com");
        var inbox=green.getManagers().getImapHostManager().getInbox(source);
        inbox.appendMessage(message("范围外账单"),new Flags(),Date.from(Instant.now().minus(Duration.ofDays(8))));
        for (int i=0;i<100;i++) inbox.appendMessage(message("范围内账单-"+i),new Flags(),new Date());
        polling.poll(a.id);
        assertThat(mails.count()).isEqualTo(100); assertThat(deliveries.count()).isEqualTo(99);
        source.deliver(message("回溯期间新账单")); polling.poll(a.id);
        assertThat(mails.count()).isEqualTo(102); assertThat(deliveries.count()).isEqualTo(101);
        assertThat(mails.findAll()).filteredOn(m->m.subject.equals("范围外账单")).allMatch(m->m.outcome.equals("超出回溯范围"));
        polling.poll(a.id); assertThat(deliveries.count()).isEqualTo(101);
    }
    @Test void newOnlyPauseResumeAndAccountIsolation() throws Exception {
        Account a=account("source@example.com",0); rule("archive@example.com");
        source.deliver(message("旧账单")); polling.poll(a.id); assertThat(mails.count()).isZero();
        source.deliver(message("新账单")); polling.poll(a.id); assertThat(mails.count()).isEqualTo(1);
        config.toggle(a.id); source.deliver(message("暂停期间账单")); polling.poll(a.id); assertThat(mails.count()).isEqualTo(1);
        config.toggle(a.id); polling.poll(a.id); assertThat(mails.count()).isEqualTo(2);
        Account b=account("other@example.com",1); green.getUserManager().getUser("other@example.com").deliver(message("新账单"));
        polling.poll(b.id); assertThat(mails.count()).isEqualTo(3); assertThat(mails.findAll()).extracting(m->m.accountId).contains(a.id,b.id);
    }
    @Test void loopHeaderSkippedAndReplyToFallsBackToFrom() throws Exception {
        Account a=account("source@example.com",1); rule("archive@example.com");
        MimeMessage loop=message("账单"); loop.setHeader(ForwardComposer.LOOP_HEADER,"1"); source.deliver(loop); polling.poll(a.id);
        assertThat(deliveries.count()).isZero(); assertThat(mails.findAll().getFirst().outcome).isEqualTo("跳过系统转发邮件");
        Delivery task=new Delivery(); task.id=12L; task.target="archive@example.com";
        var composed=composer.compose(message("账单"),a,task,UUID.randomUUID()+".eml");
        assertThat(((InternetAddress)composed.getReplyTo()[0]).getAddress()).isEqualTo("vendor@example.com");
    }
    @Test void retriesBackoffExhaustionAndManualRequeue() throws Exception {
        Account a=account("source@example.com",1); Delivery task=pending(a); gateway.failure="TEMP";
        for (int minutes:new int[]{1,5,15}) {
            sending.send(task.id); task=deliveries.findById(task.id).orElseThrow();
            assertThat(task.status).isEqualTo(DeliveryStatus.RETRY);
            assertThat(Duration.between(Instant.now(),task.nextAttempt).toSeconds()).isBetween(minutes*60L-5,minutes*60L);
            task.nextAttempt=Instant.now().minusSeconds(1); deliveries.save(task);
        }
        sending.send(task.id); task=deliveries.findById(task.id).orElseThrow(); assertThat(task.status).isEqualTo(DeliveryStatus.FAILED);
        sending.retry(task.id); task=deliveries.findById(task.id).orElseThrow(); assertThat(task.retryCount).isZero();
        gateway.failure=""; sending.send(task.id); assertThat(deliveries.findById(task.id).orElseThrow().status).isEqualTo(DeliveryStatus.SENT);
    }
    @Test void uncertainSubmissionAndRestartNeverAutomaticallyResend() throws Exception {
        Account a=account("source@example.com",1); Delivery task=pending(a); gateway.failure="UNKNOWN";
        sending.send(task.id); assertThat(deliveries.findById(task.id).orElseThrow().status).isEqualTo(DeliveryStatus.UNKNOWN);
        gateway.failure=""; sending.send(task.id); assertThat(deliveries.findById(task.id).orElseThrow().attempts).isEqualTo(1);
        sending.retry(task.id); task=deliveries.findById(task.id).orElseThrow(); task.status=DeliveryStatus.SENDING; deliveries.save(task);
        sending.recoverInterrupted(); assertThat(deliveries.findById(task.id).orElseThrow().status).isEqualTo(DeliveryStatus.UNKNOWN);
        assertThat(payloads.exists(mails.findAll().getFirst().payloadName)).isTrue();
    }
    @Test void authFailurePausesAndUidValidityRequiresReset() throws Exception {
        Account a=account("source@example.com",1); Delivery task=pending(a); gateway.failure="AUTH";
        sending.send(task.id); assertThat(accounts.findById(a.id).orElseThrow().enabled).isFalse();
        assertThat(deliveries.findById(task.id).orElseThrow().status).isEqualTo(DeliveryStatus.FAILED);
        a=accounts.findById(a.id).orElseThrow(); a.enabled=true; a.uidValidity=a.uidValidity+1; accounts.save(a);
        polling.poll(a.id); a=accounts.findById(a.id).orElseThrow(); assertThat(a.resetRequired).isTrue(); assertThat(a.enabled).isFalse();
        Long id=a.id; assertThatThrownBy(()->config.toggle(id)).isInstanceOf(IllegalArgumentException.class);
        config.reset(a.id,0); a=accounts.findById(a.id).orElseThrow(); assertThat(a.uidValidity).isNull(); assertThat(a.enabled).isFalse();
    }
    @Test void partialTargetFailureKeepsPayloadAndFrozenTargets() throws Exception {
        Account a=account("source@example.com",1); ForwardRule r=rule("archive@example.com, other@example.com");
        source.deliver(message("账单")); polling.poll(a.id);
        var tasks=deliveries.findAll(); sending.send(tasks.get(0).id); gateway.failure="PERMANENT"; sending.send(tasks.get(1).id);
        r.targets.clear(); r.targets.add("new@example.com"); rules.save(r);
        assertThat(deliveries.findAll()).extracting(d->d.target).containsExactlyInAnyOrder("archive@example.com","other@example.com");
        assertThat(payloads.exists(mails.findAll().getFirst().payloadName)).isTrue();
        assertThat(deliveries.findAll()).extracting(d->d.status).containsExactlyInAnyOrder(DeliveryStatus.SENT,DeliveryStatus.FAILED);
    }
    @Test void connectionFailureIsSafeToRetryAndImapAuthFailurePauses() throws Exception {
        Account a=account("source@example.com",1); Delivery task=pending(a); gateway.failure="CONNECT";
        sending.send(task.id); assertThat(deliveries.findById(task.id).orElseThrow().status).isEqualTo(DeliveryStatus.RETRY);
        AccountForm form=AccountForm.from(accounts.findById(a.id).orElseThrow()); form.imapPassword="incorrect";
        config.saveAccount(a.id,form); polling.poll(a.id); assertThat(accounts.findById(a.id).orElseThrow().enabled).isFalse();
    }
    @Test void adminWorkflowSecurityAndTemplates() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("登录邮箱工作台")));
        mvc.perform(post("/accounts/save").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("username","admin").param("password","integration-password")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/accounts/save").with(user("admin")).with(csrf())
                .param("name","测试邮箱").param("email","source@example.com")
                .param("imapHost","127.0.0.1").param("imapPort",String.valueOf(green.getImaps().getPort())).param("imapTls","SSL")
                .param("imapUsername","source@example.com").param("imapPassword","secret-pass")
                .param("smtpHost","127.0.0.1").param("smtpPort",String.valueOf(green.getSmtps().getPort())).param("smtpTls","SSL")
                .param("smtpUsername","source@example.com").param("smtpPassword","secret-pass").param("historyDays","1"))
                .andExpect(status().is3xxRedirection());
        Account a=accounts.findAll().getFirst();
        assertThat(a.imapSecret).isNotEqualTo("secret-pass");
        assertThat(a.smtpSecret).isNotEqualTo("secret-pass");
        mvc.perform(post("/accounts/"+a.id+"/test").with(user("admin")).with(csrf())).andExpect(flash().attributeExists("notice"));
        mvc.perform(post("/rules/save").with(user("admin")).with(csrf()).param("name","财务规则").param("subjectPattern","账单")
                .param("targets","archive@example.com").param("enabled","true").param("ignoreCase","true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/accounts/"+a.id+"/toggle").with(user("admin")).with(csrf())).andExpect(status().is3xxRedirection());
        source.deliver(message("账单")); polling.poll(a.id); var task=deliveries.findAll().getFirst(); sending.send(task.id);
        for (String path:List.of("/","/accounts","/accounts/new","/accounts/"+a.id+"/edit","/rules","/rules/new",
                "/rules/"+rules.findAll().getFirst().id+"/edit","/rules/test","/records","/records/"+task.mailId,"/shares")) {
            mvc.perform(get(path).with(user("admin"))).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-pass"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(a.imapSecret))));
        }
        mvc.perform(post("/rules/test").with(user("admin")).with(csrf()).param("accountId",a.id.toString()).param("subject","账单"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("archive@example.com")));
        mvc.perform(get("/records").with(user("admin")).param("status","SENT").param("accountId",a.id.toString())
                .param("from",LocalDate.now().toString()).param("to",LocalDate.now().toString())).andExpect(status().isOk());
        mvc.perform(post("/rules/save").with(user("admin")).with(csrf()).param("name","坏规则").param("subjectMode","REGEX")
                .param("subjectPattern","[").param("targets","archive@example.com")).andExpect(status().isBadRequest());
    }

    @Test void publicShareCombinesAccountRecipientAndSubjectAndDeletionRevokesToken() throws Exception {
        Account a=account("source@example.com",1);
        Account other=account("other@example.com",1);
        source.deliver(message("九月账单")); source.deliver(message("付款通知"));
        source.deliver(message("十月账单")); source.deliver(message("内部通知"));
        green.getUserManager().getUser("other@example.com").deliver(message("十一月账单"));
        polling.poll(a.id); polling.poll(other.id);
        List<ReceivedMail> stored=mails.findAll();
        stored.forEach(mail->mail.recipients=mail.subject.equals("十月账单") ? "team@example.com"
                : mail.subject.equals("付款通知") ? "audit@example.com" : "finance@example.com");
        mails.saveAll(stored);

        ShareForm form=new ShareForm(); form.title="账单分享"; form.accountId=a.id;
        form.subjectKeyword="账单\n付款通知\n账单";
        form.recipientKeyword="FINANCE@EXAMPLE.COM, audit@example.com; finance@example.com";
        ShareService.Created created=shareService.create(form);
        SharePage persistedShare=sharePages.findById(created.page().id).orElseThrow();
        assertThat(persistedShare.subjectKeyword).isEqualTo("账单\n付款通知");
        assertThat(persistedShare.recipientKeyword).isEqualTo("FINANCE@EXAMPLE.COM\naudit@example.com");
        assertThat(persistedShare.tokenSecret).isNotBlank().doesNotContain(created.token());
        assertThat(shareService.token(persistedShare)).contains(created.token());
        mvc.perform(get("/shares").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(created.token())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("查看 ↗")));
        ReceivedMail matching=stored.stream().filter(m->m.subject.equals("九月账单")).findFirst().orElseThrow();
        ReceivedMail secondMatch=stored.stream().filter(m->m.subject.equals("付款通知")).findFirst().orElseThrow();
        ReceivedMail hiddenRecipient=stored.stream().filter(m->m.subject.equals("十月账单")).findFirst().orElseThrow();
        ReceivedMail hiddenSubject=stored.stream().filter(m->m.subject.equals("内部通知")).findFirst().orElseThrow();
        ReceivedMail hiddenAccount=stored.stream().filter(m->m.subject.equals("十一月账单")).findFirst().orElseThrow();

        mvc.perform(get("/s/"+created.token())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("九月账单")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("付款通知")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FINANCE@EXAMPLE.COM")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("audit@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("十月账单"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("内部通知"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("十一月账单"))));
        mvc.perform(get("/s/"+created.token()+"/mail/"+matching.id)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("中文正文")));
        mvc.perform(get("/s/"+created.token()+"/mail/"+secondMatch.id)).andExpect(status().isOk());
        for (ReceivedMail hidden:List.of(hiddenRecipient,hiddenSubject,hiddenAccount))
            mvc.perform(get("/s/"+created.token()+"/mail/"+hidden.id)).andExpect(status().isNotFound());
        mvc.perform(get("/s/"+created.token()+"/mail/"+hiddenRecipient.id+"/attachments/0")).andExpect(status().isNotFound());
        mvc.perform(get("/s/invalid-token")).andExpect(status().isNotFound());

        String originalHash=persistedShare.tokenHash; String originalSecret=persistedShare.tokenSecret;
        mvc.perform(get("/shares/"+created.page().id+"/edit").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("账单分享")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("audit@example.com")));
        mvc.perform(post("/shares/"+created.page().id+"/edit").with(user("admin"))).andExpect(status().isForbidden());
        LocalDate newExpiry=LocalDate.now().plusDays(1);
        mvc.perform(post("/shares/"+created.page().id+"/edit").with(user("admin")).with(csrf())
                        .param("title","更新后的分享").param("accountId",a.id.toString())
                        .param("subjectKeyword","内部通知\n不存在").param("recipientKeyword","finance@example.com")
                        .param("expiresOn",newExpiry.toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shares"));
        persistedShare=sharePages.findById(created.page().id).orElseThrow();
        assertThat(persistedShare.title).isEqualTo("更新后的分享");
        assertThat(persistedShare.subjectKeyword).isEqualTo("内部通知\n不存在");
        assertThat(persistedShare.tokenHash).isEqualTo(originalHash);
        assertThat(persistedShare.tokenSecret).isEqualTo(originalSecret);
        assertThat(ShareForm.from(persistedShare).expiresOn).isEqualTo(newExpiry);
        mvc.perform(get("/s/"+created.token())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("内部通知")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("九月账单"))));
        mvc.perform(get("/s/"+created.token()+"/mail/"+matching.id)).andExpect(status().isNotFound());
        mvc.perform(get("/s/"+created.token()+"/mail/"+hiddenSubject.id)).andExpect(status().isOk());

        shareService.toggle(created.page().id);
        mvc.perform(get("/s/"+created.token())).andExpect(status().isNotFound());
        shareService.toggle(created.page().id);
        persistedShare=sharePages.findById(created.page().id).orElseThrow();
        persistedShare.tokenSecret=null; sharePages.save(persistedShare);
        mvc.perform(get("/shares").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("生成可查看链接")));
        mvc.perform(post("/shares/"+created.page().id+"/regenerate").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(post("/shares/"+created.page().id+"/regenerate").with(user("admin")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shares"));
        persistedShare=sharePages.findById(created.page().id).orElseThrow();
        String regenerated=shareService.token(persistedShare).orElseThrow();
        assertThat(regenerated).isNotEqualTo(created.token());
        assertThat(persistedShare.tokenSecret).doesNotContain(regenerated);
        mvc.perform(get("/s/"+created.token())).andExpect(status().isNotFound());
        mvc.perform(get("/s/"+regenerated)).andExpect(status().isOk());
        mvc.perform(get("/shares").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(regenerated)));
        mvc.perform(post("/shares/"+created.page().id+"/delete").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(post("/shares/"+created.page().id+"/delete").with(user("admin")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shares"));
        assertThat(sharePages.existsById(created.page().id)).isFalse();
        mvc.perform(get("/s/"+regenerated)).andExpect(status().isNotFound());
    }
    @Test void mailboxContentFiltersAccountSenderRecipientAndSubjectWithLiteralWildcards() throws Exception {
        Account a=account("source@example.com",1);
        Account other=account("other@example.com",1);
        source.deliver(message("九月账单")); source.deliver(message("内部通知"));
        source.deliver(message("100% 完成")); source.deliver(message("100X 完成"));
        green.getUserManager().getUser("other@example.com").deliver(message("九月账单"));
        polling.poll(a.id); polling.poll(other.id);

        List<ReceivedMail> stored=mails.findAll();
        for (ReceivedMail mail:stored) {
            if (mail.accountId.equals(other.id)) {
                mail.sender="Alice <alice@example.com>"; mail.recipients="finance@example.com";
            } else if (mail.subject.equals("九月账单")) {
                mail.sender="Alice <alice@example.com>"; mail.recipients="finance@example.com, audit@example.com";
            } else if (mail.subject.equals("内部通知")) {
                mail.sender="bob@example.com"; mail.recipients="team@example.com";
            } else if (mail.subject.equals("100% 完成")) {
                mail.sender="percent@example.com"; mail.recipients="ops@example.com";
            } else {
                mail.sender="letter@example.com"; mail.recipients="ops@example.com";
            }
        }
        mails.saveAll(stored);

        mvc.perform(get("/records").with(user("admin"))
                        .param("accountId",a.id.toString()).param("sender","ALICE@EXAMPLE.COM")
                        .param("recipient","FINANCE@EXAMPLE.COM").param("subject","账单"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("九月账单")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("内部通知"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"ALICE@EXAMPLE.COM\"")));

        mvc.perform(get("/records").with(user("admin")).param("accountId",a.id.toString()).param("subject","100%"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100% 完成")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("100X 完成"))));
    }
    @Test void bulkManualSyncIsProtectedAndSkipsPausedAccounts() throws Exception {
        Account a=account("source@example.com",1);
        Account b=account("other@example.com",1);
        source.deliver(message("手动收取一"));
        green.getUserManager().getUser("other@example.com").deliver(message("手动收取二"));
        mvc.perform(post("/accounts/sync").with(user("admin")).param("all","true"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/accounts/sync").with(user("admin")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/accounts/sync").with(user("admin")).with(csrf())
                .param("accountIds",a.id.toString(),b.id.toString(),a.id.toString()))
                .andExpect(redirectedUrl("/accounts"));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while ((accounts.findById(a.id).orElseThrow().lastChecked==null ||
                accounts.findById(b.id).orElseThrow().lastChecked==null) && System.nanoTime()<deadline) Thread.sleep(20);
        assertThat(mails.countByDeletedFalse()).isEqualTo(2);
        a=accounts.findById(a.id).orElseThrow(); a.enabled=false; accounts.save(a);
        source.deliver(message("暂停不收取"));
        mvc.perform(post("/accounts/sync").with(user("admin")).with(csrf()).param("accountIds",a.id.toString()))
                .andExpect(flash().attribute("notice",org.hamcrest.Matchers.containsString("已提交 0 个")));
        green.getUserManager().getUser("other@example.com").deliver(message("全部同步"));
        Instant previous=accounts.findById(b.id).orElseThrow().lastChecked;
        mvc.perform(post("/accounts/sync").with(user("admin")).with(csrf()).param("all","true"))
                .andExpect(redirectedUrl("/accounts"));
        deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!accounts.findById(b.id).orElseThrow().lastChecked.isAfter(previous) && System.nanoTime()<deadline) Thread.sleep(20);
        assertThat(mails.countByDeletedFalse()).isEqualTo(3);
        mvc.perform(get("/accounts").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("同步选中邮箱")));
    }

    @Test void localDeletionRevokesViewsStopsDeliveryAndRetainsServerAndDedup() throws Exception {
        Account a=account("source@example.com",1); Delivery task=pending(a);
        ReceivedMail mail=mails.findById(task.mailId).orElseThrow(); String payload=mail.payloadName;
        ShareForm form=new ShareForm(); form.title="删除测试"; form.subjectKeyword="账单";
        var share=shareService.create(form);
        mvc.perform(post("/records/"+mail.id+"/delete").with(user("admin"))).andExpect(status().isForbidden());
        mvc.perform(post("/records/"+mail.id+"/delete").with(user("admin")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(get("/records/"+mail.id).with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("删除本地邮件")));
        mvc.perform(post("/records/"+mail.id+"/delete").with(user("admin")).with(csrf()).param("confirmed","true"))
                .andExpect(redirectedUrl("/records"));
        assertThat(payloads.exists(payload)).isFalse();
        assertThat(mails.findById(mail.id).orElseThrow().deleted).isTrue();
        assertThat(mails.countByDeletedFalse()).isZero();
        assertThat(shareService.mails(share.page(),0)).isEmpty();
        for (String path:List.of("/records/"+mail.id,"/records/"+mail.id+"/attachments/0",
                "/s/"+share.token()+"/mail/"+mail.id,"/s/"+share.token()+"/mail/"+mail.id+"/attachments/0"))
            mvc.perform(get(path).with(user("admin"))).andExpect(status().isNotFound());
        assertThatThrownBy(()->sending.retry(task.id)).isInstanceOf(IllegalArgumentException.class);
        sending.send(task.id);
        assertThat(deliveries.findById(task.id).orElseThrow().attempts).isZero();
        // Replay the same UID range to prove deletion cannot be undone by a repeated fetch.
        a=accounts.findById(a.id).orElseThrow(); a.lastUid=0; accounts.save(a); polling.poll(a.id);
        assertThat(mails.count()).isEqualTo(1); assertThat(mails.countByDeletedFalse()).isZero();
        try (Store store=gateway.openImap(accounts.findById(a.id).orElseThrow())) {
            Folder inbox=store.getFolder("INBOX"); inbox.open(Folder.READ_ONLY);
            assertThat(inbox.getMessageCount()).isEqualTo(1);
            assertThat(inbox.getMessage(1).isSet(Flags.Flag.DELETED)).isFalse();
            assertThat(inbox.getMessage(1).isSet(Flags.Flag.SEEN)).isFalse();
            inbox.close(false);
        }
        mvc.perform(get("/records").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("此条件下暂无邮件")));
        mvc.perform(get("/").with(user("admin"))).andExpect(status().isOk());
    }

    @TestConfiguration static class TestConfig {
        @Bean @Primary TestGateway testGateway(SecretCipher cipher) { return new TestGateway(cipher); }
    }
    static class TestGateway extends MailGateway {
        volatile String failure="";
        TestGateway(SecretCipher cipher) { super(cipher); }
        @Override protected Properties properties(String protocol,TlsMode mode) {
            Properties p=super.properties(protocol,mode);
            // Self-signed GreenMail certificate only. Production never disables certificate checks.
            p.setProperty("mail."+protocol+".ssl.trust","*"); p.setProperty("mail."+protocol+".ssl.checkserveridentity","false"); return p;
        }
        @Override public Transport openSmtp(Account a) throws MessagingException {
            if (failure.equals("AUTH")) throw new AuthenticationFailedException("test");
            if (failure.equals("CONNECT")) throw new MessagingException("test connection refused");
            if (failure.isEmpty()) return super.openSmtp(a);
            Transport t=mock(Transport.class);
            Exception error=switch(failure) {
                case "TEMP" -> new SMTPSendFailedException("DATA",451,"test",null,null,null,null);
                case "PERMANENT" -> new SMTPSendFailedException("DATA",550,"test",null,null,null,null);
                default -> new MessagingException("test acknowledgement lost");
            };
            doThrow(error).when(t).sendMessage(any(Message.class),any(Address[].class)); return t;
        }
    }
}
