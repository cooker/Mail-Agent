package io.mailagent;

import java.time.LocalDate;
import java.time.ZoneId;

public class ShareForm {
    public String title="";
    public Long accountId;
    public String subjectKeyword="";
    public String recipientKeyword="";
    public LocalDate expiresOn;

    public String getTitle() { return title; }
    public Long getAccountId() { return accountId; }
    public String getSubjectKeyword() { return subjectKeyword; }
    public String getRecipientKeyword() { return recipientKeyword; }
    public LocalDate getExpiresOn() { return expiresOn; }

    public static ShareForm from(SharePage page) {
        ShareForm form=new ShareForm(); form.title=page.title; form.accountId=page.accountId;
        form.subjectKeyword=page.subjectKeyword; form.recipientKeyword=page.recipientKeyword;
        form.expiresOn=page.expiresAt==null ? null : page.expiresAt.atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1);
        return form;
    }
}
