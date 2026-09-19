package io.mailagent;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.*;

@Controller
public class ShareAdminController {
    private final SharePageRepository shares;
    private final AccountRepository accounts;
    private final ShareService service;

    public ShareAdminController(SharePageRepository shares,AccountRepository accounts,ShareService service) {
        this.shares=shares; this.accounts=accounts; this.service=service;
    }
    @InitBinder public void bind(WebDataBinder binder) { binder.initDirectFieldAccess(); }

    @GetMapping("/shares")
    public String list(Model model) {
        var pages=shares.findAll(Sort.by("createdAt").descending());
        Map<Long,String> shareUrls=new HashMap<>();
        pages.forEach(page->service.token(page).ifPresent(token->shareUrls.put(page.id,url(token))));
        model.addAttribute("shares",pages); model.addAttribute("shareUrls",shareUrls);
        model.addAttribute("allAccounts",accounts.findAll(Sort.by("id")));
        model.addAttribute("form",new ShareForm());
        return "shares";
    }

    @PostMapping("/shares")
    public String create(@ModelAttribute ShareForm form,RedirectAttributes flash) {
        ShareService.Created created=service.create(form);
        flash.addFlashAttribute("notice","分享页面已创建，可随时在已有分享中查看链接。");
        flash.addFlashAttribute("shareUrl",url(created.token()));
        return "redirect:/shares";
    }

    @GetMapping("/shares/{id}/edit")
    public String edit(@PathVariable Long id,Model model) {
        SharePage page=service.find(id); model.addAttribute("share",page); model.addAttribute("form",ShareForm.from(page));
        model.addAttribute("allAccounts",accounts.findAll(Sort.by("id"))); return "share-edit";
    }

    @PostMapping("/shares/{id}/edit")
    public String update(@PathVariable Long id,@ModelAttribute ShareForm form,RedirectAttributes flash) {
        service.update(id,form); flash.addFlashAttribute("notice","分享设置已更新，原链接继续有效。"); return "redirect:/shares";
    }

    @PostMapping("/shares/{id}/toggle")
    public String toggle(@PathVariable Long id) { service.toggle(id); return "redirect:/shares"; }

    @PostMapping("/shares/{id}/delete")
    public String delete(@PathVariable Long id,RedirectAttributes flash) {
        service.delete(id); flash.addFlashAttribute("notice","分享页面已删除，原链接已失效。"); return "redirect:/shares";
    }

    @PostMapping("/shares/{id}/regenerate")
    public String regenerate(@PathVariable Long id,RedirectAttributes flash) {
        service.regenerate(id); flash.addFlashAttribute("notice","分享链接已重新生成，原链接已失效。"); return "redirect:/shares";
    }

    private String url(String token) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/s/").path(token).toUriString();
    }
}
