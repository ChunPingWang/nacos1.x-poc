package com.example.nacos.config;

import com.alibaba.nacos.api.config.ConfigService;
import com.example.nacos.NacosTestBase;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for basic Nacos configuration CRUD operations.
 */
public class ConfigBasicTest extends NacosTestBase {

    private static ConfigService configService;

    @BeforeClass
    public static void setUp() throws Exception {
        configService = createFactory().createConfigService();
    }

    @Test
    public void testPublishAndGetConfig() throws Exception {
        String dataId = "config-basic-" + uniqueId();
        String group = "DEFAULT_GROUP";
        String content = "key=value\nenv=test";

        boolean published = configService.publishConfig(dataId, group, content);
        assertThat(published).isTrue();

        Thread.sleep(1000);

        String result = configService.getConfig(dataId, group, 5000);
        assertThat(result).isEqualTo(content);
    }

    @Test
    public void testUpdateConfig() throws Exception {
        String dataId = "config-update-" + uniqueId();
        String group = "DEFAULT_GROUP";

        configService.publishConfig(dataId, group, "version=1");
        Thread.sleep(1000);

        boolean updated = configService.publishConfig(dataId, group, "version=2");
        assertThat(updated).isTrue();

        Thread.sleep(1000);

        String result = configService.getConfig(dataId, group, 5000);
        assertThat(result).isEqualTo("version=2");
    }

    @Test
    public void testRemoveConfig() throws Exception {
        String dataId = "config-remove-" + uniqueId();
        String group = "DEFAULT_GROUP";

        configService.publishConfig(dataId, group, "temporary=data");
        Thread.sleep(1000);

        boolean removed = configService.removeConfig(dataId, group);
        assertThat(removed).isTrue();

        Thread.sleep(1000);

        String result = configService.getConfig(dataId, group, 5000);
        assertThat(result).isNull();
    }

    @Test
    public void testGetNonExistentConfig() throws Exception {
        String dataId = "non-existent-" + uniqueId();
        String group = "DEFAULT_GROUP";

        String result = configService.getConfig(dataId, group, 5000);
        assertThat(result).isNull();
    }

    @Test
    public void testPublishConfigWithGroup() throws Exception {
        String dataId = "config-group-" + uniqueId();
        String group1 = "GROUP_A";
        String group2 = "GROUP_B";

        configService.publishConfig(dataId, group1, "from=groupA");
        configService.publishConfig(dataId, group2, "from=groupB");
        Thread.sleep(1000);

        String resultA = configService.getConfig(dataId, group1, 5000);
        String resultB = configService.getConfig(dataId, group2, 5000);

        assertThat(resultA).isEqualTo("from=groupA");
        assertThat(resultB).isEqualTo("from=groupB");
    }
}
