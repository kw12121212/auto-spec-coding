package org.specdriven.agent.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LealonePolicyStoreConcurrentTest {

    private LealonePolicyStore store;

    @BeforeEach
    void setUp() {
        String dbName = "test_perm_conc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        store = new LealonePolicyStore(jdbcUrl);
    }

    @Test
    void concurrentGrantAndFind_seesConsistentState() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger foundCount = new AtomicInteger(0);

        Permission perm = new Permission("execute", "/bin/bash", Map.of());
        PermissionContext ctx = new PermissionContext("bash-tool", "run", "agent-1");

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();

                    if (idx == 0) {
                        store.grant(perm, ctx);
                    } else {
                        // Spin briefly to give the grant a chance
                        for (int attempt = 0; attempt < 50; attempt++) {
                            if (store.find(perm, ctx).isPresent()) {
                                foundCount.incrementAndGet();
                                return;
                            }
                            Thread.sleep(10);
                        }
                    }
                } catch (Exception e) {
                    fail("Thread threw exception: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // At least some readers should see the granted policy
        assertTrue(foundCount.get() > 0, "Expected at least one reader to see the granted policy, but got " + foundCount.get());
    }
}
