package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dto.BpmComponentPluginDescriptor;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.service.plugin.BpmPluginContext;
import com.kiwi.project.bpm.service.plugin.BpmPluginContextManager;
import com.kiwi.project.bpm.utils.BpmComponentBundleReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 {@code bpm.component.plugins-dir} 加载插件 JAR，经子 ApplicationContext 完成 DI 并桥接 delegate 至宿主。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BpmComponentPluginLoader implements InitializingBean {

    private final BpmPluginContextManager pluginContextManager;
    private final BpmComponentDeploymentService deploymentService;
    private final ObjectProvider<BpmComponentService> componentServiceProvider;
    private final PluginBpmComponentProvider pluginBpmComponentProvider;
    private final BpmComponentBundleReader bundleReader;

    @Value("${bpm.component.plugins-dir:plugins}")
    private String pluginsDir;

    @Value("${bpm.component.plugins-enabled:true}")
    private boolean pluginsEnabled;

    private final Map<String, BpmPluginContext> pluginContexts = new ConcurrentHashMap<>();
    private final Set<String> pluginRegisteredBeans = ConcurrentHashMap.newKeySet();
    private volatile List<BpmComponentPluginDescriptor> pluginDescriptors = List.of();

    public synchronized void reload() {
        if (!pluginsEnabled) {
            log.info("BPM 组件插件加载已禁用 (bpm.component.plugins-enabled=false)");
            closeAllPluginContexts();
            pluginBpmComponentProvider.setComponents(List.of());
            pluginDescriptors = List.of();
            return;
        }
        Path dir = Path.of(pluginsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建插件目录: " + dir, e);
        }

        closeAllPluginContexts();
        List<BpmComponent> discovered = new ArrayList<>();
        List<BpmComponentPluginDescriptor> descriptors = new ArrayList<>();

        try (var stream = Files.list(dir)) {
            List<Path> jars =
                    stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .toList();
            for (Path jar : jars) {
                discovered.addAll(loadJar(jar));
                BpmComponentPluginDescriptor descriptor = bundleReader.describeJar(jar);
                descriptors.add(descriptor);
                logPluginDescriptor(jar, descriptor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描插件目录失败: " + dir, e);
        }

        pluginBpmComponentProvider.setComponents(discovered);
        pluginDescriptors = List.copyOf(descriptors);
        log.info("BPM 组件插件加载完成: dir={} count={}", dir, discovered.size());
    }

    /** 重新扫描插件目录并同步至 MongoDB 与缓存。 */
    public void reloadAndDeploy() {
        reload();
        deploymentService.deploy(pluginBpmComponentProvider);
        componentServiceProvider.getObject().refresh();
    }

    @Override
    public void afterPropertiesSet() {
        reload();
    }

    /** 只读返回已安装插件描述（与最近一次 reload 同生命周期；不触发 reload）。 */
    public List<BpmComponentPluginDescriptor> describeInstalledPlugins() {
        if (!pluginDescriptors.isEmpty()) {
            return pluginDescriptors;
        }
        return listJarDescriptorsInPluginsDir();
    }

    public List<BpmComponentPluginDescriptor> listJarDescriptorsInPluginsDir() {
        Path dir = Path.of(pluginsDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .map(bundleReader::describeJar)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> listInstalledJarNames() {
        Path dir = Path.of(pluginsDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path resolvePluginsDir() {
        return Path.of(pluginsDir).toAbsolutePath().normalize();
    }

    /** 是否由插件 JAR 桥接进主上下文的 Bean（classpath 扫描应排除，避免 plugin_* 与 classpath_* 双份元数据）。 */
    public boolean isPluginRegisteredBean(String beanName) {
        return pluginRegisteredBeans.contains(beanName);
    }

    /**
     * 只读扫描 {@code plugins/} 下 JAR，返回 {@code plugin_{key}} → jar 文件名（不 reload、不注册 Bean）。
     */
    public Map<String, String> buildPluginJarIndex() {
        if (!pluginsEnabled) {
            return Map.of();
        }
        Path dir = Path.of(pluginsDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return Map.of();
        }
        Map<String, String> index = new LinkedHashMap<>();
        try (var stream = Files.list(dir)) {
            List<Path> jars =
                    stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .toList();
            for (Path jar : jars) {
                indexJarFile(jar, index);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描插件目录失败: " + dir, e);
        }
        return index;
    }

    private void indexJarFile(Path jarPath, Map<String, String> index) {
        String jarName = jarPath.getFileName().toString();
        String source = pluginBpmComponentProvider.getSource();
        bundleReader.scanJarComponents(jarPath).keySet().forEach(key -> index.put(source + "_" + key, jarName));
    }

    private void logPluginDescriptor(Path jar, BpmComponentPluginDescriptor descriptor) {
        boolean hasBundle = bundleReader.readFromJar(jar).isPresent();
        log.info(
                "插件包描述: jar={} hasBundle={} name={} version={} components={} warnings={}",
                jar.getFileName(),
                hasBundle,
                descriptor.getBundle() != null ? descriptor.getBundle().getName() : null,
                descriptor.getBundle() != null ? descriptor.getBundle().getVersion() : null,
                descriptor.getComponents() != null ? descriptor.getComponents().size() : 0,
                descriptor.getWarnings() != null ? descriptor.getWarnings().size() : 0);
    }

    private List<BpmComponent> loadJar(Path jarPath) {
        BpmPluginContextManager.LoadResult result = pluginContextManager.load(jarPath);
        BpmPluginContext pluginContext = result.pluginContext();
        pluginContexts.put(pluginContext.getJarFileName(), pluginContext);
        pluginRegisteredBeans.addAll(pluginContext.getBridgedBeanNames());
        return result.components();
    }

    private void closeAllPluginContexts() {
        for (BpmPluginContext pluginContext : pluginContexts.values()) {
            pluginContextManager.unregisterBridges(pluginContext);
            pluginContext.close();
        }
        pluginContexts.clear();
        pluginRegisteredBeans.clear();
    }
}
