package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dao.BpmProjectDao;
import com.kiwi.project.bpm.dao.BpmProjectEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplateEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplatePackDao;
import com.kiwi.project.bpm.dao.BpmTemplateProcessDao;
import com.kiwi.project.bpm.dto.InstallTemplatePackInput;
import com.kiwi.project.bpm.dto.InstallTemplatePackResult;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.model.BpmProject;
import com.kiwi.project.bpm.model.BpmProjectEnvVar;
import com.kiwi.project.bpm.model.BpmTemplateEnvVar;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BpmTemplatePackInstallService {

    private final BpmTemplatePackDao packDao;
    private final BpmTemplateProcessDao templateProcessDao;
    private final BpmTemplateEnvVarDao templateEnvVarDao;
    private final BpmProjectDao projectDao;
    private final BpmProcessDefinitionDao processDao;
    private final BpmProjectEnvVarDao projectEnvVarDao;
    private final BpmProcessDefinitionService processDefinitionService;
    private final BpmTemplatePackManifestScanner manifestScanner;
    private final BpmTemplatePackService packService;
    private final BpmOwnershipAccessService ownershipAccessService;

    @Transactional
    public InstallTemplatePackResult installPack(String packId, InstallTemplatePackInput input, String userId) {
        BpmTemplatePack pack = packService.requireReadablePack(packId, userId);
        String projectName = input != null && StringUtils.isNotBlank(input.getProjectName())
                ? input.getProjectName().trim()
                : pack.getName() + " (from template)";
        BpmProject project = new BpmProject();
        project.setName(projectName);
        project.setCreatedBy(userId);
        project.setCreatedTime(new Date());
        projectDao.insert(project);
        int count = copyPackIntoProject(pack, project.getId(), userId, false);
        packService.incrementInstallCount(packId);
        InstallTemplatePackResult result = new InstallTemplatePackResult();
        result.setProjectId(project.getId());
        result.setPackId(packId);
        result.setProcessCount(count);
        return result;
    }

    @Transactional
    public InstallTemplatePackResult installPackInto(String packId, InstallTemplatePackInput input, String userId) {
        if (input == null || StringUtils.isBlank(input.getTargetProjectId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetProjectId 不能为空");
        }
        ownershipAccessService.assertOwnsProject(userId, input.getTargetProjectId().trim());
        BpmTemplatePack pack = packService.requireReadablePack(packId, userId);
        int count = copyPackIntoProject(pack, input.getTargetProjectId().trim(), userId, true);
        packService.incrementInstallCount(packId);
        InstallTemplatePackResult result = new InstallTemplatePackResult();
        result.setProjectId(input.getTargetProjectId().trim());
        result.setPackId(packId);
        result.setProcessCount(count);
        return result;
    }

    @Transactional
    public BpmProcess installProcess(String packId, String processKey, String targetProjectId, String userId) {
        ownershipAccessService.assertOwnsProject(userId, targetProjectId);
        packService.requireReadablePack(packId, userId);
        BpmTemplateProcess template = templateProcessDao.findByPackIdAndProcessKey(packId, processKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程不存在"));
        String newId = processDefinitionService.getNewProcessId();
        Map<String, String> identityMap = Map.of(template.getProcessKey(), newId);
        return createProcessFromTemplate(template, targetProjectId, userId, identityMap, false);
    }

    private int copyPackIntoProject(BpmTemplatePack pack, String projectId, String userId, boolean merge) {
        List<BpmTemplateProcess> templates = templateProcessDao.findByPackIdOrderBySortAsc(pack.getId());
        if (templates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板包内没有流程");
        }
        Set<String> existingNames = new HashSet<>();
        if (merge) {
            processDao.findBy(Query.query(Criteria.where("projectId").is(projectId)))
                    .forEach(p -> existingNames.add(p.getName()));
        }
        Map<String, String> keyToNewId = new HashMap<>();
        for (BpmTemplateProcess t : templates) {
            String newId = processDefinitionService.getNewProcessId();
            keyToNewId.put(t.getProcessKey(), newId);
        }
        int count = 0;
        for (BpmTemplateProcess t : templates) {
            createProcessFromTemplate(t, projectId, userId, keyToNewId, merge && existingNames.contains(t.getName()));
            count++;
        }
        copyEnvVars(pack.getId(), projectId, userId);
        return count;
    }

    private BpmProcess createProcessFromTemplate(
            BpmTemplateProcess template,
            String projectId,
            String userId,
            Map<String, String> keyToNewId,
            boolean renameOnConflict) {
        String newId = keyToNewId.getOrDefault(template.getProcessKey(), processDefinitionService.getNewProcessId());
        String xml = manifestScanner.remapCalledElements(template.getBpmnXml(), keyToNewId);
        String name = template.getName();
        if (renameOnConflict && StringUtils.isNotBlank(name)) {
            name = name + " (imported)";
        }
        BpmProcess process = new BpmProcess();
        process.setId(newId);
        process.setName(name);
        process.setProjectId(projectId);
        process.setBpmnXml(xml);
        process.setEntry(template.isEntry());
        process.setCreatedBy(userId);
        process.setCreatedTime(new Date());
        processDefinitionService.syncBpmnIdentity(process);
        processDao.insert(process);
        return process;
    }

    private void copyEnvVars(String packId, String projectId, String userId) {
        List<BpmTemplateEnvVar> templateEnv = templateEnvVarDao.findByPackIdOrderBySortAscKeyAsc(packId);
        for (BpmTemplateEnvVar te : templateEnv) {
            List<BpmProjectEnvVar> existing = projectEnvVarDao.findBy(Query.query(
                    Criteria.where("projectId").is(projectId).and("key").is(te.getKey())));
            if (!existing.isEmpty()) {
                continue;
            }
            BpmProjectEnvVar ev = new BpmProjectEnvVar();
            ev.setProjectId(projectId);
            ev.setKey(te.getKey());
            ev.setValue(te.getValue());
            ev.setEncrypted(te.getEncrypted());
            ev.setDescription(te.getDescription());
            ev.setSort(te.getSort());
            ev.setCreatedBy(userId);
            ev.setCreatedTime(new Date());
            projectEnvVarDao.insert(ev);
        }
    }
}
