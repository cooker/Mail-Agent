package io.mailagent;
import org.springframework.data.jpa.repository.*;
public interface MailRepository extends JpaRepository<ReceivedMail, Long>, JpaSpecificationExecutor<ReceivedMail> {
    boolean existsByAccountIdAndFolderNameAndUidValidityAndImapUid(Long accountId, String folderName, long uidValidity, long imapUid);
}
