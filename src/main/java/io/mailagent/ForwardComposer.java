package io.mailagent;

import org.springframework.stereotype.Component;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ForwardComposer {
    public static final String LOOP_HEADER = "X-Mail-Agent-Forwarded";
    public boolean forwarded(MimeMessage original) throws MessagingException {
        return original.getHeader(LOOP_HEADER) != null;
    }
    public MimeMessage compose(MimeMessage original, Account account, Delivery delivery, String payloadName) throws Exception {
        String stableId = "<" + payloadName.replace(".eml", "") + "." + delivery.id + "@mail-agent.local>";
        MimeMessage result = new MimeMessage(Session.getInstance(new Properties())) {
            @Override protected void updateMessageID() throws MessagingException { setHeader("Message-ID", stableId); }
        };
        result.setFrom(new InternetAddress(account.email));
        result.setRecipient(Message.RecipientType.TO, new InternetAddress(delivery.target));
        result.setSubject("Fwd: " + Objects.requireNonNullElse(original.getSubject(), ""), StandardCharsets.UTF_8.name());
        Address[] replyTo = original.getReplyTo();
        if (replyTo == null || replyTo.length == 0) replyTo = original.getFrom();
        if (replyTo != null && replyTo.length > 0) result.setReplyTo(replyTo);
        result.setSentDate(new Date());
        result.setHeader(LOOP_HEADER, "1");
        result.setHeader("Auto-Submitted", "auto-generated");
        MimeBodyPart metadata = new MimeBodyPart();
        metadata.setText("---------- 转发邮件 ----------\n发件人: " + AddressUtils.display(original.getFrom())
                + "\n收件人: " + AddressUtils.display(original.getRecipients(Message.RecipientType.TO))
                + "\n抄送: " + AddressUtils.display(original.getRecipients(Message.RecipientType.CC))
                + "\n时间: " + Objects.toString(original.getSentDate(), "")
                + "\n主题: " + Objects.requireNonNullElse(original.getSubject(), "") + "\n", "UTF-8");
        // Preserve the original MIME tree, including related CID images and attachments.
        MimeBodyPart body = new MimeBodyPart();
        body.setDataHandler(original.getDataHandler());
        body.setHeader("Content-Type", original.getContentType());
        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(metadata); mixed.addBodyPart(body);
        result.setContent(mixed); result.saveChanges();
        return result;
    }
}
