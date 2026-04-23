package com.cryo.service;

import com.cryo.dao.ProjectRepository;
import com.cryo.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public Page<Project> findByGroupId(String groupId, String type, Pageable pageable) {
        Criteria criteria = Criteria.where("belong_group").is(groupId);
        if (type != null && !type.isBlank() && !"all".equalsIgnoreCase(type)) {
            criteria = criteria.and("type").is(type);
        }
        return projectRepository.findByQuery(Query.query(criteria), pageable);
    }
}
