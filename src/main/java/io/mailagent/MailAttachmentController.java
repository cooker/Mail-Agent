package io.mailagent;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController
public class MailAttachmentController {
    private final MailRepository mails;
    private final MailContentService contents;
    private final ShareService shares;

    public MailAttachmentController(MailRepository mails,MailContentService contents,ShareService shares) {
        this.mails=mails; this.contents=contents; this.shares=shares;
    }

    @GetMapping("/records/{mailId}/attachments/{index}")
    public ResponseEntity<byte[]> admin(@PathVariable Long mailId,@PathVariable int index) {
        ReceivedMail mail=mails.findById(mailId).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
        return response(contents.download(mail.payloadName,index));
    }

    @GetMapping("/s/{token}/mail/{mailId}/attachments/{index}")
    public ResponseEntity<byte[]> shared(@PathVariable String token,@PathVariable Long mailId,@PathVariable int index) {
        SharePage share=shares.requireActive(token);
        ReceivedMail mail=shares.requireMail(share,mailId);
        return response(contents.download(mail.payloadName,index));
    }

    private ResponseEntity<byte[]> response(MailContentService.Download value) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(value.name(),StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore()).body(value.bytes());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void missingAttachment() {}
}
