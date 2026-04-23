package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.MovieResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MovieResultRepository extends BaseRepository<MovieResult, String>
{
    /**
     * Same logical key may exist more than once when the compound unique index is not enforced.
     * Restrict to the newest row so callers do not get IncorrectResultSizeDataAccessException.
     */
    @Query("{movie_data_id: ?0, config_id: ?1}")
    List<MovieResult> findByDataIdCandidates(String dataId, String configId, Pageable pageable);

    default Optional<MovieResult> findByDataId(String dataId, String configId) {
        List<MovieResult> list = findByDataIdCandidates(
                dataId,
                configId,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "updated_at")));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("{task_id: ?0}")
    List<MovieResult> findByTaskId(String taskId);
}
