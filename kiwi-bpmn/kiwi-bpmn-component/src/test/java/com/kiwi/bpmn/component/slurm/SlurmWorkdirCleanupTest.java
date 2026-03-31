package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlurmWorkdirCleanupTest {

    @Test
    void matchesSuffix_longestFirst() {
        assertTrue(SlurmWorkdirCleanup.matchesSuffix("a.flag.done", SlurmWorkdirCleanup.DEFAULT_SUFFIXES));
        assertTrue(SlurmWorkdirCleanup.matchesSuffix("a.flag", SlurmWorkdirCleanup.DEFAULT_SUFFIXES));
        assertFalse(SlurmWorkdirCleanup.matchesSuffix("a.txt", SlurmWorkdirCleanup.DEFAULT_SUFFIXES));
    }

    @Test
    void cleanup_deletesOnlyExpiredMatchingSuffix(@TempDir Path tempDir) throws IOException {
        File dir = tempDir.toFile();
        File old = new File(dir, "old.sbatch");
        assertTrue(old.createNewFile());
        long oldTime = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000;
        assertTrue(old.setLastModified(oldTime));

        File young = new File(dir, "young.sbatch");
        assertTrue(young.createNewFile());

        SlurmProperties.Cleanup cfg = new SlurmProperties.Cleanup();
        cfg.setRetentionMs(24L * 60 * 60 * 1000);
        cfg.setMinAgeMs(1000);

        int deleted = SlurmWorkdirCleanup.run(dir, cfg);
        assertEquals(1, deleted);
        assertFalse(old.exists());
        assertTrue(young.exists());
    }

    @Test
    void cleanup_respectsMinAge(@TempDir Path tempDir) throws IOException {
        File dir = tempDir.toFile();
        File recent = new File(dir, "recent.sbatch");
        assertTrue(recent.createNewFile());

        SlurmProperties.Cleanup cfg = new SlurmProperties.Cleanup();
        cfg.setRetentionMs(0);
        cfg.setMinAgeMs(60_000);

        int deleted = SlurmWorkdirCleanup.run(dir, cfg);
        assertEquals(0, deleted);
        assertTrue(recent.exists());
    }

    @Test
    void cleanup_twoExpiredFiles_bothRemoved(@TempDir Path tempDir) throws IOException {
        File dir = tempDir.toFile();
        long t = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000;
        File a = new File(dir, "a.sbatch");
        File b = new File(dir, "b.out");
        assertTrue(a.createNewFile());
        assertTrue(b.createNewFile());
        assertTrue(a.setLastModified(t));
        assertTrue(b.setLastModified(t));

        SlurmProperties.Cleanup cfg = new SlurmProperties.Cleanup();
        cfg.setRetentionMs(60_000);
        cfg.setMinAgeMs(1000);

        int deleted = SlurmWorkdirCleanup.run(dir, cfg);
        assertEquals(2, deleted);
        assertFalse(a.exists());
        assertFalse(b.exists());
    }
}
