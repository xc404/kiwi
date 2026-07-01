package com.kiwi.project.bpm.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.project.bpm.dto.BpmComponentPluginComponentInfo;
import com.kiwi.project.bpm.dto.BpmComponentPluginDescriptor;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentBundleComponentEntry;
import com.kiwi.project.bpm.model.BpmComponentBundleManifest;
import com.kiwi.project.bpm.service.PluginBpmComponentProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 读取插件 JAR 内 {@link BpmComponentBundleManifest}，并与注解扫描结果合并。
 */
@Component
@RequiredArgsConstructor
public class BpmComponentBundleReader {

    private static final String ManifestSource = "manifest";
    private static final String ScannedSource = "scanned";

    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;
    private final PluginBpmComponentProvider pluginBpmComponentProvider;

    public Optional<BpmComponentBundleManifest> readFromJar(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return Optional.empty();
        }
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(BpmComponentBundleManifest.BundleResourcePath);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                BpmComponentBundleManifest manifest = objectMapper.readValue(in, BpmComponentBundleManifest.class);
                return Optional.of(manifest);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取 component-bundle.json 失败: " + jar, e);
        }
    }

    /**
     * 合并 bundle 与注解扫描，生成插件描述。清单 key 与扫描不一致时抛出 {@link IllegalArgumentException}。
     */
    public BpmComponentPluginDescriptor describeJar(Path jar) {
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("JAR 文件不存在: " + jar);
        }
        String fileName = jar.getFileName().toString();
        Map<String, ScannedComponent> scanned = scanJarComponents(jar);
        Optional<BpmComponentBundleManifest> manifestOpt = readFromJar(jar);

        BpmComponentPluginDescriptor descriptor = new BpmComponentPluginDescriptor();
        descriptor.setFileName(fileName);
        descriptor.setFileSizeBytes(fileSize(jar));
        descriptor.setSha256(sha256Hex(jar));
        descriptor.setWarnings(new ArrayList<>());

        BpmComponentBundleManifest bundle;
        List<BpmComponentPluginComponentInfo> components;
        if (manifestOpt.isPresent()) {
            bundle = manifestOpt.get();
            validateManifest(bundle, scanned.keySet());
            components = mergeFromManifest(bundle, scanned, descriptor.getWarnings());
        } else {
            bundle = fallbackBundle(fileName, scanned);
            components = fromScannedOnly(scanned);
        }
        descriptor.setBundle(bundle);
        descriptor.setComponents(components);
        return descriptor;
    }

    public Map<String, ScannedComponent> scanJarComponents(Path jarPath) {
        Map<String, ScannedComponent> index = new LinkedHashMap<>();
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader cl = new URLClassLoader(new URL[] {jarUrl}, applicationContext.getClassLoader());
            try (JarFile jarFile = new JarFile(jarPath.toFile()); cl) {
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
                    String beanKey = resolveBeanName(clazz);
                    BpmComponent meta = ComponentUtils.fromClass(clazz);
                    ScannedComponent scanned = new ScannedComponent();
                    scanned.key = beanKey;
                    if (meta != null) {
                        scanned.name = meta.getName();
                        scanned.group = meta.getGroup();
                        scanned.description = meta.getDescription();
                        scanned.version = meta.getVersion();
                        scanned.type = meta.getType();
                        if (StringUtils.isNotBlank(meta.getKey())) {
                            scanned.key = meta.getKey();
                        }
                    }
                    if (StringUtils.isBlank(scanned.name)) {
                        scanned.name = beanKey;
                    }
                    index.putIfAbsent(scanned.key, scanned);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描插件 JAR 失败: " + jarPath, e);
        }
        return index;
    }

    private void validateManifest(BpmComponentBundleManifest manifest, Set<String> scannedKeys) {
        if (!BpmComponentBundleManifest.SchemaVersionValue.equals(manifest.getSchemaVersion())) {
            throw new IllegalArgumentException(
                    "component-bundle.json schemaVersion 必须为 \""
                            + BpmComponentBundleManifest.SchemaVersionValue
                            + "\"");
        }
        if (StringUtils.isBlank(manifest.getName())) {
            throw new IllegalArgumentException("component-bundle.json name 不能为空");
        }
        if (StringUtils.isBlank(manifest.getVersion())) {
            throw new IllegalArgumentException("component-bundle.json version 不能为空");
        }
        if (manifest.getComponents() == null || manifest.getComponents().isEmpty()) {
            throw new IllegalArgumentException("component-bundle.json components 不能为空");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (BpmComponentBundleComponentEntry entry : manifest.getComponents()) {
            if (entry == null || StringUtils.isBlank(entry.getKey())) {
                throw new IllegalArgumentException("component-bundle.json components[].key 不能为空");
            }
            if (StringUtils.isBlank(entry.getName())) {
                throw new IllegalArgumentException(
                        "component-bundle.json components[].name 不能为空（key=" + entry.getKey() + "）");
            }
            if (!seen.add(entry.getKey())) {
                throw new IllegalArgumentException("component-bundle.json 存在重复 key: " + entry.getKey());
            }
            if (!scannedKeys.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "component-bundle.json 中的 key 在 JAR 注解扫描中不存在: " + entry.getKey());
            }
        }
    }

    private List<BpmComponentPluginComponentInfo> mergeFromManifest(
            BpmComponentBundleManifest manifest,
            Map<String, ScannedComponent> scanned,
            List<String> warnings) {
        List<BpmComponentPluginComponentInfo> out = new ArrayList<>();
        Set<String> listed = new LinkedHashSet<>();
        for (BpmComponentBundleComponentEntry entry : manifest.getComponents()) {
            listed.add(entry.getKey());
            ScannedComponent scan = scanned.get(entry.getKey());
            out.add(toInfo(entry, scan, ManifestSource));
        }
        for (Map.Entry<String, ScannedComponent> e : scanned.entrySet()) {
            if (listed.contains(e.getKey())) {
                continue;
            }
            warnings.add("JAR 内存在未在 component-bundle.json 列出的组件: " + e.getKey());
            out.add(toInfo(e.getValue(), ScannedSource));
        }
        return out;
    }

    private List<BpmComponentPluginComponentInfo> fromScannedOnly(Map<String, ScannedComponent> scanned) {
        List<BpmComponentPluginComponentInfo> out = new ArrayList<>();
        for (ScannedComponent value : scanned.values()) {
            out.add(toInfo(value, ScannedSource));
        }
        return out;
    }

    private BpmComponentBundleManifest fallbackBundle(String fileName, Map<String, ScannedComponent> scanned) {
        BpmComponentBundleManifest bundle = new BpmComponentBundleManifest();
        bundle.setSchemaVersion(BpmComponentBundleManifest.SchemaVersionValue);
        bundle.setName(stripJarSuffix(fileName));
        bundle.setVersion("");
        List<BpmComponentBundleComponentEntry> entries = new ArrayList<>();
        for (ScannedComponent scan : scanned.values()) {
            BpmComponentBundleComponentEntry entry = new BpmComponentBundleComponentEntry();
            entry.setKey(scan.key);
            entry.setName(scan.name);
            entry.setGroup(scan.group);
            entry.setVersion(scan.version);
            entry.setDescription(scan.description);
            if (scan.type != null) {
                entry.setType(scan.type.name());
            }
            entries.add(entry);
        }
        bundle.setComponents(entries);
        return bundle;
    }

    private BpmComponentPluginComponentInfo toInfo(
            BpmComponentBundleComponentEntry entry, ScannedComponent scan, String source) {
        BpmComponentPluginComponentInfo info = new BpmComponentPluginComponentInfo();
        info.setKey(entry.getKey());
        info.setName(entry.getName());
        info.setGroup(StringUtils.defaultIfBlank(entry.getGroup(), scan != null ? scan.group : null));
        info.setDescription(StringUtils.defaultIfBlank(entry.getDescription(), scan != null ? scan.description : null));
        info.setVersion(StringUtils.defaultIfBlank(entry.getVersion(), scan != null ? scan.version : null));
        info.setComponentId(pluginBpmComponentProvider.getSource() + "_" + entry.getKey());
        info.setSource(source);
        return info;
    }

    private BpmComponentPluginComponentInfo toInfo(ScannedComponent scan, String source) {
        BpmComponentPluginComponentInfo info = new BpmComponentPluginComponentInfo();
        info.setKey(scan.key);
        info.setName(scan.name);
        info.setGroup(scan.group);
        info.setDescription(scan.description);
        info.setVersion(scan.version);
        info.setComponentId(pluginBpmComponentProvider.getSource() + "_" + scan.key);
        info.setSource(source);
        return info;
    }

    private boolean isDelegateClass(Class<?> clazz) {
        return JavaDelegate.class.isAssignableFrom(clazz)
                || ActivityBehavior.class.isAssignableFrom(clazz)
                || ExternalTaskHandler.class.isAssignableFrom(clazz);
    }

    private String resolveBeanName(Class<?> clazz) {
        org.springframework.stereotype.Component springComponent =
                AnnotationUtils.getAnnotation(clazz, org.springframework.stereotype.Component.class);
        if (springComponent != null && StringUtils.isNotBlank(springComponent.value())) {
            return springComponent.value();
        }
        return StringUtils.uncapitalize(clazz.getSimpleName());
    }

    private static String stripJarSuffix(String fileName) {
        if (fileName != null && fileName.toLowerCase().endsWith(".jar")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private static long fileSize(Path jar) {
        try {
            return Files.size(jar);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256Hex(Path jar) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(jar);
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("计算 JAR SHA-256 失败", e);
        }
    }

    static final class ScannedComponent {
        String key;
        String name;
        String group;
        String description;
        String version;
        BpmComponent.Type type;
    }
}
