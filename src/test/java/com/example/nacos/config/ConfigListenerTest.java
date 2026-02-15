package com.example.nacos.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.example.nacos.NacosTestBase;
import org.awaitility.core.ThrowingRunnable;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for Nacos config change listeners.
 * Uses Awaitility to handle async notifications reliably.
 */
public class ConfigListenerTest extends NacosTestBase {

    private static ConfigService configService;

    @BeforeClass
    public static void setUp() throws Exception {
        configService = createFactory().createConfigService();
    }

    @Test
    public void testConfigChangeListener() throws Exception {
        String dataId = "listener-basic-" + uniqueId();
        String group = "DEFAULT_GROUP";

        configService.publishConfig(dataId, group, "initial=value");
        Thread.sleep(1000);

        final AtomicReference<String> received = new AtomicReference<String>();
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                received.set(configInfo);
            }
        };
        configService.addListener(dataId, group, listener);

        configService.publishConfig(dataId, group, "updated=value");

        await().atMost(10, SECONDS)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertThat(received.get()).isEqualTo("updated=value");
                    }
                });
    }

    @Test
    public void testMultipleListeners() throws Exception {
        String dataId = "listener-multi-" + uniqueId();
        String group = "DEFAULT_GROUP";

        configService.publishConfig(dataId, group, "v0");
        Thread.sleep(1000);

        final AtomicReference<String> received1 = new AtomicReference<String>();
        final AtomicReference<String> received2 = new AtomicReference<String>();

        Listener listener1 = createListener(received1);
        Listener listener2 = createListener(received2);

        configService.addListener(dataId, group, listener1);
        configService.addListener(dataId, group, listener2);

        Thread.sleep(1000);
        configService.publishConfig(dataId, group, "v1");

        await().atMost(30, SECONDS)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertThat(received1.get()).isEqualTo("v1");
                        assertThat(received2.get()).isEqualTo("v1");
                    }
                });
    }

    @Test
    public void testRemoveListener() throws Exception {
        String dataId = "listener-remove-" + uniqueId();
        String group = "DEFAULT_GROUP";

        configService.publishConfig(dataId, group, "initial");
        Thread.sleep(1000);

        final AtomicReference<String> received = new AtomicReference<String>();
        Listener listener = createListener(received);
        configService.addListener(dataId, group, listener);

        configService.publishConfig(dataId, group, "update1");
        await().atMost(10, SECONDS)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertThat(received.get()).isEqualTo("update1");
                    }
                });

        configService.removeListener(dataId, group, listener);
        received.set(null);

        configService.publishConfig(dataId, group, "update2");

        Thread.sleep(5000);
        assertThat(received.get()).isNull();
    }

    private Listener createListener(final AtomicReference<String> holder) {
        return new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                holder.set(configInfo);
            }
        };
    }
}
