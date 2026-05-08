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
 * 以 {@link SlurmJob#getStatus()} 与 {@link SlurmJob#getCompleteProcessLock()} 区分是否仍待触发终态上报：
 * 仅 {@link SlurmJobStatus#Running} 且未持终态上报锁时由 sacct 驱动上报。
 */
@Slf4j
public class SlurmJobTracker implements InitializingBean, DisposableBean
{

    private final SlurmProperties slurmProperties;
    private final SlurmJobCompleteProcessor slurmJobCompleteProcessor;
    private final SlurmJobRepository slurmJobRepository;

    private volatile ScheduledExecutorService scheduler;
    private SlurmProperties.Sacct sacct;

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
        if( job == null
                || job.getJobId() == null
                || job.getExternalTaskId() == null ) {
            return;
        }
        job.setId(job.getJobId());
        if( job.getCreatedTime() == null ) {
            job.setCreatedTime(new Date());
        }
        if( job.getStatus() == null ) {
            job.setStatus(SlurmJobStatus.Running);
        }
        slurmJobRepository.save(job);
        log.debug("Slurm job persisted for sacct tracking: jobId={}, externalTaskId={}", job.getJobId(), job.getExternalTaskId());
    }

    @Override
    public void afterPropertiesSet() {
        this.sacct = this.slurmProperties.getSacct();
        if( sacct == null ) {
            log.info("Slurm sacct polling disabled )");
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
        } catch( Throwable t ) {
            log.warn("Slurm sacct poll tick failed: {}", t.toString());
        }
    }

    private void tickInternal() {
        long now = System.currentTimeMillis();

        List<SlurmJob> timedOut = findTimedOutJobs(now);
        for( SlurmJob j : timedOut ) {
            applyTimeout(j);
        }

        List<SlurmJob> active = findActiveTrackedJobs(now);
        if( active.isEmpty() ) {
            return;
        }
        Map<String, SlurmJob> byJobId =
                active.stream().collect(Collectors.toMap(SlurmJob::getJobId, j -> j, (a, b) -> a, LinkedHashMap::new));
        List<String> ids = new ArrayList<>(byJobId.keySet());
        int chunk = Math.max(1, this.sacct.getMaxJobsPerSacctCall());
        for( int i = 0; i < ids.size(); i += chunk ) {
            List<String> batch = new ArrayList<>(ids.subList(i, Math.min(i + chunk, ids.size())));
            String raw;
            try {
                raw =
                        SlurmSacctClient.queryJobBatch(
                                batch,
                                this.sacct.getExecutable(),
                                this.sacct.getExtraArgs());
            } catch( Exception ex ) {
                log.warn("sacct batch query failed for {} jobs: {}", batch.size(), ex.toString());
                continue;
            }
            List<SlurmSacctParser.SacctLine> lines = SlurmSacctParser.parseLines(raw);
            applySacctLinesToTrackedJobs(batch, lines, byJobId);
        }
    }

    private List<SlurmJob> findTimedOutJobs(long nowMs) {
        return slurmJobRepository.findByStatusAndExpirationBefore(SlurmJobStatus.Running, new Date(nowMs));
    }

    private List<SlurmJob> findActiveTrackedJobs(long nowMs) {
        return slurmJobRepository.findByStatusAndExpirationGreaterThanEqual(
                SlurmJobStatus.Running, new Date(nowMs));
    }

    private void applySacctLinesToTrackedJobs(
            List<String> batch, List<SlurmSacctParser.SacctLine> lines, Map<String, SlurmJob> byJobId) {
        for( String jobId : batch ) {
            if( !byJobId.containsKey(jobId) ) {
                continue;
            }
            SlurmJob slurmJob = byJobId.get(jobId);
            SlurmSacctParser.SacctResolution res = SlurmSacctParser.resolveForJob(lines, jobId);
            if( !res.hasFinalState() ) {
                continue;
            }
            int ec = res.success() ? 0 : res.commandExitCode();
            SlurmJob parsed = slurmJob.clone();
            SlurmJobResult jobResult = new SlurmJobResult();
            jobResult.setExitCode(ec);
            jobResult.setSlurmState(res.slurmState());
            slurmJobCompleteProcessor.complete(parsed, jobResult);
        }
    }



    private void applyTimeout(SlurmJob job) {
        SlurmJob parsed = job.clone();
        SlurmJobResult jobResult = new SlurmJobResult();
        jobResult.setExitCode(1);
        jobResult.setSlurmState(SlurmJobResult.STATE_TRACKING_EXPIRED);
        jobResult.setErrorMessage(trackingExpiredUserMessage(job));
        log.warn(
                "Slurm sacct tracking window expired (Mongo expiration reached before terminal state): jobId={}, expiration={}",
                job.getJobId(),
                job.getExpiration());
        slurmJobCompleteProcessor.complete(parsed, jobResult);
    }

    /**
     * 与 {@link SlurmJobResult#STATE_TRACKING_EXPIRED} 配套的人类可读说明（写入 Mongo / Camunda 失败原因）。
     */
    private String trackingExpiredUserMessage(SlurmJob job) {
        String jobId = job.getJobId() != null ? job.getJobId() : "";
        String exp =
                job.getExpiration() != null ? job.getExpiration().toString() : "unknown";
        return "本系统 sacct/Mongo 跟踪截止时刻已到（expiration="
                + exp
                + "），仍未观测到 Slurm 作业终态（jobId="
                + jobId
                + "）；集群上作业可能仍在运行。";
    }


    @Override
    public void destroy() {
        ScheduledExecutorService exec = this.scheduler;
        if( exec != null ) {
            exec.shutdownNow();
        }
    }
}
