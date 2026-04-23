package com.cryo.dao.dataset;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.dataset.MovieDataset;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface MovieDataSetRepository extends BaseRepository<MovieDataset, String>
{
    @Query("{ 'belonging_data' : ?0,  'path': ?1}")
    Optional<MovieDataset> findByFile(String id, String absolutePath);
}
