package io.mailagent;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PublicShareController {
    private final ShareService shares;
    private final MailContentService contents;

    public PublicShareController(ShareService shares,MailContentService contents) { this.shares=shares; this.contents=contents; }

    @GetMapping("/s/{token}")
    public String list(@PathVariable String token,@RequestParam(defaultValue="0") int page,Model model) {
        SharePage share=shares.requireActive(token);
        model.addAttribute("share",share); model.addAttribute("token",token); model.addAttribute("results",shares.mails(share,page));
        return "share-public";
    }

    @GetMapping("/s/{token}/mail/{id}")
    public String mail(@PathVariable String token,@PathVariable Long id,Model model) {
        SharePage share=shares.requireActive(token);
        ReceivedMail mail=shares.requireMail(share,id);
        model.addAttribute("share",share); model.addAttribute("token",token); model.addAttribute("mail",mail);
        model.addAttribute("content",contents.read(mail.payloadName)); return "share-mail";
    }
}
