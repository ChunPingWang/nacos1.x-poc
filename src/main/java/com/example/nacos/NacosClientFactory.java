package com.example.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Factory for creating Nacos ConfigService and NamingService instances.
 * Supports namespace isolation and authentication configuration.
 */
public class NacosClientFactory {

    private static final Logger log = LoggerFactory.getLogger(NacosClientFactory.class);

    private final String serverAddr;

    public NacosClientFactory(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    /**
     * Create a ConfigService with default namespace.
     */
    public ConfigService createConfigService() throws NacosException {
        return createConfigService(null, null, null);
    }

    /**
     * Create a ConfigService with a specific namespace.
     */
    public ConfigService createConfigService(String namespace) throws NacosException {
        return createConfigService(namespace, null, null);
    }

    /**
     * Create a ConfigService with namespace and authentication.
     */
    public ConfigService createConfigService(String namespace, String username, String password) throws NacosException {
        Properties properties = buildProperties(namespace, username, password);
        log.info("Creating ConfigService: serverAddr={}, namespace={}", serverAddr, namespace);
        return NacosFactory.createConfigService(properties);
    }

    /**
     * Create a NamingService with default namespace.
     */
    public NamingService createNamingService() throws NacosException {
        return createNamingService(null, null, null);
    }

    /**
     * Create a NamingService with a specific namespace.
     */
    public NamingService createNamingService(String namespace) throws NacosException {
        return createNamingService(namespace, null, null);
    }

    /**
     * Create a NamingService with namespace and authentication.
     */
    public NamingService createNamingService(String namespace, String username, String password) throws NacosException {
        Properties properties = buildProperties(namespace, username, password);
        log.info("Creating NamingService: serverAddr={}, namespace={}", serverAddr, namespace);
        return NacosFactory.createNamingService(properties);
    }

    private Properties buildProperties(String namespace, String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        if (namespace != null && !namespace.isEmpty()) {
            properties.setProperty("namespace", namespace);
        }
        if (username != null && password != null) {
            properties.setProperty("username", username);
            properties.setProperty("password", password);
        }
        return properties;
    }
}
