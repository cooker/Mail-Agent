package io.mailagent;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
@Component
public class AccountLocks {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    public ReentrantLock forAccount(Long id) { return locks.computeIfAbsent(id, ignored -> new ReentrantLock()); }
}
