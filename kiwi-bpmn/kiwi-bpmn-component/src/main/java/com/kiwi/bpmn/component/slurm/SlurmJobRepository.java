package com.kiwi.bpmn.component.slurm;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Date;
import java.util.List;

public interface SlurmJobRepository extends MongoRepository<SlurmJob, String> {

    List<SlurmJob> findByExternalTaskIdAndStatus(String externalTaskId, SlurmJobStatus status);

    boolean existsByExternalTaskId(String externalTaskId);

    List<SlurmJob> findByStatusAndExpirationBefore(SlurmJobStatus status, Date now);

    List<SlurmJob> findByStatusAndExpirationGreaterThanEqual(SlurmJobStatus status, Date now);

    /**
     * 乐观并发：仅当 {@link SlurmJobStatus#Running} 且未持锁（{@link SlurmJob#getCompleteProcessLock()} 非 true）时
     * 原子置 {@code completeProcessLock=true}；返回修改行数（0 表示未抢到）。
     */
    @Query("{ '_id': ?0, 'status': 'Running', 'completeProcessLock': { '$ne': true } }")
    @Update("{ '$set': { 'completeProcessLock': true } }")
    long claimCompleteProcessLock(String jobId);

    /** Camunda 上报失败时释放锁；{@link SlurmJobStatus} 保持 {@link SlurmJobStatus#Running}。 */
    @Query("{ '_id': ?0, 'completeProcessLock': true }")
    @Update("{ '$set': { 'completeProcessLock': false } }")
    long releaseCompleteProcessLock(String jobId);

    /** Camunda 已接受 complete/handleFailure 后写入 {@link SlurmJobStatus#Completed}、清锁，并记录退出码与错误说明（成功时 {@code errorMessage} 可为 null）。 */
    @Query("{ '_id': ?0, 'completeProcessLock': true }")
    @Update("{ '$set': { 'status': 'Completed', 'completeProcessLock': false } }")
    long finalizeAfterCamundaReport(String jobId);

    /**
     * 兼容路径：仍为 {@link SlurmJobStatus#Running} 且未持锁时置为 Completed（幂等；已 Completed 或正持锁则 0）。
     */
    @Query("{ '_id': ?0, 'status': 'Running', 'completeProcessLock': { '$ne': true } }")
    @Update("{ '$set': { 'status': 'Completed' } }")
    long markCompletedIfStillActive(String jobId);
}
