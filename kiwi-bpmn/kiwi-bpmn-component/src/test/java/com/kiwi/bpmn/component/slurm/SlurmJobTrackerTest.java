package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * sacct 在无 .flag 时通过 Mongo 中 {@link SlurmJob} 与 {@link SlurmJobCompleteProcessor#processParsedSlurmTerminal} 完成终态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlurmJobTrackerTest {

    @Mock
    private SlurmJobCompleteProcessor slurmJobCompleteProcessor;

    @Mock
    private SlurmJobRepository slurmJobRepository;

    private final List<SlurmJob> persisted = new CopyOnWriteArrayList<>();

    private SlurmProperties slurmProperties;
    private SlurmJobTracker tracker;

    @BeforeEach
    void setUp() {
        slurmProperties = new SlurmProperties();
        slurmProperties.getSacct().setEnabled(true);
        slurmProperties.getSacct().setPollIntervalMs(60_000L);
        slurmProperties.getSacct().setMaxTrackDurationMs(3600_000L);
        tracker = new SlurmJobTracker(slurmProperties, slurmJobCompleteProcessor, slurmJobRepository);
        persisted.clear();
        when(slurmJobRepository.save(ArgumentMatchers.any())).thenAnswer(inv -> {
            SlurmJob j = inv.getArgument(0);
            persisted.removeIf(x -> x.getJobId().equals(j.getJobId()));
            persisted.add(j);
            return j;
        });
        when(slurmJobRepository.findById(ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    String id = inv.getArgument(0);
                    return persisted.stream().filter(x -> id.equals(x.getJobId())).findFirst();
                });
        when(slurmJobRepository.findByStatusAndCreatedTimeGreaterThanEqual(
                        eq(SlurmJobStatus.RUNNING), ArgumentMatchers.any(Date.class)))
                .thenAnswer(
                        inv -> persisted.stream()
                                .filter(
                                        j -> j.getStatus() == null || j.getStatus() == SlurmJobStatus.RUNNING)
                                .collect(Collectors.toCollection(ArrayList::new)));
        when(slurmJobRepository.findByStatusAndCreatedTimeBefore(eq(SlurmJobStatus.RUNNING), ArgumentMatchers.any(Date.class)))
                .thenReturn(List.of());
        when(slurmJobRepository.markTerminatedIfStillActive(anyString()))
                .thenAnswer(
                        inv -> {
                            String id = inv.getArgument(0);
                            return persisted.stream()
                                    .filter(x -> id.equals(x.getJobId()))
                                    .filter(
                                            x -> x.getStatus() == SlurmJobStatus.RUNNING
                                                    && !Boolean.TRUE.equals(x.getTerminalReportLocked()))
                                    .peek(x -> x.setStatus(SlurmJobStatus.TERMINATED))
                                    .count()
                                    > 0
                                    ? 1L
                                    : 0L;
                        });
    }

    @Test
    void applySacctStdout_cancelled_invokesProcessParsed() {
        SlurmJob job = trackedJob("999", "ext-1", "worker-1", "jn");
        tracker.saveTrackedJob(job);
        when(slurmJobCompleteProcessor.processParsedSlurmTerminal(
                        ArgumentMatchers.any(SlurmResult.class), ArgumentMatchers.any(), isNull()))
                .thenReturn(true);

        tracker.applySacctStdoutForTests("999|CANCELLED|0:0\n");

        ArgumentCaptor<SlurmResult> cap = ArgumentCaptor.forClass(SlurmResult.class);
        verify(slurmJobCompleteProcessor)
                .processParsedSlurmTerminal(cap.capture(), ArgumentMatchers.any(), isNull());
        assertEquals(143, cap.getValue().getCommandExitCode());
        assertEquals("ext-1", cap.getValue().getTaskId());
        assertEquals("worker-1", cap.getValue().getWorkerId());
        assertEquals("jn", cap.getValue().getJobName());
    }

    @Test
    void applySacctStdout_whenReloadNotTracking_skipsHandler() {
        SlurmJob job = trackedJob("1000", "ext-2", "w", "j");
        tracker.saveTrackedJob(job);
        SlurmJob terminalView = trackedJob("1000", "ext-2", "w", "j");
        terminalView.setStatus(SlurmJobStatus.TERMINATED);
        when(slurmJobRepository.findById(eq("1000"))).thenReturn(Optional.of(terminalView));

        tracker.applySacctStdoutForTests("1000|CANCELLED|0:0\n");

        verify(slurmJobCompleteProcessor, never())
                .processParsedSlurmTerminal(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void saveTrackedJob_whenSacctDisabled_noPersist() {
        slurmProperties.getSacct().setEnabled(false);
        tracker.saveTrackedJob(trackedJob("1", "e", "w", "j"));
        verify(slurmJobRepository, never()).save(ArgumentMatchers.any());
    }

    private static SlurmJob trackedJob(String jobId, String extId, String workerId, String jobName) {
        SlurmJob j = new SlurmJob();
        j.setJobId(jobId);
        j.setId(jobId);
        j.setExternalTaskId(extId);
        j.setWorkerId(workerId);
        j.setJobName(jobName);
        j.setCreatedTime(new Date());
        return j;
    }
}
