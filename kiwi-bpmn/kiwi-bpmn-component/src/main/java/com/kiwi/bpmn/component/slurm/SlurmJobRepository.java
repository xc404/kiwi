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

    /**
     * 乐观并发：仅当 {@link SlurmJobStatus#RUNNING} 且未持锁（{@link SlurmJob#getTerminalReportLocked()} 非 true）时
     * 原子置 {@code terminalReportLocked=true}；返回修改行数（0 表示未抢到）。
     */
    @Query("{ '_id': ?0, 'status': 'RUNNING', 'terminalReportLocked': { '$ne': true } }")
    @Update("{ '$set': { 'terminalReportLocked': true } }")
    long claimTerminalReportingLock(String jobId);

    /** 终态上报失败时释放锁；{@link SlurmJobStatus} 保持 {@link SlurmJobStatus#RUNNING}。 */
    @Query("{ '_id': ?0, 'terminalReportLocked': true }")
    @Update("{ '$set': { 'terminalReportLocked': false } }")
    long releaseTerminalReportingLock(String jobId);

    /** 终态上报成功后写入 {@link SlurmJobStatus#TERMINATED}、清锁，并记录退出码与错误说明（成功时 {@code errorMessage} 可为 null）。 */
    @Query("{ '_id': ?0, 'terminalReportLocked': true }")
    @Update("{ '$set': { 'status': 'TERMINATED', 'terminalReportLocked': false, 'exitCode': ?1, 'errorMessage': ?2 } }")
    long finalizeTerminalAfterReport(String jobId, int exitCode, String errorMessage);

    /**
     * 兼容路径：仍为 {@link SlurmJobStatus#RUNNING} 且未持锁时置为 TERMINATED（幂等；已 TERMINATED 或正持锁则 0）。
     */
    @Query("{ '_id': ?0, 'status': 'RUNNING', 'terminalReportLocked': { '$ne': true } }")
    @Update("{ '$set': { 'status': 'TERMINATED' } }")
    long markTerminatedIfStillActive(String jobId);
}
