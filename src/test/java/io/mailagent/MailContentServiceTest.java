package io.mailagent;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

class MailContentServiceTest {
    @TempDir Path directory;

    @Test void extractsSafeTextAndDownloadsAttachments() throws Exception {
        PayloadStore store=new PayloadStore(directory.toString());
        MailContentService service=new MailContentService(store);
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()));
        MimeBodyPart html=new MimeBodyPart();
        html.setContent("<h1 style='color:red'>标题</h1><p onclick='alert(1)'>正文</p>"
                +"<script>alert('x')</script><img src='https://tracker.example/pixel'>"
                +"<a href='javascript:alert(2)'>危险链接</a><a href='https://example.com/help'>安全链接</a>","text/html; charset=UTF-8");
        MimeBodyPart file=new MimeBodyPart();
        file.setDataHandler(new DataHandler(new ByteArrayDataSource("附件内容".getBytes(StandardCharsets.UTF_8),"text/plain")));
        file.setFileName("报告.txt");
        MimeMultipart mixed=new MimeMultipart(); mixed.addBodyPart(html); mixed.addBodyPart(file);
        message.setContent(mixed); message.saveChanges();
        String name=store.save(message);

        MailContentService.Content content=service.read(name);
        assertThat(content.available()).isTrue();
        assertThat(content.text()).contains("标题","正文").doesNotContain("<h1>","tracker.example");
        assertThat(content.html()).contains("<h1>标题</h1>","<p>正文</p>","https://example.com/help")
                .doesNotContain("<script","<img","tracker.example","javascript:","onclick=","style=");
        assertThat(content.attachments()).singleElement().satisfies(a->assertThat(a.name()).isEqualTo("报告.txt"));
        MailContentService.Download download=service.download(name,0);
        assertThat(download.name()).isEqualTo("报告.txt");
        assertThat(download.bytes()).isEqualTo("附件内容".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(()->service.download(name,1)).isInstanceOf(IllegalArgumentException.class);
    }
}
