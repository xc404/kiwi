package com.kiwi.bpmn.component.slurm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 以 {@link SlurmJob#getStatus()} 与 {@link SlurmJob#getTerminalReportLocked()} 区分是否仍待触发终态上报：
 * 仅 {@link SlurmJobStatus#RUNNING} 且未持终态上报锁时由 sacct 驱动上报。
 */
@Slf4j
public class SlurmJobTracker implements InitializingBean, DisposableBean {

    private final SlurmProperties slurmProperties;
    private final SlurmJobCompleteProcessor slurmJobCompleteProcessor;
    private final SlurmJobRepository slurmJobRepository;

    private volatile ScheduledExecutorService scheduler;

    public SlurmJobTracker(
            SlurmProperties slurmProperties,
            SlurmJobCompleteProcessor slurmJobCompleteProcessor,
            SlurmJobRepository slurmJobRepository) {
        this.slurmProperties = slurmProperties;
        this.slurmJobCompleteProcessor = slurmJobCompleteProcessor;
        this.slurmJobRepository = slurmJobRepository;
    }

    /**
     * 将已提交作业写入库，供 sacct 轮询直至终态。
     */
    public void saveTrackedJob(SlurmJob job) {
        SlurmProperties.Sacct sacct = slurmProperties.getSacct();
        if (sacct == null || !sacct.isEnabled()) {
            return;
        }
        if (job == null
                || job.getJobId() == null
                || job.getExternalTaskId() == null
                || job.getWorkerId() == null) {
            return;
        }
        job.setId(job.getJobId());
        if (job.getCreatedTime() == null) {
            job.setCreatedTime(new Date());
        }
        if (job.getStatus() == null) {
            job.setStatus(SlurmJobStatus.RUNNING);
        }
        slurmJobRepository.save(job);
        log.debug("Slurm job persisted for sacct tracking: jobId={}, externalTaskId={}", job.getJobId(), job.getExternalTaskId());
    }

    @Override
    public void afterPropertiesSet() {
        SlurmProperties.Sacct sacct = slurmProperties.getSacct();
        if (sacct == null || !sacct.isEnabled()) {
            log.info("Slurm sacct polling disabled (kiwi.bpm.slurm.sacct.enabled=false)");
            return;
        }
        long ms = Math.max(5_000L, sacct.getPollIntervalMs());
        ScheduledExecutorService exec =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "slurm-sacct-poll");
                    t.setDaemon(true);
                    return t;
                });
        this.scheduler = exec;
        exec.scheduleAtFixedRate(this::safeTick, ms, ms, TimeUnit.MILLISECONDS);
        log.info("Slurm sacct polling started: intervalMs={}", ms);
    }

    private void safeTick() {
        try {
            tickInternal();
        } catch (Throwable t) {
            log.warn("Slurm sacct poll tick failed: {}", t.toString());
        }
    }

    private void tickInternal() {
        long now = System.currentTimeMillis();
        long maxAge = Math.max(60_000L, slurmProperties.getSacct().getMaxTrackDurationMs());
        long deadline = now - maxAge;

        List<SlurmJob> timedOut = findTimedOutJobs(deadline);
        for (SlurmJob j : timedOut) {
            applyTimeout(j);
        }

        List<SlurmJob> active = findActiveTrackedJobs(now, maxAge);
        if (active.isEmpty()) {
            return;
        }
        Map<String, SlurmJob> byJobId =
                active.stream().collect(Collectors.toMap(SlurmJob::getJobId, j -> j, (a, b) -> a, LinkedHashMap::new));
        List<String> ids = new ArrayList<>(byJobId.keySet());
        int chunk = Math.max(1, slurmProperties.getSacct().getMaxJobsPerSacctCall());
        for (int i = 0; i < ids.size(); i += chunk) {
            List<String> batch = new ArrayList<>(ids.subList(i, Math.min(i + chunk, ids.size())));
            String raw;
            try {
                raw =
                        SlurmSacctClient.queryJobBatch(
                                batch,
                                slurmProperties.getSacct().getExecutable(),
                                slurmProperties.getSacct().getExtraArgs());
            } catch (Exception ex) {
                log.warn("sacct batch query failed for {} jobs: {}", batch.size(), ex.toString());
                continue;
            }
            List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
            applySacctLinesToTrackedJobs(batch, lines, byJobId);
        }
    }

    private List<SlurmJob> findTimedOutJobs(long deadline) {
        Date deadlineDate = new Date(deadline);
        return slurmJobRepository.findByStatusAndCreatedTimeBefore(SlurmJobStatus.RUNNING, deadlineDate);
    }

    private List<SlurmJob> findActiveTrackedJobs(long now, long maxAge) {
        long cutoff = now - maxAge;
        Date cutoffDate = new Date(cutoff);
        return slurmJobRepository.findByStatusAndCreatedTimeGreaterThanEqual(SlurmJobStatus.RUNNING, cutoffDate);
    }

    private void applySacctLinesToTrackedJobs(
            List<String> batch, List<SlurmSacctParser.SacctLine> lines, Map<String, SlurmJob> byJobId) {
        for (String jobId : batch) {
            if (!byJobId.containsKey(jobId)) {
                continue;
            }
            SlurmJob reload =
                    slurmJobRepository
                            .findById(jobId)
                            .filter(SlurmJobTracker::readyForSacctTerminalHandling)
                            .orElse(null);
            if (reload == null) {
                continue;
            }
            SlurmSacctParser.SacctResolution res = SlurmSacctParser.resolveForJob(lines, jobId);
            if (!res.terminal()) {
                continue;
            }
            int ec = res.success() ? 0 : res.commandExitCode();
            SlurmResult parsed =
                    new SlurmResult(ec, reload.getExternalTaskId(), reload.getWorkerId(), reload.getJobName());
            String diag =
                    "sacct: jobId="
                            + jobId
                            + ", state="
                            + (res.slurmState() != null ? res.slurmState() : "")
                            + ", exit="
                            + res.commandExitCode();
            boolean ok = slurmJobCompleteProcessor.processParsedSlurmTerminal(parsed, diag, null);
            if (ok) {
                markTerminated(reload);
            }
        }
    }

    private void markTerminated(SlurmJob job) {
        if (job == null || job.getJobId() == null) {
            return;
        }
        try {
            long n = slurmJobRepository.markTerminatedIfStillActive(job.getJobId());
            if (n == 0) {
                log.debug("markTerminated: jobId={} already TERMINATED or not active", job.getJobId());
            }
        } catch (Exception e) {
            log.debug("Failed to persist TERMINATED for jobId={}: {}", job.getJobId(), e.toString());
        }
    }

    /**
     * 测试用：对库中在册作业应用给定的 sacct 标准输出（不真实调用 sacct）。
     */
    void applySacctStdoutForTests(String sacctStdout) {
        long now = System.currentTimeMillis();
        long maxAge = Math.max(60_000L, slurmProperties.getSacct().getMaxTrackDurationMs());
        List<SlurmJob> active = findActiveTrackedJobs(now, maxAge);
        if (active.isEmpty()) {
            return;
        }
        Map<String, SlurmJob> byJobId =
                active.stream().collect(Collectors.toMap(SlurmJob::getJobId, j -> j, (a, b) -> a, LinkedHashMap::new));
        List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(sacctStdout);
        int chunk = Math.max(1, slurmProperties.getSacct().getMaxJobsPerSacctCall());
        List<String> ids = new ArrayList<>(byJobId.keySet());
        for (int i = 0; i < ids.size(); i += chunk) {
            List<String> batch = new ArrayList<>(ids.subList(i, Math.min(i + chunk, ids.size())));
            if (!batch.isEmpty()) {
                applySacctLinesToTrackedJobs(batch, lines, byJobId);
            }
        }
    }

    private void applyTimeout(SlurmJob job) {
        SlurmJob reload =
                slurmJobRepository
                        .findById(job.getJobId())
                        .filter(SlurmJobTracker::readyForSacctTerminalHandling)
                        .orElse(null);
        if (reload == null) {
            return;
        }
        SlurmResult parsed =
                new SlurmResult(1, reload.getExternalTaskId(), reload.getWorkerId(), reload.getJobName());
        String diag = "sacct-tracker: maxTrackDurationMs exceeded for jobId=" + reload.getJobId();
        boolean ok = slurmJobCompleteProcessor.processParsedSlurmTerminal(parsed, diag, null);
        if (ok) {
            markTerminated(reload);
        }
    }

    /** sacct 已判终态、可安全调用终态上报（未持 Mongo 终态锁） */
    private static boolean readyForSacctTerminalHandling(SlurmJob j) {
        return j.getStatus() == SlurmJobStatus.RUNNING && !Boolean.TRUE.equals(j.getTerminalReportLocked());
    }

    @Override
    public void destroy() {
        ScheduledExecutorService exec = this.scheduler;
        if (exec != null) {
            exec.shutdownNow();
        }
    }
}
