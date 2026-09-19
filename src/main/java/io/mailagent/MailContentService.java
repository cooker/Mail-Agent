package io.mailagent;

import jakarta.mail.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MailContentService {
    private static final int MAX_BODY_CHARS=1_000_000;
    private static final int MAX_ATTACHMENT_BYTES=25*1024*1024;
    private static final Safelist HTML_ALLOWLIST=Safelist.relaxed()
            .removeTags("img")
            .removeProtocols("a","href","ftp")
            .addProtocols("a","href","http","https","mailto")
            .addEnforcedAttribute("a","rel","noopener noreferrer");
    private final PayloadStore payloads;

    public MailContentService(PayloadStore payloads) { this.payloads=payloads; }

    public record Attachment(int index,String name,String contentType,long size) {}
    public record Content(boolean available,String text,String html,List<Attachment> attachments) {}
    public record Download(String name,String contentType,byte[] bytes) {}

    public Content read(String payloadName) {
        if (!payloads.exists(payloadName)) return new Content(false,"","",List.of());
        try {
            Collector collector=new Collector();
            collect(payloads.read(payloadName),collector);
            String html=collector.html.isEmpty() ? "" : sanitizeHtml(String.join("<hr>",collector.html));
            String text=!collector.plain.isEmpty() ? String.join("\n\n",collector.plain)
                    : Jsoup.parse(html).text();
            return new Content(true,clip(text),html,List.copyOf(collector.attachments));
        } catch (Exception e) {
            return new Content(false,"","",List.of());
        }
    }

    public Download download(String payloadName,int requestedIndex) {
        if (requestedIndex<0 || !payloads.exists(payloadName)) throw new IllegalArgumentException("附件不存在");
        try {
            DownloadCollector collector=new DownloadCollector(requestedIndex);
            findAttachment(payloads.read(payloadName),collector);
            if (collector.result==null) throw new IllegalArgumentException("附件不存在");
            return collector.result;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("附件读取失败"); }
    }

    private void collect(Part part,Collector collector) throws Exception {
        if (isAttachment(part)) {
            collector.attachments.add(new Attachment(collector.attachments.size(),fileName(part),safeType(part),part.getSize()));
            return;
        }
        if (part.isMimeType("text/plain")) {
            Object value=part.getContent(); if (value instanceof String text) collector.plain.add(clip(text)); return;
        }
        if (part.isMimeType("text/html")) {
            Object value=part.getContent(); if (value instanceof String html) collector.html.add(sanitizeHtml(html)); return;
        }
        if (!part.isMimeType("multipart/*") && !part.isMimeType("message/rfc822")) return;
        Object value=part.getContent();
        if (value instanceof Multipart multipart)
            for (int i=0;i<multipart.getCount();i++) collect(multipart.getBodyPart(i),collector);
        else if (value instanceof Message message) collect(message,collector);
    }

    private void findAttachment(Part part,DownloadCollector collector) throws Exception {
        if (collector.result!=null) return;
        if (isAttachment(part)) {
            int current=collector.seen++;
            if (current==collector.requested) {
                try (InputStream input=part.getInputStream()) {
                    byte[] bytes=input.readNBytes(MAX_ATTACHMENT_BYTES+1);
                    if (bytes.length>MAX_ATTACHMENT_BYTES) throw new IllegalArgumentException("附件超过 25 MB，无法通过页面下载");
                    collector.result=new Download(fileName(part),safeType(part),bytes);
                }
            }
            return;
        }
        if (!part.isMimeType("multipart/*") && !part.isMimeType("message/rfc822")) return;
        Object value=part.getContent();
        if (value instanceof Multipart multipart)
            for (int i=0;i<multipart.getCount();i++) findAttachment(multipart.getBodyPart(i),collector);
        else if (value instanceof Message message) findAttachment(message,collector);
    }

    private boolean isAttachment(Part part) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName()!=null;
    }
    private String fileName(Part part) throws MessagingException {
        String value=Objects.requireNonNullElse(part.getFileName(),"attachment");
        value=value.replace('\\','_').replace('/','_').replace("\r","").replace("\n","").strip();
        return value.isEmpty() ? "attachment" : AddressUtils.clip(value,200);
    }
    private String safeType(Part part) throws MessagingException {
        String type=Objects.requireNonNullElse(part.getContentType(),"application/octet-stream").split(";",2)[0].strip();
        return type.matches("[A-Za-z0-9!#$&^_.+/-]+") ? type : "application/octet-stream";
    }
    private String sanitizeHtml(String html) {
        boolean truncated=html.length()>MAX_BODY_CHARS;
        String limited=truncated ? html.substring(0,MAX_BODY_CHARS) : html;
        String marker=truncated ? "<p>[正文已截断]</p>" : "";
        return Jsoup.clean(limited+marker,HTML_ALLOWLIST);
    }
    private String clip(String text) { return text.length()<=MAX_BODY_CHARS ? text : text.substring(0,MAX_BODY_CHARS)+"\n\n[正文已截断]"; }
    private static class Collector { final List<String> plain=new ArrayList<>(); final List<String> html=new ArrayList<>(); final List<Attachment> attachments=new ArrayList<>(); }
    private static class DownloadCollector { final int requested; int seen; Download result; DownloadCollector(int requested){this.requested=requested;} }
}
