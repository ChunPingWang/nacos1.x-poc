package com.example.nacos.namespace;

import com.alibaba.nacos.api.config.ConfigService;
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
 * Tests for namespace and group isolation in Nacos.
 */
public class NamespaceGroupTest extends NacosTestBase {

    private static final String NS_A = "ns-a-" + System.currentTimeMillis();
    private static final String NS_B = "ns-b-" + System.currentTimeMillis();

    @BeforeClass
    public static void setUp() throws Exception {
        createNamespace(NS_A, "Namespace A");
        createNamespace(NS_B, "Namespace B");
        Thread.sleep(2000);
    }

    @Test
    public void testConfigIsolationByNamespace() throws Exception {
        String dataId = "ns-config-" + uniqueId();
        String group = "DEFAULT_GROUP";

        ConfigService configA = createFactory().createConfigService(NS_A);
        ConfigService configB = createFactory().createConfigService(NS_B);

        configA.publishConfig(dataId, group, "namespace=A");
        configB.publishConfig(dataId, group, "namespace=B");
        Thread.sleep(1000);

        String resultA = configA.getConfig(dataId, group, 5000);
        String resultB = configB.getConfig(dataId, group, 5000);

        assertThat(resultA).isEqualTo("namespace=A");
        assertThat(resultB).isEqualTo("namespace=B");

        ConfigService configDefault = createFactory().createConfigService();
        String resultDefault = configDefault.getConfig(dataId, group, 5000);
        assertThat(resultDefault).isNull();
    }

    @Test
    public void testServiceIsolationByNamespace() throws Exception {
        final String serviceName = "ns-svc-" + uniqueId();

        final NamingService namingA = createFactory().createNamingService(NS_A);
        final NamingService namingB = createFactory().createNamingService(NS_B);

        namingA.registerInstance(serviceName, "10.10.0.1", 8080);
        namingB.registerInstance(serviceName, "10.20.0.1", 9090);

        await().atMost(10, SECONDS).untilAsserted(new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                List<Instance> instancesA = namingA.getAllInstances(serviceName);
                assertThat(instancesA).hasSize(1);
                assertThat(instancesA.get(0).getIp()).isEqualTo("10.10.0.1");

                List<Instance> instancesB = namingB.getAllInstances(serviceName);
                assertThat(instancesB).hasSize(1);
                assertThat(instancesB.get(0).getIp()).isEqualTo("10.20.0.1");
            }
        });

        NamingService namingDefault = createFactory().createNamingService();
        List<Instance> defaultInstances = namingDefault.getAllInstances(serviceName);
        assertThat(defaultInstances).isEmpty();
    }

    @Test
    public void testConfigIsolationByGroup() throws Exception {
        String dataId = "group-config-" + uniqueId();
        String groupDev = "DEV_GROUP";
        String groupProd = "PROD_GROUP";

        ConfigService configService = createFactory().createConfigService();

        configService.publishConfig(dataId, groupDev, "env=development");
        configService.publishConfig(dataId, groupProd, "env=production");
        Thread.sleep(1000);

        String devResult = configService.getConfig(dataId, groupDev, 5000);
        String prodResult = configService.getConfig(dataId, groupProd, 5000);

        assertThat(devResult).isEqualTo("env=development");
        assertThat(prodResult).isEqualTo("env=production");

        String defaultResult = configService.getConfig(dataId, "DEFAULT_GROUP", 5000);
        assertThat(defaultResult).isNull();
    }
}
