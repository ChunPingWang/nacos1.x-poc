package com.example.nacos.discovery;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.example.nacos.NacosClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Interactive demo showing Nacos service discovery features.
 * Run with: docker-compose up -d, then execute this main class.
 *
 * Demonstrates: register, discover, subscribe, select, deregister
 */
public class ServiceDiscoveryDemo {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryDemo.class);
    private static final String SERVER_ADDR = "127.0.0.1:8848";

    public static void main(String[] args) throws Exception {
        NacosClientFactory factory = new NacosClientFactory(SERVER_ADDR);
        NamingService namingService = factory.createNamingService();

        String serviceName = "demo-service";

        // 1. Subscribe to service changes
        log.info("=== Step 1: Subscribe to Service ===");
        CountDownLatch latch = new CountDownLatch(1);
        EventListener listener = event -> {
            if (event instanceof NamingEvent) {
                NamingEvent ne = (NamingEvent) event;
                log.info("[Subscriber] Service changed! Instances: {}", ne.getInstances().size());
                for (Instance inst : ne.getInstances()) {
                    log.info("  - {}:{} (healthy={}, metadata={})",
                            inst.getIp(), inst.getPort(), inst.isHealthy(), inst.getMetadata());
                }
                latch.countDown();
            }
        };
        namingService.subscribe(serviceName, listener);
        log.info("Subscribed to '{}'", serviceName);

        // 2. Register instances
        log.info("=== Step 2: Register Instances ===");

        // Instance 1: simple
        namingService.registerInstance(serviceName, "192.168.1.10", 8080);
        log.info("Registered instance 1: 192.168.1.10:8080");

        // Instance 2: with metadata
        Instance instance2 = new Instance();
        instance2.setIp("192.168.1.11");
        instance2.setPort(8080);
        instance2.setWeight(2.0);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "v2.0");
        metadata.put("region", "us-west-2");
        instance2.setMetadata(metadata);
        namingService.registerInstance(serviceName, instance2);
        log.info("Registered instance 2: 192.168.1.11:8080 (weight=2.0, metadata)");

        // Wait for subscriber notification
        boolean notified = latch.await(10, TimeUnit.SECONDS);
        log.info("Subscriber notified: {}", notified);

        Thread.sleep(2000);

        // 3. Discover services
        log.info("=== Step 3: Discover All Instances ===");
        List<Instance> allInstances = namingService.getAllInstances(serviceName);
        log.info("Total instances: {}", allInstances.size());
        for (Instance inst : allInstances) {
            log.info("  - {}:{} weight={} healthy={} metadata={}",
                    inst.getIp(), inst.getPort(), inst.getWeight(),
                    inst.isHealthy(), inst.getMetadata());
        }

        // 4. Get healthy instances only
        log.info("=== Step 4: Get Healthy Instances ===");
        List<Instance> healthy = namingService.selectInstances(serviceName, true);
        log.info("Healthy instances: {}", healthy.size());

        // 5. Select one (load balancing)
        log.info("=== Step 5: Select One Healthy Instance (Load Balancing) ===");
        for (int i = 0; i < 5; i++) {
            Instance selected = namingService.selectOneHealthyInstance(serviceName);
            log.info("  Selected: {}:{} (weight={})", selected.getIp(), selected.getPort(), selected.getWeight());
        }

        // 6. Deregister
        log.info("=== Step 6: Deregister Instances ===");
        namingService.deregisterInstance(serviceName, "192.168.1.10", 8080);
        log.info("Deregistered 192.168.1.10:8080");

        Thread.sleep(2000);

        List<Instance> remaining = namingService.getAllInstances(serviceName);
        log.info("Remaining instances: {}", remaining.size());

        // Cleanup
        namingService.unsubscribe(serviceName, listener);
        namingService.deregisterInstance(serviceName, instance2);

        log.info("=== Service Discovery Demo Complete ===");
        namingService.shutDown();
    }
}
