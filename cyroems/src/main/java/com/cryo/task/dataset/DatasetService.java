package com.cryo.task.dataset;

import com.cryo.model.Microscope;
import lombok.Getter;
import lombok.Setter;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatasetService implements InitializingBean
{
    @Value("${app.dataset.config:classpath:dataset-config.json}")
    private String configJson = "classpath:dataset-config.json";
    private Map<String, DataSetConfig> dataSetConfigs = new HashMap<>();

    @Getter
    @Setter
    @Value("${app.dataset.permission.enabled:false}")
    private boolean permissionEnabled = false;

    @Override
    public void afterPropertiesSet() throws Exception {
        File file = ResourceUtils.getFile(configJson);
        Map<String, DataSetConfig> dataSetConfigMap = JsonUtil.readValue(new FileInputStream(file), JsonUtil.getParametricType(Map.class, String.class, DataSetConfig.class));
        dataSetConfigMap.forEach((key, value) -> {

            value.setMicroscope(key);
            dataSetConfigs.put(key, value);
        });
    }

    public DataSetConfig getDataSetConfig(String microscope) {
        return dataSetConfigs.get(microscope);
    }

    public List<DataSetConfig> getDataSetConfigs() {
        return List.copyOf(this.dataSetConfigs.values());
    }


}
