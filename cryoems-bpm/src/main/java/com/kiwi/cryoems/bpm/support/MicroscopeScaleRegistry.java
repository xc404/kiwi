package com.kiwi.cryoems.bpm.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiwi.common.utils.JsonUtils;
import com.kiwi.cryoems.bpm.model.ClosetScale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 从与 cyroems 同结构的 {@code microscope_config.json} 中读取各电镜 {@code scales}，按 {@code p_size} 选最近档位（同
 * {@code MicroscopeConfig#getClosetScale}）。
 */
@Component
@Slf4j
public class MicroscopeScaleRegistry {

    private final Object loadLock = new Object();

    @Value("${cryoems.bpm.microscope-config:classpath:microscope_config.json}")
    private Resource microscopeConfigResource;

    private volatile JsonNode root;

    /**
     * @return 无 scales 配置或显微镜不存在时返回 {@code null}（例如 Titan3_falcon）
     */
    public ClosetScale closestScale(String microscopeKey, double pSize) {
        if (microscopeKey == null || microscopeKey.isBlank()) {
            return null;
        }
        JsonNode mic = root().get(microscopeKey.trim());
        if (mic == null || !mic.has("scales") || !mic.get("scales").isObject()) {
            return null;
        }
        JsonNode scales = mic.get("scales");
        String bestKey =
                iteratorKeys(scales)
                        .min(Comparator.comparingDouble(k -> Math.abs(Double.parseDouble(k) - pSize)))
                        .orElse(null);
        if (bestKey == null) {
            return null;
        }
        JsonNode entry = scales.get(bestKey);
        if (entry == null || !entry.isObject()) {
            return null;
        }
        ClosetScale out = new ClosetScale();
        out.setMatchedPixelSizeKey(bestKey);
        out.setMajorScale(entry.path("major_scale").asDouble());
        out.setMinorScale(entry.path("minor_scale").asDouble());
        out.setDistortAng(entry.path("distort_ang").asDouble());
        return out;
    }

    private JsonNode root() {
        JsonNode r = root;
        if (r != null) {
            return r;
        }
        synchronized (loadLock) {
            if (root == null) {
                root = loadRoot();
            }
            return root;
        }
    }

    private JsonNode loadRoot() {
        if (microscopeConfigResource == null || !microscopeConfigResource.exists()) {
            log.warn("cryoems.bpm.microscope-config 资源不存在，closetScale 将始终为 null: {}", microscopeConfigResource);
            return JsonUtils.createObjectNode();
        }
        try (InputStream in = microscopeConfigResource.getInputStream()) {
            JsonNode node = JsonUtils.readTree(in);
            if (!node.isObject()) {
                throw new IllegalStateException("microscope_config 根节点须为 JSON 对象");
            }
            return node;
        } catch (IOException e) {
            throw new IllegalStateException("读取 microscope_config 失败: " + e.getMessage(), e);
        }
    }

    /** 测试或热加载时清空缓存。 */
    public void clearCache() {
        synchronized (loadLock) {
            root = null;
        }
    }

    private static Stream<String> iteratorKeys(JsonNode scales) {
        ArrayList<String> keys = new ArrayList<>();
        scales.fieldNames().forEachRemaining(keys::add);
        return keys.stream();
    }
}
