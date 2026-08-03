package com.kiwi.project.bpm.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kiwi 语义化版本比较（主.次.补丁；忽略 -SNAPSHOT 等预发布后缀）。
 */
@Component
public class KiwiVersionCompatibilityHelper {

    /**
     * @return true 当 {@code instanceVersion} &gt;= {@code requiredMinVersion}
     */
    public boolean isCompatible(String instanceVersion, String requiredMinVersion) {
        if (StringUtils.isBlank(requiredMinVersion)) {
            return true;
        }
        List<Integer> instance = parseVersion(instanceVersion);
        List<Integer> required = parseVersion(requiredMinVersion);
        int len = Math.max(instance.size(), required.size());
        for (int i = 0; i < len; i++) {
            int a = i < instance.size() ? instance.get(i) : 0;
            int b = i < required.size() ? required.get(i) : 0;
            if (a > b) {
                return true;
            }
            if (a < b) {
                return false;
            }
        }
        return true;
    }

    private List<Integer> parseVersion(String raw) {
        String core = StringUtils.defaultString(raw).trim();
        int dash = core.indexOf('-');
        if (dash > 0) {
            core = core.substring(0, dash);
        }
        String[] parts = core.split("\\.");
        List<Integer> nums = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            String digits = part.replaceAll("[^0-9].*$", "");
            if (digits.isEmpty()) {
                nums.add(0);
            } else {
                nums.add(Integer.parseInt(digits));
            }
        }
        if (nums.isEmpty()) {
            nums.add(0);
        }
        return nums;
    }
}
