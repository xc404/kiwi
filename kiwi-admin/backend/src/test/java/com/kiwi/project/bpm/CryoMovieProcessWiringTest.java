package com.kiwi.project.bpm;

import com.kiwi.cryoems.bpm.movie.delegate.MovieCtfEstimationJavaDelegate;
import com.kiwi.cryoems.bpm.movie.delegate.MovieHeaderJavaDelegate;
import com.kiwi.cryoems.bpm.movie.delegate.MovieMotionCorrectionJavaDelegate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoMovieProcessWiringTest {

    @Test
    void shouldDiscoverDelegateBeansAndBpmnBindings() throws Exception {
        Set<String> delegateNamesInBpmn = readDelegateExpressions("bpm/samples/cryo-movie-minimal.bpmn");

        assertTrue(delegateNamesInBpmn.contains("movieHeaderJavaDelegate"));
        assertTrue(delegateNamesInBpmn.contains("movieMotionCorrectionJavaDelegate"));
        assertTrue(delegateNamesInBpmn.contains("movieCtfEstimationJavaDelegate"));

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.scan("com.kiwi.cryoems.bpm.movie.delegate");
            ctx.refresh();

            assertNotNull(ctx.getBean("movieHeaderJavaDelegate", MovieHeaderJavaDelegate.class));
            assertNotNull(ctx.getBean("movieMotionCorrectionJavaDelegate", MovieMotionCorrectionJavaDelegate.class));
            assertNotNull(ctx.getBean("movieCtfEstimationJavaDelegate", MovieCtfEstimationJavaDelegate.class));
        }

        assertEquals("movieHeaderJavaDelegate", beanNameOf(MovieHeaderJavaDelegate.class));
        assertEquals("movieMotionCorrectionJavaDelegate", beanNameOf(MovieMotionCorrectionJavaDelegate.class));
        assertEquals("movieCtfEstimationJavaDelegate", beanNameOf(MovieCtfEstimationJavaDelegate.class));
    }

    private static String beanNameOf(Class<?> type) {
        Component c = type.getAnnotation(Component.class);
        assertNotNull(c, () -> type.getName() + " 缺少 @Component");
        assertTrue(c.value() != null && !c.value().isBlank(), () -> type.getName() + " @Component 未声明 bean 名");
        return c.value();
    }

    private static Set<String> readDelegateExpressions(String classpathBpmn) throws Exception {
        Set<String> names = new HashSet<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        try (InputStream in = new ClassPathResource(classpathBpmn).getInputStream()) {
            var doc = factory.newDocumentBuilder().parse(in);
            var tasks = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "serviceTask");
            for (int i = 0; i < tasks.getLength(); i++) {
                var node = tasks.item(i);
                var attr = node.getAttributes().getNamedItemNS("http://camunda.org/schema/1.0/bpmn", "delegateExpression");
                if (attr == null) {
                    continue;
                }
                String raw = attr.getNodeValue();
                if (raw != null && raw.startsWith("${") && raw.endsWith("}")) {
                    names.add(raw.substring(2, raw.length() - 1));
                }
            }
        }
        return names;
    }
}
