package com.kiwi.cryoems.bpm.dao;

import com.kiwi.cryoems.bpm.model.MovieResult;
import com.kiwi.common.mongo.BaseMongoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MovieResultRepository extends BaseMongoRepository<MovieResult, String> {

}
