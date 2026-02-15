package com.example.nacos.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.example.nacos.NacosClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Interactive demo showing Nacos configuration management features.
 * Run with: docker-compose up -d, then execute this main class.
 *
 * Demonstrates: publish, get, listen, update, remove
 */
public class ConfigDemo {

    private static final Logger log = LoggerFactory.getLogger(ConfigDemo.class);
    private static final String SERVER_ADDR = "127.0.0.1:8848";

    public static void main(String[] args) throws Exception {
        NacosClientFactory factory = new NacosClientFactory(SERVER_ADDR);
        ConfigService configService = factory.createConfigService();

        String dataId = "demo.properties";
        String group = "DEFAULT_GROUP";

        // 1. Publish config
        log.info("=== Step 1: Publish Config ===");
        String content = "app.name=nacos-demo\napp.version=1.0.0\napp.env=development";
        boolean published = configService.publishConfig(dataId, group, content);
        log.info("Published: {}", published);

        Thread.sleep(1000);

        // 2. Get config
        log.info("=== Step 2: Get Config ===");
        String config = configService.getConfig(dataId, group, 5000);
        log.info("Config content:\n{}", config);

        // 3. Add listener
        log.info("=== Step 3: Add Config Listener ===");
        CountDownLatch latch = new CountDownLatch(1);
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("[Listener] Config changed! New content:\n{}", configInfo);
                latch.countDown();
            }
        };
        configService.addListener(dataId, group, listener);
        log.info("Listener registered");

        // 4. Update config (triggers listener)
        log.info("=== Step 4: Update Config ===");
        String updatedContent = "app.name=nacos-demo\napp.version=2.0.0\napp.env=production";
        configService.publishConfig(dataId, group, updatedContent);
        log.info("Config updated, waiting for listener notification...");

        boolean notified = latch.await(10, TimeUnit.SECONDS);
        log.info("Listener notified: {}", notified);

        // 5. Verify updated config
        log.info("=== Step 5: Verify Updated Config ===");
        String updated = configService.getConfig(dataId, group, 5000);
        log.info("Updated config:\n{}", updated);

        // 6. Remove config
        log.info("=== Step 6: Remove Config ===");
        boolean removed = configService.removeConfig(dataId, group);
        log.info("Removed: {}", removed);

        Thread.sleep(1000);

        String afterRemove = configService.getConfig(dataId, group, 5000);
        log.info("Config after remove: {}", afterRemove);

        log.info("=== Config Demo Complete ===");
        configService.shutDown();
    }
}
