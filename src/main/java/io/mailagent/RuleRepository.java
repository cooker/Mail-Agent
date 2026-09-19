package io.mailagent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RuleRepository extends JpaRepository<ForwardRule, Long> {
    List<ForwardRule> findByEnabledTrueOrderById();
}
