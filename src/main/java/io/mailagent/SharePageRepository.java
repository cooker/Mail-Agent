package io.mailagent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SharePageRepository extends JpaRepository<SharePage,Long> {
    Optional<SharePage> findByTokenHashAndEnabledTrue(String tokenHash);
}
