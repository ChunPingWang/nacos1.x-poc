package com.example.nacos.discovery;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.example.nacos.NacosTestBase;
import org.awaitility.core.ThrowingRunnable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for Nacos health check and heartbeat mechanisms.
 * These tests have longer timeouts as they depend on heartbeat intervals.
 */
public class HealthCheckTest extends NacosTestBase {

    private static NamingService namingService;

    @BeforeClass
    public static void setUp() throws Exception {
        namingService = createFactory().createNamingService();
    }

    @Test
    public void testInstanceBecomeUnhealthy() throws Exception {
        final String serviceName = "svc-unhealthy-" + uniqueId();

        NamingService tempNaming = createFactory().createNamingService();
        tempNaming.registerInstance(serviceName, "10.5.0.1", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).hasSize(1);
                assertThat(instances.get(0).isHealthy()).isTrue();
            }
        });

        tempNaming.shutDown();

        // After heartbeat stops, instance becomes unhealthy (~15s) then deregistered (~30s).
        // We accept either state as proof that health check works.
        await().atMost(60, SECONDS)
                .pollInterval(1, SECONDS)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() throws Exception {
                        List<Instance> instances = namingService.getAllInstances(serviceName);
                        boolean unhealthyOrRemoved = instances.isEmpty()
                                || !instances.get(0).isHealthy();
                        assertThat(unhealthyOrRemoved)
                                .as("Instance should become unhealthy or be removed")
                                .isTrue();
                    }
                });
    }

    @Test
    public void testEphemeralInstanceAutoDeregister() throws Exception {
        final String serviceName = "svc-auto-dereg-" + uniqueId();

        NamingService tempNaming = createFactory().createNamingService();
        tempNaming.registerInstance(serviceName, "10.6.0.1", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).hasSize(1);
            }
        });

        tempNaming.shutDown();

        await().atMost(60, SECONDS)
                .pollInterval(3, SECONDS)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() throws Exception {
                        List<Instance> instances = namingService.getAllInstances(serviceName);
                        assertThat(instances).isEmpty();
                    }
                });
    }
}
