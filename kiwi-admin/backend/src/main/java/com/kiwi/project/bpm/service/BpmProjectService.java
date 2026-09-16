package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dao.BpmProjectDao;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BpmProjectService {

    private final BpmProjectDao bpmProjectDao;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmProjectEnvService bpmProjectEnvService;

    public void deleteProject(String id) {
        long processCount = bpmProcessDefinitionDao.countBy(Query.query(Criteria.where("projectId").is(id)));
        if (processCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "项目下仍有 " + processCount + " 条流程，请先删除或迁走后再删除项目");
        }
        bpmProjectEnvService.deleteAllByProjectId(id);
        bpmProjectDao.deleteById(id);
    }
}
