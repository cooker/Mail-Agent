package io.mailagent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
    List<DeliveryAttempt> findByDeliveryIdOrderByIdDesc(Long deliveryId);
}
