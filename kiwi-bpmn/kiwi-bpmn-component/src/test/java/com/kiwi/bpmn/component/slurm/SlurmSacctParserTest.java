package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlurmSacctParserTest {

    @Test
    void parseLines_parsable2Pipe() {
        String raw = "12345.batch|COMPLETED|0:0\n12345.extern|COMPLETED|0:0\n";
        List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
        assertEquals(2, lines.size());
        assertEquals("12345.batch", lines.get(0).jobId());
        assertEquals("COMPLETED", lines.get(0).state());
        assertEquals("0:0", lines.get(0).exitCodeField());
    }

    @Test
    void resolveForJob_cancelledWithoutBatchFlagFile() {
        String raw = "12345|CANCELLED|0:0\n";
        List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
        SlurmSacctParser.SacctResolution r = SlurmSacctParser.resolveForJob(lines, "12345");
        assertTrue(r.terminal());
        assertFalse(r.success());
        assertEquals(143, r.commandExitCode());
        assertEquals("CANCELLED", r.slurmState());
    }

    @Test
    void resolveForJob_batchCompletedSuccess() {
        String raw = "12345.batch|COMPLETED|0:0\n";
        List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
        SlurmSacctParser.SacctResolution r = SlurmSacctParser.resolveForJob(lines, "12345");
        assertTrue(r.terminal());
        assertTrue(r.success());
        assertEquals(0, r.commandExitCode());
    }

    @Test
    void resolveForJob_runningNotTerminal() {
        String raw = "12345.batch|RUNNING||\n";
        List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
        SlurmSacctParser.SacctResolution r = SlurmSacctParser.resolveForJob(lines, "12345");
        assertFalse(r.terminal());
    }

    @Test
    void resolveForJob_failedUsesExitCode() {
        String raw = "12345.batch|FAILED|1:0\n";
        List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
        SlurmSacctParser.SacctResolution r = SlurmSacctParser.resolveForJob(lines, "12345");
        assertTrue(r.terminal());
        assertFalse(r.success());
        assertEquals(1, r.commandExitCode());
    }
}
