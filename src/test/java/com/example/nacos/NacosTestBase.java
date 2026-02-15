package com.example.nacos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Base class for Nacos integration tests.
 *
 * Supports two modes:
 * 1. External Nacos: Set env NACOS_SERVER_ADDR (e.g. "127.0.0.1:18848") and
 *    optionally NACOS_AUTH_SERVER_ADDR to skip Testcontainers.
 * 2. Testcontainers: If no env is set, automatically starts Nacos containers.
 */
public abstract class NacosTestBase {

    private static final Logger log = LoggerFactory.getLogger(NacosTestBase.class);

    private static final int NACOS_PORT = 8848;
    private static final String NACOS_IMAGE = "nacos/nacos-server:v1.4.6";

    // External mode addresses (null if using Testcontainers)
    private static final String EXTERNAL_SERVER_ADDR = System.getenv("NACOS_SERVER_ADDR");
    private static final String EXTERNAL_AUTH_SERVER_ADDR = System.getenv("NACOS_AUTH_SERVER_ADDR");

    private static final boolean USE_EXTERNAL = EXTERNAL_SERVER_ADDR != null && !EXTERNAL_SERVER_ADDR.isEmpty();

    // Testcontainers (only initialized if not using external)
    protected static final GenericContainer<?> NACOS_CONTAINER;

    private static GenericContainer<?> authContainer;
    private static final Object AUTH_LOCK = new Object();

    static {
        if (USE_EXTERNAL) {
            NACOS_CONTAINER = null;
            log.info("Using external Nacos: {}", EXTERNAL_SERVER_ADDR);
            if (EXTERNAL_AUTH_SERVER_ADDR != null) {
                log.info("Using external Nacos auth: {}", EXTERNAL_AUTH_SERVER_ADDR);
            }
        } else {
            log.info("Starting Nacos via Testcontainers...");
            NACOS_CONTAINER = new GenericContainer<>(NACOS_IMAGE)
                    .withExposedPorts(NACOS_PORT)
                    .withEnv("MODE", "standalone")
                    .withEnv("PREFER_HOST_MODE", "hostname")
                    .withEnv("JVM_XMS", "256m")
                    .withEnv("JVM_XMX", "256m")
                    .waitingFor(new HttpWaitStrategy()
                            .forPath("/nacos/v1/console/health/readiness")
                            .forPort(NACOS_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(600)))
                    .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("nacos"));
            NACOS_CONTAINER.start();
            log.info("Nacos container started: {}:{}", NACOS_CONTAINER.getHost(),
                    NACOS_CONTAINER.getMappedPort(NACOS_PORT));
        }
    }

    protected static String getServerAddr() {
        if (USE_EXTERNAL) {
            return EXTERNAL_SERVER_ADDR;
        }
        return NACOS_CONTAINER.getHost() + ":" + NACOS_CONTAINER.getMappedPort(NACOS_PORT);
    }

    protected static NacosClientFactory createFactory() {
        return new NacosClientFactory(getServerAddr());
    }

    protected static GenericContainer<?> getAuthContainer() {
        if (USE_EXTERNAL) {
            return null;
        }
        synchronized (AUTH_LOCK) {
            if (authContainer == null) {
                authContainer = new GenericContainer<>(NACOS_IMAGE)
                        .withExposedPorts(NACOS_PORT)
                        .withEnv("MODE", "standalone")
                        .withEnv("PREFER_HOST_MODE", "hostname")
                        .withEnv("JVM_XMS", "256m")
                        .withEnv("JVM_XMX", "256m")
                        .withEnv("NACOS_AUTH_ENABLE", "true")
                        .withEnv("NACOS_AUTH_IDENTITY_KEY", "serverIdentity")
                        .withEnv("NACOS_AUTH_IDENTITY_VALUE", "security")
                        .withEnv("NACOS_AUTH_TOKEN", "SecretKey012345678901234567890123456789012345678901234567890123456789")
                        .waitingFor(new HttpWaitStrategy()
                                .forPath("/nacos/v1/console/health/readiness")
                                .forPort(NACOS_PORT)
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.ofSeconds(600)))
                        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("nacos-auth"));
                authContainer.start();
                log.info("Nacos auth container started: {}:{}", authContainer.getHost(),
                        authContainer.getMappedPort(NACOS_PORT));
            }
            return authContainer;
        }
    }

    protected static String getAuthServerAddr() {
        if (USE_EXTERNAL && EXTERNAL_AUTH_SERVER_ADDR != null) {
            return EXTERNAL_AUTH_SERVER_ADDR;
        }
        GenericContainer<?> container = getAuthContainer();
        return container.getHost() + ":" + container.getMappedPort(NACOS_PORT);
    }

    protected static String uniqueId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String createNamespace(String namespaceId, String namespaceName) throws Exception {
        String urlStr = String.format("http://%s/nacos/v1/console/namespaces", getServerAddr());
        String body = String.format("customNamespaceId=%s&namespaceName=%s&namespaceDesc=%s",
                namespaceId, namespaceName, namespaceName);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            log.info("Create namespace '{}': status={}, body={}", namespaceId, statusCode, response);
        } finally {
            conn.disconnect();
        }
        return namespaceId;
    }
}
