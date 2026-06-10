package com.kiwi.project.bpm.service;

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
import java.util.List;

@Service
@RequiredArgsConstructor
public class BpmComponentBundleService {

    private final BpmComponentPluginLoader pluginLoader;

    public List<String> listInstalled() {
        return pluginLoader.listInstalledJarNames();
    }

    public void uploadJar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 JAR 文件");
        }
        String original = file.getOriginalFilename();
        if (StringUtils.isBlank(original) || !original.toLowerCase().endsWith(".jar")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .jar 文件");
        }
        String safeName = Path.of(original).getFileName().toString();
        Path target = pluginLoader.resolvePluginsDir().resolve(safeName);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("保存插件 JAR 失败", e);
        }
        pluginLoader.reloadAndDeploy();
    }

    public void reload() {
        pluginLoader.reloadAndDeploy();
    }

    public void deleteJar(String fileName) {
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
    }
}
