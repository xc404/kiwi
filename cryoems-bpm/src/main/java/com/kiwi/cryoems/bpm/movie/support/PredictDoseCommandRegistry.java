package com.kiwi.cryoems.bpm.movie.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 按显微镜类型解析 predict dose（Titan contrast_mean）可执行路径，对齐 cyroems
 * {@code MicroscopeConfig#getPredict_dose} / {@code SoftwareExe.Titan*Mean}。
 */
@Component
public class PredictDoseCommandRegistry {

    @Value("${cryoems.bpm.predict-dose.titan1-command:Titan1Mean}")
    private String titan1Command;

    @Value("${cryoems.bpm.predict-dose.titan2-command:Titan2Mean}")
    private String titan2Command;

    @Value("${cryoems.bpm.predict-dose.titan3-command:Titan3Mean}")
    private String titan3Command;

    public String resolve(String microscopeKey) {
        if (!StringUtils.hasText(microscopeKey)) {
            throw new IllegalArgumentException("microscope 不能为空");
        }
        String key = microscopeKey.trim();
        return switch (key) {
            case "Titan1_k3", "Titan1", "titan1" -> titan1Command;
            case "Titan2_k3", "Titan2", "titan2" -> titan2Command;
            case "Titan3_falcon", "Titan3", "titan3" -> titan3Command;
            default -> throw new IllegalArgumentException("不支持的显微镜类型: " + key);
        };
    }
}
