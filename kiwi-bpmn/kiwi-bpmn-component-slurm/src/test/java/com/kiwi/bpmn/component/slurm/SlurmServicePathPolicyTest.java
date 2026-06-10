package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlurmServicePathPolicyTest {

    @Test
    void resolve_relativeFile_staysUnderWorkDir(@TempDir Path work) throws Exception {
        SlurmProperties p = new SlurmProperties();
        p.setWorkDirectory(work.toString());
        SlurmService svc = new SlurmService(p);
        svc.afterPropertiesSet();

        String out = svc.resolvePathUnderShellDir("job.out");
        assertTrue(out.replace('\\', '/').contains(work.toString().replace('\\', '/')));
    }

    @Test
    void resolve_traversal_throws(@TempDir Path work) throws Exception {
        SlurmProperties p = new SlurmProperties();
        p.setWorkDirectory(work.toString());
        SlurmService svc = new SlurmService(p);
        svc.afterPropertiesSet();

        assertThrows(
                IllegalArgumentException.class,
                () -> svc.resolvePathUnderShellDir(Path.of("..", "..", "etc", "passwd").toString()));
    }

    @Test
    void resolve_absoluteOutsideWorkDir_throws(@TempDir Path work) throws Exception {
        SlurmProperties p = new SlurmProperties();
        p.setWorkDirectory(work.toString());
        SlurmService svc = new SlurmService(p);
        svc.afterPropertiesSet();

        Path outside = work.getParent() == null ? work : work.getParent().resolve("slurm-path-test-outside");
        java.nio.file.Files.createDirectories(outside);
        try {
            Path bad = outside.resolve("x.err");
            assertThrows(IllegalArgumentException.class, () -> svc.resolvePathUnderShellDir(bad.toString()));
        } finally {
            java.nio.file.Files.deleteIfExists(outside.resolve("x.err"));
            java.nio.file.Files.deleteIfExists(outside);
        }
    }

    @Test
    void isResolvedPathUnderWorkDirectory_acceptsInTree(@TempDir Path work) throws Exception {
        SlurmProperties p = new SlurmProperties();
        p.setWorkDirectory(work.toString());
        SlurmService svc = new SlurmService(p);
        svc.afterPropertiesSet();

        assertTrue(svc.isResolvedPathUnderWorkDirectory("a.err"));
    }

    @Test
    void sanitize_sbachtDirective_embeddedNewlinesBecomeSpaces() {
        SbatchConfig c = new SbatchConfig();
        c.setPartition("cpu\nINJECT");
        assertTrue(c.toSbatchCmd().contains("--partition=cpu INJECT"));
        assertFalse(c.toSbatchCmd().contains("partition=cpu\n"));
    }
}
