package com.kiwi.cryoems.bpm.mdoc.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;

/**
 * {@link MDoc} 的 Mongo 仓库，直连 cryoEMS 同一个 {@code mdoc} 集合；
 * 与 {@link com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository} 同形。
 */
public interface MDocRepository extends BaseMongoRepository<MDoc, String> {
}
