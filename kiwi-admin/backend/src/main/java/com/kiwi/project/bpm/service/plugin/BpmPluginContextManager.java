package com.kiwi.project.bpm.service.plugin;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentBundleManifest;
import com.kiwi.project.bpm.service.PluginBpmComponentProvider;
import com.kiwi.project.bpm.utils.BpmComponentBundleReader;
import com.kiwi.project.bpm.utils.ComponentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 为每个插件 JAR 创建子 {@link AnnotationConfigApplicationContext}，并将 delegate 桥接至宿主。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BpmPluginContextManager {

    private final ConfigurableApplicationContext hostContext;
    private final BpmComponentBundleReader bundleReader;
    private final PluginBpmComponentProvider pluginBpmComponentProvider;

    /**
     * 加载单个插件 JAR：子上下文 refresh + 桥接 delegate 至宿主。
     *
     * @return 插件元数据及运行时上下文（调用方负责在 unload 时 {@link BpmPluginContext#close()}）
     */
    public LoadResult load(Path jarPath) {
        String jarFileName = jarPath.getFileName().toString();
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader pluginCl = new URLClassLoader(new URL[] {jarUrl}, hostContext.getClassLoader());

            Optional<BpmComponentBundleManifest> manifestOpt = bundleReader.readFromJar(jarPath);
            List<DelegateClassInfo> delegates = scanDelegateClasses(jarPath, pluginCl);

            AnnotationConfigApplicationContext childCtx = new AnnotationConfigApplicationContext();
            childCtx.setParent(hostContext);
            childCtx.setClassLoader(pluginCl);
            bootstrapChildContext(childCtx, manifestOpt.orElse(null), delegates, pluginCl);
            childCtx.refresh();

            List<BpmComponent> components = new ArrayList<>();
            List<String> bridgedBeanNames = new ArrayList<>();
            DefaultListableBeanFactory hostFactory = (DefaultListableBeanFactory) hostContext.getBeanFactory();

            for (DelegateClassInfo delegate : delegates) {
                String beanName = delegate.beanName();
                if (!childCtx.containsBean(beanName)) {
                    log.warn(
                            "插件 delegate 未在子上下文中注册为 Bean: jar={} bean={} class={}",
                            jarFileName,
                            beanName,
                            delegate.className());
                    continue;
                }
                Object bridge = createBridge(childCtx, beanName, delegate.clazz());
                if (!registerBridge(hostFactory, beanName, bridge)) {
                    continue;
                }
                bridgedBeanNames.add(beanName);

                BpmComponent meta = ComponentUtils.fromClass(delegate.clazz());
                if (meta != null) {
                    if (StringUtils.isBlank(meta.getKey())) {
                        meta.setKey(beanName);
                    }
                    meta.setSource(pluginBpmComponentProvider.getSource());
                    meta.setId(meta.getSource() + "_" + meta.getKey());
                    components.add(meta);
                    log.info("已加载插件组件: jar={} bean={} key={}", jarFileName, beanName, meta.getKey());
                }
            }

            BpmPluginContext pluginContext =
                    new BpmPluginContext(jarFileName, pluginCl, childCtx, List.copyOf(bridgedBeanNames));
            return new LoadResult(pluginContext, components);
        } catch (IOException e) {
            throw new UncheckedIOException("加载插件 JAR 失败: " + jarPath, e);
        }
    }

    public void unregisterBridges(BpmPluginContext pluginContext) {
        DefaultListableBeanFactory hostFactory = (DefaultListableBeanFactory) hostContext.getBeanFactory();
        for (String beanName : pluginContext.getBridgedBeanNames()) {
            if (hostFactory.containsSingleton(beanName)) {
                hostFactory.destroySingleton(beanName);
            }
        }
    }

    private void bootstrapChildContext(
            AnnotationConfigApplicationContext childCtx,
            BpmComponentBundleManifest manifest,
            List<DelegateClassInfo> delegates,
            URLClassLoader pluginCl)
            throws IOException {
        if (manifest != null && StringUtils.isNotBlank(manifest.getContextClass())) {
            try {
                Class<?> configClass = pluginCl.loadClass(manifest.getContextClass().trim());
                childCtx.register(configClass);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "插件 contextClass 不存在: " + manifest.getContextClass(), e);
            }
            return;
        }
        if (manifest != null
                && manifest.getScanPackages() != null
                && !manifest.getScanPackages().isEmpty()) {
            List<String> packages =
                    manifest.getScanPackages().stream()
                            .filter(StringUtils::isNotBlank)
                            .map(String::trim)
                            .toList();
            if (!packages.isEmpty()) {
                childCtx.scan(packages.toArray(String[]::new));
                return;
            }
        }
        Set<String> derivedPackages = deriveScanPackages(delegates);
        if (derivedPackages.isEmpty()) {
            throw new IllegalStateException("插件 JAR 未找到可扫描的包，请配置 contextClass 或 scanPackages");
        }
        childCtx.scan(derivedPackages.toArray(String[]::new));
    }

    private Set<String> deriveScanPackages(List<DelegateClassInfo> delegates) {
        Set<String> packages = new LinkedHashSet<>();
        for (DelegateClassInfo delegate : delegates) {
            String pkg = delegate.clazz().getPackageName();
            if (StringUtils.isBlank(pkg)) {
                continue;
            }
            packages.add(pkg);
        }
        return packages;
    }

    private List<DelegateClassInfo> scanDelegateClasses(Path jarPath, URLClassLoader pluginCl) throws IOException {
        List<DelegateClassInfo> out = new ArrayList<>();
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
                    clazz = pluginCl.loadClass(className);
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
                out.add(new DelegateClassInfo(clazz, resolveBeanName(clazz)));
            }
        }
        return out;
    }

    private Object createBridge(
            ConfigurableApplicationContext childCtx, String beanName, Class<?> delegateClass) {
        if (ExternalTaskHandler.class.isAssignableFrom(delegateClass)) {
            return PluginDelegateBridge.externalTaskHandler(childCtx, beanName);
        }
        return PluginDelegateBridge.javaDelegate(childCtx, beanName);
    }

    private boolean registerBridge(DefaultListableBeanFactory hostFactory, String beanName, Object bridge) {
        if (hostFactory.containsSingleton(beanName)) {
            log.warn("跳过插件桥接 Bean（主上下文已存在同名 Bean）: {}", beanName);
            return false;
        }
        hostFactory.registerSingleton(beanName, bridge);
        return true;
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

    public record LoadResult(BpmPluginContext pluginContext, List<BpmComponent> components) {}

    private record DelegateClassInfo(Class<?> clazz, String beanName) {
        String className() {
            return clazz.getName();
        }
    }
}
