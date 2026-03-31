package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于组件元数据计算稳定部署指纹，用于判断自动部署是否需写库。
 */
public final class BpmComponentDeploymentSignature {

    private BpmComponentDeploymentSignature() {
    }

    public static String compute(BpmComponent c) {
        String canonical = canonicalString(c);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static String canonicalString(BpmComponent c) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(StringUtils.defaultString(c.getParentId())).append('\0');
        sb.append(StringUtils.defaultString(c.getKey())).append('\0');
        sb.append(StringUtils.defaultString(c.getSource())).append('\0');
        sb.append(StringUtils.defaultString(c.getName())).append('\0');
        sb.append(StringUtils.defaultString(c.getDescription())).append('\0');
        sb.append(StringUtils.defaultString(c.getGroup())).append('\0');
        sb.append(c.getType() == null ? "" : c.getType().name()).append('\0');
        sb.append(StringUtils.defaultString(c.getVersion())).append('\0');
        appendParameters(sb, c.getInputParameters());
        sb.append('|');
        appendParameters(sb, c.getOutputParameters());
        return sb.toString();
    }

    private static void appendParameters(StringBuilder sb, List<BpmComponentParameter> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<BpmComponentParameter> sorted = new ArrayList<>();
        for (BpmComponentParameter p : list) {
            if (p != null) {
                sorted.add(p);
            }
        }
        sorted.sort(Comparator.comparing(p -> StringUtils.defaultString(p.getKey())));
        for (BpmComponentParameter p : sorted) {
            sb.append(StringUtils.defaultString(p.getKey())).append('\0');
            sb.append(StringUtils.defaultString(p.getName())).append('\0');
            sb.append(StringUtils.defaultString(p.getDescription())).append('\0');
            sb.append(StringUtils.defaultString(p.getDefaultValue())).append('\0');
            sb.append(p.isArray()).append('\0');
            sb.append(p.isRequired()).append('\0');
            sb.append(p.isReadonly()).append('\0');
            sb.append(p.isHidden()).append('\0');
            sb.append(StringUtils.defaultString(p.getHtmlType())).append('\0');
            sb.append(StringUtils.defaultString(p.getType())).append('\0');
            sb.append(StringUtils.defaultString(p.getExample())).append('\0');
            sb.append(StringUtils.defaultString(p.getDictKey())).append('\0');
            sb.append(StringUtils.defaultString(p.getGroup())).append('\0');
            sb.append(p.isImportant()).append('\0');
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
