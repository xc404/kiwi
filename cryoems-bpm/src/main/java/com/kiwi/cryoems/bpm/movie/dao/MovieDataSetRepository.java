package com.kiwi.cryoems.bpm.movie.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.cryoems.bpm.movie.model.MovieDataset;

/**
 * {@link MovieDataset} 的 Mongo 仓库，直连 cryoEMS 同一个 {@code movies} 集合；
 * 与 {@link MovieResultRepository} 同形。
 */
public interface MovieDataSetRepository extends BaseMongoRepository<MovieDataset, String> {
}
