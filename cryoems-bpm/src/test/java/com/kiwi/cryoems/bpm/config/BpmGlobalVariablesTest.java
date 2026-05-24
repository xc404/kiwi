package com.kiwi.cryoems.bpm.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link BpmGlobalVariables} 在「组件扫描 + ConfigurationPropertiesBindingPostProcessor」路径下的装配：
 * <ul>
 *   <li>Bean 以名为 {@code bpmGlobalVariables} 注册（BPMN EL 引用前提）；</li>
 *   <li>kebab-case yml 键正确绑定到 camelCase 字段；</li>
 *   <li>未配置任何属性时使用安全默认值，不阻塞启动。</li>
 * </ul>
 */
class BpmGlobalVariablesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultsAreSafeWhenNoPropertyConfigured() {
        runner.run(context -> {
            BpmGlobalVariables vars = context.getBean(BpmGlobalVariables.class);
            assertThat(vars.isMdocAutoFilterEnabled()).isFalse();
            assertThat(vars.isManualRebuildEnabled()).isTrue();
        });
    }

    @Test
    void bindsKebabCasePropertiesToCamelCaseFields() {
        runner.withPropertyValues(
                        "kiwi.bpm.global.mdoc-auto-filter-enabled=true",
                        "kiwi.bpm.global.manual-rebuild-enabled=false")
                .run(context -> {
                    BpmGlobalVariables vars = context.getBean(BpmGlobalVariables.class);
                    assertThat(vars.isMdocAutoFilterEnabled()).isTrue();
                    assertThat(vars.isManualRebuildEnabled()).isFalse();
                });
    }

    /**
     * BPMN EL 通过 Bean 名查找：必须保证 Bean 名为 {@code bpmGlobalVariables}；
     * 否则在 {@code kiwi.bpm.juel-null-for-missing-variables=true} 下表达式会静默解析为 null。
     */
    @Test
    void beanIsRegisteredUnderExpectedNameForBpmnExpression() {
        runner.run(context -> {
            assertThat(context.containsBean("bpmGlobalVariables")).isTrue();
            assertThat(context.getBean("bpmGlobalVariables")).isInstanceOf(BpmGlobalVariables.class);
        });
    }

    @Configuration
    @ComponentScan(basePackageClasses = BpmGlobalVariables.class)
    @EnableConfigurationProperties
    static class TestConfig {
    }
}
