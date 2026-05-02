package com.kiwi.bpmn.component.slurm;

import org.springframework.data.mongodb.repository.MongoRepository;

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
}
