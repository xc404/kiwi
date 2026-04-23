package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.Microscope;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MicroscopeRepository extends BaseRepository<Microscope, String>
{
    @Query("{ 'managed_by': ?0 }")
    List<Microscope> findByManagedBy(String userId);

    @Query("{ 'microscope_key': ?0 }")
    Optional<Microscope> findByMicroscopeKey(String microscopeKey);
}
