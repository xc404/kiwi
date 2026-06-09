package com.kiwi.project.bpm.service;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.utils.ComponentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 从 {@code bpm.component.plugins-dir} 加载插件 JAR，注册 Spring Bean 并同步元数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BpmComponentPluginLoader implements InitializingBean {

    private final ConfigurableApplicationContext applicationContext;
    private final BpmComponentDeploymentService deploymentService;
    private final BpmComponentService componentService;
    private final PluginBpmComponentProvider pluginBpmComponentProvider;

    @Value("${bpm.component.plugins-dir:plugins}")
    private String pluginsDir;

    @Value("${bpm.component.plugins-enabled:true}")
    private boolean pluginsEnabled;

    private final Map<String, URLClassLoader> jarClassLoaders = new ConcurrentHashMap<>();

    public synchronized void reload() {
        if (!pluginsEnabled) {
            log.info("BPM 组件插件加载已禁用 (bpm.component.plugins-enabled=false)");
            pluginBpmComponentProvider.setComponents(List.of());
            return;
        }
        Path dir = Path.of(pluginsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建插件目录: " + dir, e);
        }

        closeClassLoaders();
        List<BpmComponent> discovered = new ArrayList<>();

        try (var stream = Files.list(dir)) {
            List<Path> jars =
                    stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"))
                            .sorted()
                            .toList();
            for (Path jar : jars) {
                discovered.addAll(loadJar(jar));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描插件目录失败: " + dir, e);
        }

        pluginBpmComponentProvider.setComponents(discovered);
        log.info("BPM 组件插件加载完成: dir={} count={}", dir, discovered.size());
    }

    /** 重新扫描插件目录并同步至 MongoDB 与缓存。 */
    public void reloadAndDeploy() {
        reload();
        deploymentService.deploy(pluginBpmComponentProvider);
        componentService.refresh();
    }

    @Override
    public void afterPropertiesSet() {
        reload();
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

    private List<BpmComponent> loadJar(Path jarPath) {
        List<BpmComponent> out = new ArrayList<>();
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader cl = new URLClassLoader(new URL[] {jarUrl}, applicationContext.getClassLoader());
            jarClassLoaders.put(jarPath.getFileName().toString(), cl);

            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                        continue;
                    }
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    if (className.contains("$")) {
                        continue;
                    }
                    Class<?> clazz;
                    try {
                        clazz = cl.loadClass(className);
                    } catch (LinkageError | ClassNotFoundException ex) {
                        continue;
                    }
                    if (!isDelegateClass(clazz)) {
                        continue;
                    }
                    ComponentDescription desc = AnnotationUtils.getAnnotation(clazz, ComponentDescription.class);
                    if (desc == null) {
                        continue;
                    }
                    String beanName = resolveBeanName(clazz);
                    registerBean(beanName, clazz);
                    BpmComponent meta = ComponentUtils.fromClass(clazz);
                    if (meta != null) {
                        if (StringUtils.isBlank(meta.getKey())) {
                            meta.setKey(beanName);
                        }
                        meta.setSource(pluginBpmComponentProvider.getSource());
                        meta.setId(meta.getSource() + "_" + meta.getKey());
                        out.add(meta);
                        log.info("已加载插件组件: jar={} bean={} key={}", jarPath.getFileName(), beanName, meta.getKey());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取插件 JAR 失败: " + jarPath, e);
        }
        return out;
    }

    private boolean isDelegateClass(Class<?> clazz) {
        return JavaDelegate.class.isAssignableFrom(clazz)
                || ActivityBehavior.class.isAssignableFrom(clazz)
                || ExternalTaskHandler.class.isAssignableFrom(clazz);
    }

    private String resolveBeanName(Class<?> clazz) {
        Component springComponent = AnnotationUtils.getAnnotation(clazz, Component.class);
        if (springComponent != null && StringUtils.isNotBlank(springComponent.value())) {
            return springComponent.value();
        }
        return StringUtils.uncapitalize(clazz.getSimpleName());
    }

    private void registerBean(String beanName, Class<?> clazz) {
        DefaultListableBeanFactory beanFactory =
                (DefaultListableBeanFactory) applicationContext.getBeanFactory();
        if (beanFactory.containsSingleton(beanName)) {
            log.warn("跳过插件 Bean（主上下文已存在同名 Bean）: {}", beanName);
            return;
        }
        AutowireCapableBeanFactory autowire = applicationContext.getAutowireCapableBeanFactory();
        Object bean = autowire.createBean(clazz);
        autowire.initializeBean(bean, beanName);
        beanFactory.registerSingleton(beanName, bean);
    }

    private void closeClassLoaders() {
        for (URLClassLoader cl : jarClassLoaders.values()) {
            try {
                cl.close();
            } catch (IOException e) {
                log.debug("关闭插件 ClassLoader 失败", e);
            }
        }
        jarClassLoaders.clear();
    }
}
