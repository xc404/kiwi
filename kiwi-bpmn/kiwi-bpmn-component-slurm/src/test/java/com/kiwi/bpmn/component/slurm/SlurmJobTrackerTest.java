package com.kiwi.bpmn.component.slurm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlurmJobTrackerTest {

    @Mock
    private SlurmProperties slurmProperties;

    @Mock
    private SlurmJobCompleteProcessor slurmJobCompleteProcessor;

    @Mock
    private SlurmJobRepository slurmJobRepository;

    @Test
    void saveTrackedJob_persistsWhenExternalTaskIdPresent() {
        SlurmJobTracker tracker =
                new SlurmJobTracker(slurmProperties, slurmJobCompleteProcessor, slurmJobRepository);
        SlurmJob job = new SlurmJob();
        job.setJobId("25493593");
        job.setExternalTaskId("ext-1");
        job.setProcessInstanceId("pi-1");
        job.setActivityId("Activity_095vcnd");

        boolean saved = tracker.saveTrackedJob(job);

        ArgumentCaptor<SlurmJob> captor = ArgumentCaptor.forClass(SlurmJob.class);
        verify(slurmJobRepository).save(captor.capture());
        SlurmJob persisted = captor.getValue();
        assertEquals("25493593", persisted.getId());
        assertEquals(SlurmJobStatus.Running, persisted.getStatus());
        assertEquals(true, saved);
    }

    @Test
    void saveTrackedJob_skipsWhenExternalTaskIdBlank() {
        SlurmJobTracker tracker =
                new SlurmJobTracker(slurmProperties, slurmJobCompleteProcessor, slurmJobRepository);
        SlurmJob job = new SlurmJob();
        job.setJobId("25493593");

        boolean saved = tracker.saveTrackedJob(job);

        verify(slurmJobRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertEquals(false, saved);
    }
}
