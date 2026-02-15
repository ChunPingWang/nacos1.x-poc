package com.example.nacos.discovery;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.example.nacos.NacosTestBase;
import org.awaitility.core.ThrowingRunnable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for Nacos service registration and deregistration.
 */
public class ServiceRegistrationTest extends NacosTestBase {

    private static NamingService namingService;

    @BeforeClass
    public static void setUp() throws Exception {
        namingService = createFactory().createNamingService();
    }

    @Test
    public void testRegisterInstance() throws Exception {
        final String serviceName = "svc-register-" + uniqueId();

        namingService.registerInstance(serviceName, "192.168.1.1", 8080);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).hasSize(1);
                assertThat(instances.get(0).getIp()).isEqualTo("192.168.1.1");
                assertThat(instances.get(0).getPort()).isEqualTo(8080);
            }
        });
    }

    @Test
    public void testDeregisterInstance() throws Exception {
        final String serviceName = "svc-deregister-" + uniqueId();

        namingService.registerInstance(serviceName, "192.168.1.2", 8080);
        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).hasSize(1);
            }
        });

        namingService.deregisterInstance(serviceName, "192.168.1.2", 8080);

        await().atMost(15, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).isEmpty();
            }
        });
    }

    @Test
    public void testRegisterMultipleInstances() throws Exception {
        final String serviceName = "svc-multi-" + uniqueId();

        namingService.registerInstance(serviceName, "10.0.0.1", 8080);
        namingService.registerInstance(serviceName, "10.0.0.2", 8080);
        namingService.registerInstance(serviceName, "10.0.0.3", 8081);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).hasSize(3);
            }
        });
    }

    @Test
    public void testRegisterWithMetadata() throws Exception {
        final String serviceName = "svc-metadata-" + uniqueId();

        Instance instance = new Instance();
        instance.setIp("172.16.0.1");
        instance.setPort(9090);
        instance.setWeight(2.0);

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("version", "v1.0");
        metadata.put("region", "us-east-1");
        metadata.put("env", "staging");
        instance.setMetadata(metadata);

        namingService.registerInstance(serviceName, instance);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instances = namingService.getAllInstances(serviceName);
                assertThat(instances).hasSize(1);

                Instance registered = instances.get(0);
                assertThat(registered.getIp()).isEqualTo("172.16.0.1");
                assertThat(registered.getPort()).isEqualTo(9090);
                assertThat(registered.getWeight()).isEqualTo(2.0);
                assertThat(registered.getMetadata())
                        .containsEntry("version", "v1.0")
                        .containsEntry("region", "us-east-1")
                        .containsEntry("env", "staging");
            }
        });
    }
}
