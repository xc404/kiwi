package com.kiwi.cryoems.bpm.mdoc.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDocInstance;

/**
 * {@link MDocInstance} 的 Mongo 仓库，直连 cryoEMS 同一个 {@code mDocInstance} 集合。
 */
public interface MDocInstanceRepository extends BaseMongoRepository<MDocInstance, String> {
}
