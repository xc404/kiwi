package com.kiwi.project.bpm.utils;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BpmComponentDeploymentSignatureTest {

    @Test
    void sameMetadataProducesSameSignature() {
        BpmComponent a = sampleComponent();
        BpmComponent b = sampleComponent();
        assertEquals(BpmComponentDeploymentSignature.compute(a), BpmComponentDeploymentSignature.compute(b));
    }

    @Test
    void versionChangeChangesSignature() {
        BpmComponent a = sampleComponent();
        BpmComponent b = sampleComponent();
        b.setVersion("2");
        assertNotEquals(BpmComponentDeploymentSignature.compute(a), BpmComponentDeploymentSignature.compute(b));
    }

    @Test
    void parameterChangeChangesSignature() {
        BpmComponent a = sampleComponent();
        BpmComponent b = sampleComponent();
        BpmComponentParameter p = new BpmComponentParameter();
        p.setKey("extra");
        b.setInputParameters(List.of(p));
        assertNotEquals(BpmComponentDeploymentSignature.compute(a), BpmComponentDeploymentSignature.compute(b));
    }

    private static BpmComponent sampleComponent() {
        BpmComponent c = new BpmComponent();
        c.setParentId(null);
        c.setKey("k1");
        c.setSource("classpath");
        c.setName("n");
        c.setDescription("d");
        c.setGroup("g");
        c.setType(BpmComponent.Type.SpringBean);
        c.setVersion("1");
        c.setInputParameters(null);
        c.setOutputParameters(null);
        return c;
    }
}
