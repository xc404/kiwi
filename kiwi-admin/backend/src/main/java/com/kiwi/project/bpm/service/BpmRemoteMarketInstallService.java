package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.config.BpmRemoteMarketProperties;
import com.kiwi.project.bpm.dto.BpmComponentPluginDescriptor;
import com.kiwi.project.bpm.dto.BpmRemoteMarketInstallResultDto;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.dto.InstallTemplatePackInput;
import com.kiwi.project.bpm.dto.InstallTemplatePackResult;
import com.kiwi.project.bpm.utils.KiwiVersionCompatibilityHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BpmRemoteMarketInstallService {

    private final BpmRemoteMarketService marketService;
    private final BpmRemoteMarketDownloadService downloadService;
    private final BpmTemplatePackBundleService templateBundleService;
    private final BpmComponentBundleService componentBundleService;
    private final BpmRemoteMarketProperties properties;
    private final KiwiVersionCompatibilityHelper versionHelper;

    @Transactional
    public BpmRemoteMarketInstallResultDto installTemplate(
            String slug,
            String version,
            String sourceId,
            InstallTemplatePackInput input,
            String userId) {
        BpmRemoteMarketItemDto item = marketService.requireItem(slug, version, sourceId);
        assertTemplateItem(item);
        assertCompatible(item);
        assertComponentsAvailable(item);
        byte[] zip = downloadService.downloadVerified(item);
        String filename = slug + "-" + version + ".kiwi-template-pack";
        InstallTemplatePackResult installed = templateBundleService.importAndInstallFromBytes(zip, filename, input, userId);
        BpmRemoteMarketInstallResultDto result = new BpmRemoteMarketInstallResultDto();
        result.setType("template");
        result.setSlug(slug);
        result.setVersion(version);
        result.setProjectId(installed.getProjectId());
        if (input != null && StringUtils.isNotBlank(input.getProjectName())) {
            result.setProjectName(input.getProjectName().trim());
        } else {
            result.setProjectName(item.getName() + " (from remote)");
        }
        return result;
    }

    public BpmRemoteMarketInstallResultDto installPlugin(String slug, String version, String sourceId, String userId) {
        BpmRemoteMarketItemDto item = marketService.requireItem(slug, version, sourceId);
        assertPluginItem(item);
        assertCompatible(item);
        byte[] jar = downloadService.downloadVerified(item);
        String fileName = resolvePluginFileName(item);
        List<BpmComponentPluginDescriptor> plugins = componentBundleService.installJarFromBytes(jar, fileName);
        BpmRemoteMarketInstallResultDto result = new BpmRemoteMarketInstallResultDto();
        result.setType("plugin");
        result.setSlug(slug);
        result.setVersion(version);
        result.setPluginFileName(fileName);
        result.setInstalledComponentKeys(plugins.stream()
                .filter(p -> fileName.equals(p.getFileName()))
                .findFirst()
                .map(p -> p.getComponents() == null ? List.<String>of() : p.getComponents().stream()
                        .map(c -> c.getKey())
                        .filter(StringUtils::isNotBlank)
                        .toList())
                .orElse(List.of()));
        return result;
    }

    private void assertTemplateItem(BpmRemoteMarketItemDto item) {
        if (!"template".equalsIgnoreCase(item.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条目不是模板类型");
        }
    }

    private void assertPluginItem(BpmRemoteMarketItemDto item) {
        if (!"plugin".equalsIgnoreCase(item.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条目不是插件类型");
        }
    }

    private void assertCompatible(BpmRemoteMarketItemDto item) {
        if (!versionHelper.isCompatible(properties.getKiwiVersion(), item.getKiwiMinVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前 Kiwi 版本 " + properties.getKiwiVersion()
                            + " 低于要求 " + item.getKiwiMinVersion());
        }
    }

    private void assertComponentsAvailable(BpmRemoteMarketItemDto item) {
        if (item.getMissingComponentKeys() != null && !item.getMissingComponentKeys().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "缺少依赖组件: " + String.join(", ", item.getMissingComponentKeys()));
        }
    }

    private String resolvePluginFileName(BpmRemoteMarketItemDto item) {
        if (item.getMavenCoordinate() != null
                && StringUtils.isNotBlank(item.getMavenCoordinate().getArtifactId())) {
            String artifact = item.getMavenCoordinate().getArtifactId();
            String ver = StringUtils.defaultIfBlank(item.getMavenCoordinate().getVersion(), item.getVersion());
            return artifact + "-" + ver + ".jar";
        }
        return item.getSlug() + "-" + item.getVersion() + ".jar";
    }
}
