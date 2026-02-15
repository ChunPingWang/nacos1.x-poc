package com.example.nacos.discovery;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.example.nacos.NacosTestBase;
import org.awaitility.core.ThrowingRunnable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for Nacos service discovery features.
 */
public class ServiceDiscoveryTest extends NacosTestBase {

    private static NamingService namingService;

    @BeforeClass
    public static void setUp() throws Exception {
        namingService = createFactory().createNamingService();
    }

    @Test
    public void testGetAllInstances() throws Exception {
        final String serviceName = "svc-all-" + uniqueId();

        namingService.registerInstance(serviceName, "10.1.0.1", 8080);
        namingService.registerInstance(serviceName, "10.1.0.2", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> all = namingService.getAllInstances(serviceName);
                assertThat(all).hasSize(2);
                assertThat(all).extracting(Instance::getIp)
                        .containsExactlyInAnyOrder("10.1.0.1", "10.1.0.2");
            }
        });
    }

    @Test
    public void testGetHealthyInstances() throws Exception {
        final String serviceName = "svc-healthy-" + uniqueId();

        namingService.registerInstance(serviceName, "10.2.0.1", 8080);
        namingService.registerInstance(serviceName, "10.2.0.2", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> healthy = namingService.selectInstances(serviceName, true);
                assertThat(healthy).hasSize(2);
                assertThat(healthy).allMatch(Instance::isHealthy);
            }
        });
    }

    @Test
    public void testSubscribeToService() throws Exception {
        final String serviceName = "svc-subscribe-" + uniqueId();

        final AtomicReference<List<Instance>> receivedInstances = new AtomicReference<List<Instance>>();

        EventListener listener = new EventListener() {
            @Override
            public void onEvent(com.alibaba.nacos.api.naming.listener.Event event) {
                if (event instanceof NamingEvent) {
                    NamingEvent namingEvent = (NamingEvent) event;
                    receivedInstances.set(namingEvent.getInstances());
                }
            }
        };
        namingService.subscribe(serviceName, listener);

        namingService.registerInstance(serviceName, "10.3.0.1", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() {
                List<Instance> instances = receivedInstances.get();
                assertThat(instances).isNotNull().isNotEmpty();
                assertThat(instances).anyMatch(i -> "10.3.0.1".equals(i.getIp()));
            }
        });

        namingService.unsubscribe(serviceName, listener);
    }

    @Test
    public void testSelectOneHealthyInstance() throws Exception {
        final String serviceName = "svc-select-" + uniqueId();

        namingService.registerInstance(serviceName, "10.4.0.1", 8080);
        namingService.registerInstance(serviceName, "10.4.0.2", 8080);
        namingService.registerInstance(serviceName, "10.4.0.3", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> all = namingService.getAllInstances(serviceName);
                assertThat(all).hasSize(3);
            }
        });

        Instance selected = namingService.selectOneHealthyInstance(serviceName);
        assertThat(selected).isNotNull();
        assertThat(selected.isHealthy()).isTrue();
        assertThat(selected.getIp()).isIn("10.4.0.1", "10.4.0.2", "10.4.0.3");
    }
}
