package com.example.nacos.auth;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.example.nacos.NacosClientFactory;
import com.example.nacos.NacosTestBase;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Nacos authentication (using a separate auth-enabled container).
 */
public class AuthenticationTest extends NacosTestBase {

    private static String authServerAddr;

    @BeforeClass
    public static void setUp() {
        authServerAddr = getAuthServerAddr();
    }

    @Test
    public void testAuthenticatedAccess() throws Exception {
        NacosClientFactory factory = new NacosClientFactory(authServerAddr);
        ConfigService configService = factory.createConfigService(null, "nacos", "nacos");

        String dataId = "auth-config-" + uniqueId();
        String group = "DEFAULT_GROUP";

        boolean published = configService.publishConfig(dataId, group, "secure=data");
        assertThat(published).isTrue();

        Thread.sleep(1000);

        String result = configService.getConfig(dataId, group, 5000);
        assertThat(result).isEqualTo("secure=data");
    }

    @Test
    public void testUnauthenticatedAccessDenied() throws Exception {
        NacosClientFactory factory = new NacosClientFactory(authServerAddr);

        ConfigService configService = factory.createConfigService();

        final String dataId = "unauth-config-" + uniqueId();
        final String group = "DEFAULT_GROUP";

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                configService.publishConfig(dataId, group, "should=fail");
            }
        }).isInstanceOf(NacosException.class);
    }
}
