package com.cryo.dao.dataset;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.dataset.MDoc;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface MDocRepository extends BaseRepository<MDoc, String>
{
    @Query("{ 'belonging_data' : ?0,  'path': ?1}")
    Optional<MDoc> findByFile(String dataId, String absolutePath);
}
