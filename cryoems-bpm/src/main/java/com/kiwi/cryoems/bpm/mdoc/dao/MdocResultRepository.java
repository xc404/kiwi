package com.kiwi.cryoems.bpm.mdoc.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MdocResult;

/**
 * {@link MdocResult} 的 Mongo 仓库，直连 cryoEMS 同一个 {@code mDocResult} 集合；
 * 与 {@link MDocRepository} / {@link com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository} 同形。
 */
public interface MdocResultRepository extends BaseMongoRepository<MdocResult, String> {
}
