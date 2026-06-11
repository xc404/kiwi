package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 application-local.yml 中扁平 KIWI_BPM_* 键是否能绑定到 {@link SlurmProperties}。
 */
class SlurmPropertiesBindingTest {

    @Test
    void flatLocalYamlKey_doesNotBindToWorkDirectory_withoutNestedProperty() {
        Map<String, Object> props = new HashMap<>();
        props.put("KIWI_BPM_SLURM_WORK_DIRECTORY", "/home/chaox/slurm");

        SlurmProperties bound = bind(props);
        assertNull(
                bound.getWorkDirectory(),
                "扁平 KIWI_BPM_SLURM_WORK_DIRECTORY 不会自动绑定到 kiwi.bpm.slurm.work-directory");
    }

    @Test
    void nestedProperty_bindsWorkDirectory() {
        Map<String, Object> props = new HashMap<>();
        props.put("kiwi.bpm.slurm.work-directory", "/home/chaox/slurm");

        SlurmProperties bound = bind(props);
        assertEquals("/home/chaox/slurm", bound.getWorkDirectory());
    }

    @Test
    void placeholderStyleProperty_bindsWorkDirectory() {
        Map<String, Object> props = new HashMap<>();
        props.put("KIWI_BPM_SLURM_WORK_DIRECTORY", "/home/chaox/slurm");
        props.put("kiwi.bpm.slurm.work-directory", "${KIWI_BPM_SLURM_WORK_DIRECTORY:}");

        SlurmProperties bound = bindWithPlaceholderResolution(props);
        assertEquals("/home/chaox/slurm", bound.getWorkDirectory());
    }

    private static SlurmProperties bind(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        return Binder.get(env)
                .bind("kiwi.bpm.slurm", SlurmProperties.class)
                .orElseGet(SlurmProperties::new);
    }

    private static SlurmProperties bindWithPlaceholderResolution(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", props));
        String resolved = env.getProperty("kiwi.bpm.slurm.work-directory");
        SlurmProperties p = new SlurmProperties();
        p.setWorkDirectory(resolved);
        return p;
    }
}
