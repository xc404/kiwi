package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dto.BpmComponentPluginDescriptor;
import com.kiwi.project.bpm.utils.BpmComponentBundleReader;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BpmComponentBundleService {

    private final BpmComponentPluginLoader pluginLoader;
    private final BpmComponentBundleReader bundleReader;

    public List<BpmComponentPluginDescriptor> listInstalled() {
        return pluginLoader.describeInstalledPlugins();
    }

    public BpmComponentPluginDescriptor previewJar(MultipartFile file) {
        Path temp = saveToTempJar(file);
        try {
            return describeOrBadRequest(temp);
        } finally {
            deleteQuietly(temp);
        }
    }

    public List<BpmComponentPluginDescriptor> uploadJar(MultipartFile file) {
        validateJarFile(file);
        String safeName = Path.of(file.getOriginalFilename()).getFileName().toString();
        Path temp = saveToTempJar(file);
        try {
            describeOrBadRequest(temp);
            Path target = pluginLoader.resolvePluginsDir().resolve(safeName);
            Files.createDirectories(target.getParent());
            Files.copy(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存插件 JAR 失败", e);
        } finally {
            deleteQuietly(temp);
        }
        pluginLoader.reloadAndDeploy();
        return listInstalled();
    }

    public List<BpmComponentPluginDescriptor> reload() {
        pluginLoader.reloadAndDeploy();
        return listInstalled();
    }

    public List<BpmComponentPluginDescriptor> deleteJar(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名不能为空");
        }
        String safeName = Path.of(fileName).getFileName().toString();
        if (!safeName.toLowerCase().endsWith(".jar")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持删除 .jar 文件");
        }
        Path target = pluginLoader.resolvePluginsDir().resolve(safeName);
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "插件 JAR 不存在: " + safeName);
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new UncheckedIOException("删除插件 JAR 失败", e);
        }
        pluginLoader.reloadAndDeploy();
        return listInstalled();
    }

    private BpmComponentPluginDescriptor describeOrBadRequest(Path jar) {
        try {
            return bundleReader.describeJar(jar);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private void validateJarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 JAR 文件");
        }
        String original = file.getOriginalFilename();
        if (StringUtils.isBlank(original) || !original.toLowerCase().endsWith(".jar")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .jar 文件");
        }
    }

    private Path saveToTempJar(MultipartFile file) {
        validateJarFile(file);
        try {
            Path temp = Files.createTempFile("kiwi-plugin-preview-", ".jar");
            file.transferTo(temp);
            return temp;
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传 JAR 失败", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // preview 临时文件清理失败不阻断主流程
        }
    }
}
