package com.kiwi.project.bpm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.config.BpmRemoteMarketProperties;
import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDetailDto;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.dto.BpmRemoteMarketSyncResultDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmRemoteMarketIndex;
import com.kiwi.project.bpm.model.BpmRemoteMarketIndexItem;
import com.kiwi.project.bpm.utils.KiwiVersionCompatibilityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BpmRemoteMarketService {

    private static final int SupportedSchemaVersion = 1;

    private final BpmRemoteMarketProperties properties;
    private final BpmRemoteMarketHttpFetcher httpFetcher;
    private final ObjectMapper objectMapper;
    private final BpmComponentDao componentDao;
    private final KiwiVersionCompatibilityHelper versionHelper;

    private final Map<String, CachedItems> cacheBySource = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return properties.isEnabled() && properties.getSources() != null && !properties.getSources().isEmpty();
    }

    public List<BpmRemoteMarketItemDto> listItems(String type, String keyword, String sourceId) {
        if (!isEnabled()) {
            return List.of();
        }
        ensureFreshCache(false);
        String kw = StringUtils.trimToEmpty(keyword).toLowerCase();
        return cacheBySource.values().stream()
                .flatMap(c -> c.items().stream())
                .filter(item -> StringUtils.isBlank(sourceId) || sourceId.equals(item.getSourceId()))
                .filter(item -> StringUtils.isBlank(type) || type.equalsIgnoreCase(item.getType()))
                .filter(item -> kw.isEmpty() || matchesKeyword(item, kw))
                .sorted(Comparator.comparing(BpmRemoteMarketItemDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public BpmRemoteMarketItemDetailDto getItem(String slug, String version, String sourceId) {
        BpmRemoteMarketItemDto base = requireItem(slug, version, sourceId);
        BpmRemoteMarketItemDetailDto detail = copyToDetail(base);
        if (StringUtils.isNotBlank(base.getManifestUrl())) {
            BpmRemoteMarketProperties.Source source = requireSource(base.getSourceId());
            try {
                String json = httpFetcher.fetchText(base.getManifestUrl(), source);
                detail.setManifest(objectMapper.readValue(json, Map.class));
            } catch (Exception e) {
                log.warn("拉取 manifest 失败 slug={} version={}: {}", slug, version, e.getMessage());
            }
        }
        return detail;
    }

    public BpmRemoteMarketItemDto requireItem(String slug, String version, String sourceId) {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "远程市场未启用");
        }
        ensureFreshCache(false);
        return cacheBySource.values().stream()
                .flatMap(c -> c.items().stream())
                .filter(i -> slug.equals(i.getSlug()) && version.equals(i.getVersion()))
                .filter(i -> StringUtils.isBlank(sourceId) || sourceId.equals(i.getSourceId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "远程市场条目不存在: " + slug + "@" + version));
    }

    public BpmRemoteMarketSyncResultDto sync() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "远程市场未启用");
        }
        ensureFreshCache(true);
        BpmRemoteMarketSyncResultDto result = new BpmRemoteMarketSyncResultDto();
        result.setSourceCount(properties.getSources().size());
        result.setItemCount(cacheBySource.values().stream().mapToInt(c -> c.items().size()).sum());
        result.setFetchedAt(System.currentTimeMillis());
        return result;
    }

    public BpmRemoteMarketProperties properties() {
        return properties;
    }

    public BpmRemoteMarketProperties.Source requireSource(String sourceId) {
        return properties.getSources().stream()
                .filter(s -> sourceId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "市场源不存在: " + sourceId));
    }

    private void ensureFreshCache(boolean force) {
        long ttlMs = Math.max(0, properties.getCacheTtlSeconds()) * 1000L;
        long now = System.currentTimeMillis();
        for (BpmRemoteMarketProperties.Source source : properties.getSources()) {
            if (StringUtils.isAnyBlank(source.getId(), source.getBaseUrl())) {
                continue;
            }
            CachedItems cached = cacheBySource.get(source.getId());
            if (!force && cached != null && now - cached.fetchedAt() < ttlMs) {
                continue;
            }
            try {
                List<BpmRemoteMarketItemDto> items = fetchSourceItems(source);
                cacheBySource.put(source.getId(), new CachedItems(now, items));
            } catch (Exception e) {
                log.error("拉取远程市场源失败 id={} url={}: {}", source.getId(), source.getBaseUrl(), e.getMessage());
                if (force && cached == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "拉取远程市场索引失败: " + source.getId(), e);
                }
            }
        }
    }

    private List<BpmRemoteMarketItemDto> fetchSourceItems(BpmRemoteMarketProperties.Source source) throws Exception {
        String indexUrl = buildIndexUrl(source);
        String json = httpFetcher.fetchText(indexUrl, source);
        BpmRemoteMarketIndex index = objectMapper.readValue(json, BpmRemoteMarketIndex.class);
        if (index.getSchemaVersion() > SupportedSchemaVersion) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "不支持的 index schemaVersion: " + index.getSchemaVersion());
        }
        Set<String> deployedKeys = componentDao.findAll().stream()
                .map(BpmComponent::getKey)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        List<BpmRemoteMarketItemDto> items = new ArrayList<>();
        for (BpmRemoteMarketIndexItem raw : index.getItems()) {
            items.add(toDto(raw, source, deployedKeys));
        }
        return items;
    }

    private String buildIndexUrl(BpmRemoteMarketProperties.Source source) {
        String path = StringUtils.defaultIfBlank(source.getIndexPath(), "market/index.json");
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return httpFetcher.resolveUrl(path, source);
    }

    private BpmRemoteMarketItemDto toDto(
            BpmRemoteMarketIndexItem raw,
            BpmRemoteMarketProperties.Source source,
            Set<String> deployedKeys) {
        BpmRemoteMarketItemDto dto = new BpmRemoteMarketItemDto();
        dto.setSourceId(source.getId());
        dto.setSourceName(source.getName());
        dto.setType(raw.getType());
        dto.setSlug(raw.getSlug());
        dto.setName(raw.getName());
        dto.setVersion(raw.getVersion());
        dto.setSummary(raw.getSummary());
        dto.setCategory(raw.getCategory());
        dto.setTags(raw.getTags() != null ? new ArrayList<>(raw.getTags()) : new ArrayList<>());
        dto.setKiwiMinVersion(raw.getKiwiMinVersion());
        dto.setDownloadUrl(resolveItemUrl(raw.getDownloadUrl(), source));
        dto.setSha256(raw.getSha256());
        dto.setManifestUrl(StringUtils.isNotBlank(raw.getManifestUrl())
                ? resolveItemUrl(raw.getManifestUrl(), source) : null);
        dto.setSignatureUrl(StringUtils.isNotBlank(raw.getSignatureUrl())
                ? resolveItemUrl(raw.getSignatureUrl(), source) : null);
        dto.setKind(raw.getKind());
        dto.setProcessCount(raw.getProcessCount());
        dto.setRequiredComponentKeys(raw.getRequiredComponentKeys() != null
                ? new ArrayList<>(raw.getRequiredComponentKeys()) : new ArrayList<>());
        dto.setComponentKeys(raw.getComponentKeys() != null
                ? new ArrayList<>(raw.getComponentKeys()) : new ArrayList<>());
        dto.setMavenCoordinate(raw.getMavenCoordinate());
        boolean compatible = versionHelper.isCompatible(properties.getKiwiVersion(), raw.getKiwiMinVersion());
        dto.setKiwiCompatible(compatible);
        if (raw.getRequiredComponentKeys() != null) {
            List<String> missing = raw.getRequiredComponentKeys().stream()
                    .filter(k -> StringUtils.isNotBlank(k) && !deployedKeys.contains(k))
                    .toList();
            dto.setMissingComponentKeys(new ArrayList<>(missing));
        }
        return dto;
    }

    private String resolveItemUrl(String url, BpmRemoteMarketProperties.Source source) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        return httpFetcher.resolveUrl(url, source);
    }

    private boolean matchesKeyword(BpmRemoteMarketItemDto item, String kw) {
        return contains(item.getName(), kw)
                || contains(item.getSlug(), kw)
                || contains(item.getSummary(), kw)
                || contains(item.getCategory(), kw)
                || item.getTags() != null && item.getTags().stream().anyMatch(t -> contains(t, kw));
    }

    private boolean contains(String text, String kw) {
        return text != null && text.toLowerCase().contains(kw);
    }

    private BpmRemoteMarketItemDetailDto copyToDetail(BpmRemoteMarketItemDto base) {
        BpmRemoteMarketItemDetailDto detail = new BpmRemoteMarketItemDetailDto();
        detail.setSourceId(base.getSourceId());
        detail.setSourceName(base.getSourceName());
        detail.setType(base.getType());
        detail.setSlug(base.getSlug());
        detail.setName(base.getName());
        detail.setVersion(base.getVersion());
        detail.setSummary(base.getSummary());
        detail.setCategory(base.getCategory());
        detail.setTags(base.getTags());
        detail.setKiwiMinVersion(base.getKiwiMinVersion());
        detail.setDownloadUrl(base.getDownloadUrl());
        detail.setSha256(base.getSha256());
        detail.setManifestUrl(base.getManifestUrl());
        detail.setSignatureUrl(base.getSignatureUrl());
        detail.setKind(base.getKind());
        detail.setProcessCount(base.getProcessCount());
        detail.setRequiredComponentKeys(base.getRequiredComponentKeys());
        detail.setComponentKeys(base.getComponentKeys());
        detail.setMavenCoordinate(base.getMavenCoordinate());
        detail.setKiwiCompatible(base.isKiwiCompatible());
        detail.setMissingComponentKeys(base.getMissingComponentKeys());
        return detail;
    }

    private record CachedItems(long fetchedAt, List<BpmRemoteMarketItemDto> items) {
    }
}
