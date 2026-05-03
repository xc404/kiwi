package com.cryo.service;

import com.cryo.dao.MicroscopeRepository;
import com.cryo.model.Microscope;
import com.cryo.model.MicroscopeConfig;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MicroscopeService implements InitializingBean
{
    private Map<String, MicroscopeConfig> microscopeConfigs = new HashMap<>();
    private final MicroscopeRepository microscopeRepository;

    @Value("${app.microscope_config}")
    private String config_path;

    public String keyFromId(String microscopeId) {
        Microscope microscope = microscopeRepository.findById(microscopeId).orElseThrow(() -> new RuntimeException("Microscope not found: " + microscopeId));
        return microscope.getMicroscope_key();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        reload();
    }

    public Map<String, MicroscopeConfig> reload() {
        File file;
        try {
            file = ResourceUtils.getFile(config_path);

            Map<String, MicroscopeConfig> configMap = JsonUtil.readMap(new FileInputStream(file), String.class, MicroscopeConfig.class);
            configMap.forEach((key, value) -> {
                value.setMicroscope(key);
                microscopeConfigs.put(key, value);
            });
            return Map.copyOf(this.microscopeConfigs);
        } catch( FileNotFoundException e ) {
            throw new RuntimeException(e);
        }
    }


    public MicroscopeConfig getMicroscopeConfig(String microscope) {
        return microscopeConfigs.get(microscope);
    }


    public List<Microscope> listAllMicroscopes() {
        return microscopeRepository.findAll();
    }

    public List<Microscope> getMicroscopeByAdmin(String id) {
        return microscopeRepository.findByManagedBy(id);
    }

    public Microscope findById(String id) {
        return microscopeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Microscope not found: " + id));
    }

    public Microscope save(Microscope microscope) {
        return microscopeRepository.save(microscope);
    }
}
