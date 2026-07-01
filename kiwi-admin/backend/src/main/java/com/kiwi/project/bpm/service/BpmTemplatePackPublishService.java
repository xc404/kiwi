package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dao.BpmProjectEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplateEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplatePackDao;
import com.kiwi.project.bpm.dao.BpmTemplateProcessDao;
import com.kiwi.project.bpm.dto.PublishTemplatePackInput;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.model.BpmProjectEnvVar;
import com.kiwi.project.bpm.model.BpmTemplateEnvVar;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplatePackManifest;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BpmTemplatePackPublishService {

    private final BpmTemplatePackDao packDao;
    private final BpmTemplateProcessDao templateProcessDao;
    private final BpmTemplateEnvVarDao templateEnvVarDao;
    private final BpmProcessDefinitionDao processDao;
    private final BpmProjectEnvVarDao projectEnvVarDao;
    private final BpmTemplatePackManifestScanner manifestScanner;
    private final BpmOwnershipAccessService ownershipAccessService;

    @Transactional
    public BpmTemplatePack publishProject(String projectId, PublishTemplatePackInput input, String userId) {
        ownershipAccessService.assertOwnsProject(userId, projectId);
        List<BpmProcess> processes = processDao.findBy(Query.query(Criteria.where("projectId").is(projectId)));
        if (processes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目下没有可发布的流程");
        }
        List<BpmProjectEnvVar> envVars = projectEnvVarDao.findBy(
                Query.query(Criteria.where("projectId").is(projectId)));
        return savePackFromSources(input, userId, processes, envVars, "project:" + projectId);
    }

    @Transactional
    public BpmTemplatePack publishProcess(String processId, PublishTemplatePackInput input, String userId) {
        ownershipAccessService.assertOwnsProcess(userId, processId);
        BpmProcess process = processDao.findById(processId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程不存在"));
        return savePackFromSources(input, userId, List.of(process), List.of(), "process:" + processId);
    }

    private BpmTemplatePack savePackFromSources(
            PublishTemplatePackInput input,
            String userId,
            List<BpmProcess> processes,
            List<BpmProjectEnvVar> envVars,
            String sourceRef) {
        if (input == null || StringUtils.isBlank(input.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板包名称不能为空");
        }
        BpmTemplatePack pack = new BpmTemplatePack();
        pack.setName(input.getName().trim());
        pack.setSlug(resolveSlug(input.getSlug(), input.getName()));
        pack.setSummary(StringUtils.trimToNull(input.getSummary()));
        pack.setReadme(StringUtils.trimToNull(input.getReadme()));
        pack.setTags(input.getTags() != null ? new ArrayList<>(input.getTags()) : new ArrayList<>());
        pack.setCategory(StringUtils.trimToNull(input.getCategory()));
        pack.setKind(processes.size() <= 1 ? BpmTemplatePack.Kind.Single : BpmTemplatePack.Kind.Solution);
        pack.setPublisherId(userId);
        pack.setPublisherOrg("community");
        pack.setVersion(StringUtils.defaultIfBlank(input.getVersion(), "1.0.0"));
        pack.setVisibility(input.getVisibility() != null ? input.getVisibility() : BpmTemplatePack.Visibility.Org);
        pack.setStatus(BpmTemplatePack.Status.Published);
        pack.setProcessCount(processes.size());
        pack.setCreatedBy(userId);
        pack.setCreatedTime(new Date());
        pack.setUpdatedBy(userId);
        pack.setUpdatedTime(new Date());

        List<String> processKeys = new ArrayList<>();
        List<String> entryKeys = new ArrayList<>();
        List<String> xmlList = new ArrayList<>();
        int sort = 0;
        for (BpmProcess p : processes) {
            String key = toProcessKey(p);
            processKeys.add(key);
            if (p.isEntry()) {
                entryKeys.add(key);
            }
            xmlList.add(p.getBpmnXml());
            sort++;
        }
        pack.setEntryProcessKeys(entryKeys);
        BpmTemplatePackManifest manifest = manifestScanner.scan(xmlList, processKeys, entryKeys);
        manifest.setKind(pack.getKind().name().toLowerCase(Locale.ROOT));
        pack.setManifest(manifest);
        packDao.insert(pack);

        sort = 0;
        for (BpmProcess p : processes) {
            BpmTemplateProcess tp = new BpmTemplateProcess();
            tp.setPackId(pack.getId());
            tp.setProcessKey(toProcessKey(p));
            tp.setName(p.getName());
            tp.setBpmnXml(p.getBpmnXml());
            tp.setEntry(p.isEntry());
            tp.setSort(sort++);
            tp.setCreatedBy(userId);
            tp.setCreatedTime(new Date());
            templateProcessDao.insert(tp);
        }

        for (BpmProjectEnvVar ev : envVars) {
            BpmTemplateEnvVar te = new BpmTemplateEnvVar();
            te.setPackId(pack.getId());
            te.setKey(ev.getKey());
            te.setValue(ev.getValue());
            te.setEncrypted(ev.getEncrypted());
            te.setDescription(ev.getDescription());
            te.setSort(ev.getSort());
            te.setCreatedBy(userId);
            te.setCreatedTime(new Date());
            templateEnvVarDao.insert(te);
        }
        return pack;
    }

    @Transactional
    public BpmTemplatePack publishFromBundle(
            PublishTemplatePackInput input,
            String userId,
            BpmTemplatePackBundleService.BundleContent content) {
        if (input == null || StringUtils.isBlank(input.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板包名称不能为空");
        }
        BpmTemplatePack pack = new BpmTemplatePack();
        pack.setName(input.getName().trim());
        pack.setSlug(resolveSlug(input.getSlug(), input.getName()));
        pack.setSummary(StringUtils.trimToNull(input.getSummary()));
        pack.setReadme(StringUtils.trimToNull(
                StringUtils.isNotBlank(input.getReadme()) ? input.getReadme() : content.readme));
        pack.setTags(input.getTags() != null ? new ArrayList<>(input.getTags()) : new ArrayList<>());
        pack.setCategory(StringUtils.trimToNull(input.getCategory()));
        int processCount = content.processes.size();
        pack.setKind(processCount <= 1 ? BpmTemplatePack.Kind.Single : BpmTemplatePack.Kind.Solution);
        pack.setPublisherId(userId);
        pack.setPublisherOrg("import");
        pack.setVersion(StringUtils.defaultIfBlank(input.getVersion(), "1.0.0"));
        pack.setVisibility(input.getVisibility() != null ? input.getVisibility() : BpmTemplatePack.Visibility.Org);
        pack.setStatus(BpmTemplatePack.Status.Published);
        pack.setProcessCount(processCount);
        pack.setCreatedBy(userId);
        pack.setCreatedTime(new Date());
        pack.setUpdatedBy(userId);
        pack.setUpdatedTime(new Date());

        List<String> processKeys = new ArrayList<>(content.processes.keySet());
        List<String> xmlList = processKeys.stream().map(content.processes::get).toList();
        List<String> entryKeys = new ArrayList<>();
        BpmTemplatePackManifest manifest = manifestScanner.scan(xmlList, processKeys, entryKeys);
        manifest.setKind(pack.getKind().name().toLowerCase(Locale.ROOT));
        pack.setManifest(manifest);
        pack.setEntryProcessKeys(entryKeys);
        packDao.insert(pack);

        int sort = 0;
        for (String key : processKeys) {
            BpmTemplateProcess tp = new BpmTemplateProcess();
            tp.setPackId(pack.getId());
            tp.setProcessKey(key);
            tp.setName(key);
            tp.setBpmnXml(content.processes.get(key));
            tp.setSort(sort++);
            tp.setCreatedBy(userId);
            tp.setCreatedTime(new Date());
            templateProcessDao.insert(tp);
        }

        if (content.envVars != null) {
            for (BpmTemplateEnvVar te : content.envVars) {
                te.setId(null);
                te.setPackId(pack.getId());
                te.setCreatedBy(userId);
                te.setCreatedTime(new Date());
                templateEnvVarDao.insert(te);
            }
        }
        return pack;
    }

    private String toProcessKey(BpmProcess p) {
        if (StringUtils.isNotBlank(p.getId())) {
            return p.getId().trim();
        }
        return slugify(p.getName());
    }

    private String resolveSlug(String slug, String name) {
        String base = StringUtils.isNotBlank(slug) ? slug.trim() : slugify(name);
        if (StringUtils.isBlank(base)) {
            base = "pack-" + System.currentTimeMillis();
        }
        String candidate = base;
        int n = 1;
        while (packDao.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private String slugify(String name) {
        if (StringUtils.isBlank(name)) {
            return "template";
        }
        String s = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return StringUtils.isBlank(s) ? "template" : s;
    }
}
