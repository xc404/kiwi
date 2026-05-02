package com.kiwi.bpmn.component.slurm;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Date;
import java.util.List;

public interface SlurmJobRepository extends MongoRepository<SlurmJob, String> {

    List<SlurmJob> findByExternalTaskIdAndStatus(String externalTaskId, SlurmJobStatus status);

    boolean existsByExternalTaskId(String externalTaskId);

    List<SlurmJob> findByStatusAndCreatedTimeBefore(SlurmJobStatus status, Date deadline);

    List<SlurmJob> findByStatusAndCreatedTimeGreaterThanEqual(SlurmJobStatus status, Date cutoff);

    List<SlurmJob> findByWorkerIdAndStatusAndCreatedTimeGreaterThanEqual(
            String workerId, SlurmJobStatus status, Date cutoff);

    List<SlurmJob> findByWorkerIdAndStatusAndCreatedTimeBefore(String workerId, SlurmJobStatus status, Date deadline);

    /**
     * 乐观并发：仅当仍为 {@link SlurmJobStatus#RUNNING} 时原子改为 {@link SlurmJobStatus#REPORTING_TERMINAL}，
     * 表示本线程独占终态上报；返回修改行数（0 表示未抢到）。
     */
    @Query("{ '_id': ?0, 'status': 'RUNNING' }")
    @Update("{ '$set': { 'status': 'REPORTING_TERMINAL' } }")
    long claimTerminalReportingLock(String jobId);

    /** 终态上报失败时恢复为 {@link SlurmJobStatus#RUNNING}，便于 sacct 再次尝试。 */
    @Query("{ '_id': ?0, 'status': 'REPORTING_TERMINAL' }")
    @Update("{ '$set': { 'status': 'RUNNING' } }")
    long releaseTerminalReportingLock(String jobId);

    /** 终态上报成功后由 {@link SlurmJobCompleteProcessor} 写入 {@link SlurmJobStatus#TERMINATED}。 */
    @Query("{ '_id': ?0, 'status': 'REPORTING_TERMINAL' }")
    @Update("{ '$set': { 'status': 'TERMINATED' } }")
    long finalizeTerminalAfterReport(String jobId);

    /**
     * 兼容路径：终态已成功但文档仍为 RUNNING / REPORTING_TERMINAL 时置为 TERMINATED（幂等，已 TERMINATED 则 0）。
     */
    @Query("{ '_id': ?0, 'status': { '$in': ['RUNNING', 'REPORTING_TERMINAL'] } }")
    @Update("{ '$set': { 'status': 'TERMINATED' } }")
    long markTerminatedIfStillActive(String jobId);
}
