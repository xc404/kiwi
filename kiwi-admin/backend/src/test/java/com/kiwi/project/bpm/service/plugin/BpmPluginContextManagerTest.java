package com.kiwi.project.bpm.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.service.PluginBpmComponentProvider;
import com.kiwi.project.bpm.utils.BpmComponentBundleReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件子上下文加载、桥接与 reload 生命周期（含 Payment 多 Bean DI）。
 */
class BpmPluginContextManagerTest {

    @TempDir Path tempDir;

    private AnnotationConfigApplicationContext hostContext;
    private BpmPluginContextManager contextManager;
    private Path paymentJar;
    private final List<BpmPluginContext> openContexts = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        hostContext = new AnnotationConfigApplicationContext(TestHostConfiguration.class);
        contextManager = hostContext.getBean(BpmPluginContextManager.class);
        paymentJar = resolvePaymentPluginJar();
    }

    @AfterEach
    void tearDown() {
        for (BpmPluginContext ctx : openContexts) {
            contextManager.unregisterBridges(ctx);
            ctx.close();
        }
        openContexts.clear();
        hostContext.close();
    }

    private BpmPluginContextManager.LoadResult loadAndTrack(Path jar) {
        BpmPluginContextManager.LoadResult result = contextManager.load(jar);
        openContexts.add(result.pluginContext());
        return result;
    }

    @Test
    void loadPaymentPlugin_bridgesDelegatesAndWiresInternalBeans() {
        BpmPluginContextManager.LoadResult result = loadAndTrack(paymentJar);

        assertTrue(hostContext.containsBean("paymentCreate"));
        assertTrue(hostContext.containsBean("paymentQuery"));
        assertFalse(hostContext.containsBean("paymentChannelRouter"));

        JavaDelegate paymentCreate = hostContext.getBean("paymentCreate", JavaDelegate.class);
        assertInstanceOf(JavaDelegate.class, paymentCreate);

        List<BpmComponent> components = result.components();
        assertTrue(components.stream().anyMatch(c -> "plugin_paymentCreate".equals(c.getId())));
        assertTrue(components.stream().anyMatch(c -> "plugin_paymentQuery".equals(c.getId())));

        var childCtx = result.pluginContext().getApplicationContext();
        assertTrue(childCtx.containsBean("paymentChannelRouter"));
        assertTrue(childCtx.containsBean("paymentCreate"));
    }

    @Test
    void reload_closesChildContextAndReplacesBridge() throws Exception {
        BpmPluginContextManager.LoadResult first = loadAndTrack(paymentJar);
        JavaDelegate firstDelegate = hostContext.getBean("paymentCreate", JavaDelegate.class);

        contextManager.unregisterBridges(first.pluginContext());
        first.pluginContext().close();
        openContexts.remove(first.pluginContext());

        BpmPluginContextManager.LoadResult second = loadAndTrack(paymentJar);
        JavaDelegate secondDelegate = hostContext.getBean("paymentCreate", JavaDelegate.class);

        assertNotSame(firstDelegate, secondDelegate);
        assertTrue(hostContext.containsBean("paymentCreate"));
        assertTrue(second.pluginContext().getApplicationContext().isActive());
    }

    @Test
    void uploadAndReloadViaPluginsDir_simulatesLoaderLifecycle() throws Exception {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Path installed = pluginsDir.resolve(paymentJar.getFileName());
        Files.copy(paymentJar, installed, StandardCopyOption.REPLACE_EXISTING);

        BpmPluginContextManager.LoadResult loaded = loadAndTrack(installed);
        assertTrue(hostContext.getBean("paymentCreate", JavaDelegate.class) != null);

        contextManager.unregisterBridges(loaded.pluginContext());
        loaded.pluginContext().close();
        openContexts.remove(loaded.pluginContext());

        BpmPluginContextManager.LoadResult reloaded = loadAndTrack(installed);
        assertTrue(reloaded.pluginContext().getApplicationContext().containsBean("paymentCreate"));
        assertTrue(hostContext.containsBean("paymentCreate"));
    }

    private Path resolvePaymentPluginJar() {
        String jarName = "kiwi-bpmn-component-payment-1.0.0-SNAPSHOT-plugin.jar";
        List<Path> candidates =
                List.of(
                        Path.of("")
                                .toAbsolutePath()
                                .resolve("../../kiwi-bpmn/kiwi-bpmn-component-payment/target/" + jarName)
                                .normalize(),
                        Path.of("")
                                .toAbsolutePath()
                                .resolve("../../../kiwi-bpmn/kiwi-bpmn-component-payment/target/" + jarName)
                                .normalize(),
                        Path.of("")
                                .toAbsolutePath()
                                .resolve("kiwi-bpmn/kiwi-bpmn-component-payment/target/" + jarName)
                                .normalize(),
                        Path.of("")
                                .toAbsolutePath()
                                .resolve("kiwi-admin/backend/plugins/" + jarName)
                                .normalize());
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "请先构建 payment 插件: mvn -pl kiwi-bpmn/kiwi-bpmn-component-payment -am package -Pbuild-plugins -DskipTests");
    }

    @Configuration
    static class TestHostConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PluginBpmComponentProvider pluginBpmComponentProvider() {
            return new PluginBpmComponentProvider();
        }

        @Bean
        BpmComponentBundleReader bpmComponentBundleReader(
                ObjectMapper objectMapper,
                AnnotationConfigApplicationContext context,
                PluginBpmComponentProvider pluginBpmComponentProvider) {
            return new BpmComponentBundleReader(objectMapper, context, pluginBpmComponentProvider);
        }

        @Bean
        BpmPluginContextManager bpmPluginContextManager(
                AnnotationConfigApplicationContext context,
                BpmComponentBundleReader bundleReader,
                PluginBpmComponentProvider pluginBpmComponentProvider) {
            return new BpmPluginContextManager(context, bundleReader, pluginBpmComponentProvider);
        }
    }
}
