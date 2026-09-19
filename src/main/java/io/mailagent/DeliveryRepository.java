package io.mailagent;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {
    List<Delivery> findByMailIdOrderById(Long mailId);
    List<Delivery> findByStatus(DeliveryStatus status);
    long countByStatus(DeliveryStatus status);
    @Query("select d from Delivery d where d.status in :statuses and d.nextAttempt <= :now and exists (select a.id from Account a where a.id = d.accountId and a.enabled = true) order by d.nextAttempt, d.id")
    List<Delivery> findDue(Collection<DeliveryStatus> statuses, Instant now, Pageable pageable);
}
