package io.mailagent;
import org.springframework.data.jpa.repository.*;
public interface MailRepository extends JpaRepository<ReceivedMail, Long>, JpaSpecificationExecutor<ReceivedMail> {
    java.util.Optional<ReceivedMail> findByIdAndDeletedFalse(Long id);
    long countByDeletedFalse();
    org.springframework.data.domain.Page<ReceivedMail> findByDeletedFalse(org.springframework.data.domain.Pageable pageable);
    boolean existsByAccountIdAndFolderNameAndUidValidityAndImapUid(Long accountId, String folderName, long uidValidity, long imapUid);
}
