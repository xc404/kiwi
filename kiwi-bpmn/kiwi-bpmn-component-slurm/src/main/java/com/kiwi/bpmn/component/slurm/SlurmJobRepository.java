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
     * 应用层并发闸门使用的当前在跑作业计数。
     * <p>
     * Spring Data 派生方法，底层走 Mongo {@code count} 命令；{@code status} 字段已被既有
     * {@code findByStatusAndExpiration*} 查询使用，索引已具备。
     *
     * @see SlurmExternalTaskHandler
     * @see SlurmProperties#getMaxConcurrentJobs()
     */
    long countByStatus(SlurmJobStatus status);

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

    /**
     * 跟踪窗口到期（{@link SlurmJobTracker#applyTimeout}）后的兜底收尾：仅当文档仍为
     * {@link SlurmJobStatus#Running} 时原子置为 {@link SlurmJobStatus#Completed}、清锁，并写入
     * {@code slurmState} / {@code errorMessage} / {@code exitCode}。
     * <p>
     * 不要求持有 {@code completeProcessLock}：调用方语义是"无论 Camunda 上报是否成功都必须打破 Running，
     * 否则下一轮 {@code findByStatusAndExpirationBefore} 会重复触发死循环"。Camunda 上报路径已成功时
     * 文档状态已是 {@code Completed}，此方法返回 0 表示 no-op，避免双写。
     *
     * @return 影响的文档数（0 表示已是 Completed 或不存在）
     */
    @Query("{ '_id': ?0, 'status': 'Running' }")
    @Update("{ '$set': { 'status': 'Completed', 'completeProcessLock': false, 'slurmState': ?1, 'errorMessage': ?2, 'exitCode': ?3 } }")
    long forceFinalizeIfStillRunning(String jobId, String slurmState, String errorMessage, Integer exitCode);
}
