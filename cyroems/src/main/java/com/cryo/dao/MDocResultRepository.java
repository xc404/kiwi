package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.MDocResult;
import com.cryo.model.MovieResult;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface MDocResultRepository extends BaseRepository<MDocResult, String>
{
    @Query("{data_id: ?0, config_id: ?1}")
    Optional<MDocResult> findByDataId(String dataId, String configId);
}
