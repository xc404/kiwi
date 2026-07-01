package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmTemplateEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplatePackDao;
import com.kiwi.project.bpm.dao.BpmTemplateProcessDao;
import com.kiwi.project.bpm.dto.BpmTemplatePackDetailDto;
import com.kiwi.project.bpm.model.BpmTemplateEnvVar;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BpmTemplatePackService {

    private final BpmTemplatePackDao packDao;
    private final BpmTemplateProcessDao processDao;
    private final BpmTemplateEnvVarDao envVarDao;
    private final BpmOwnershipAccessService ownershipAccessService;

    @Data
    public static class PackQueryInput {
        private String category;
        private String kind;
        private String status;
        private String keyword;
        private String tag;
    }

    public Page<BpmTemplatePack> page(PackQueryInput input, Pageable pageable, String userId) {
        PackQueryInput q = input != null ? input : new PackQueryInput();
        if (StringUtils.isBlank(q.getStatus())) {
            q.setStatus(BpmTemplatePack.Status.Published.name());
        }
        Criteria criteria = buildVisibilityCriteria(userId);
        if (StringUtils.isNotBlank(q.getCategory())) {
            criteria.and("category").is(q.getCategory().trim());
        }
        if (StringUtils.isNotBlank(q.getKind())) {
            criteria.and("kind").is(q.getKind().trim());
        }
        if (StringUtils.isNotBlank(q.getStatus())) {
            criteria.and("status").is(q.getStatus().trim());
        }
        if (StringUtils.isNotBlank(q.getTag())) {
            criteria.and("tags").is(q.getTag().trim());
        }
        if (StringUtils.isNotBlank(q.getKeyword())) {
            String kw = Pattern.quote(q.getKeyword().trim());
            criteria.orOperator(
                    Criteria.where("name").regex(kw, "i"),
                    Criteria.where("summary").regex(kw, "i"),
                    Criteria.where("slug").regex(kw, "i"));
        }
        Query query = Query.query(criteria).with(Sort.by(Sort.Order.desc("updatedTime")));
        List<BpmTemplatePack> all = packDao.findBy(query);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<BpmTemplatePack> slice = start >= all.size() ? List.of() : all.subList(start, end);
        return new PageImpl<>(slice, pageable, all.size());
    }

    public BpmTemplatePack requireReadablePack(String packId, String userId) {
        BpmTemplatePack pack = packDao.findById(packId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板包不存在: " + packId));
        assertCanRead(pack, userId);
        return pack;
    }

    public BpmTemplatePackDetailDto getDetail(String packId, String userId) {
        BpmTemplatePack pack = requireReadablePack(packId, userId);
        BpmTemplatePackDetailDto dto = new BpmTemplatePackDetailDto();
        dto.setPack(pack);
        dto.setManifest(pack.getManifest());
        List<BpmTemplateProcess> processes = processDao.findByPackIdOrderBySortAsc(packId);
        for (BpmTemplateProcess p : processes) {
            dto.getProcesses().add(BpmTemplatePackDetailDto.fromProcess(p));
        }
        envVarDao.findByPackIdOrderBySortAscKeyAsc(packId).stream()
                .map(BpmTemplateEnvVar::getKey)
                .forEach(dto.getEnvKeys()::add);
        return dto;
    }

    public BpmTemplateProcess getProcess(String packId, String processKey, String userId) {
        requireReadablePack(packId, userId);
        return processDao.findByPackIdAndProcessKey(packId, processKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "流程不存在: packId=" + packId + ", processKey=" + processKey));
    }

    public List<BpmTemplateProcess> listProcesses(String packId, String userId) {
        requireReadablePack(packId, userId);
        return processDao.findByPackIdOrderBySortAsc(packId);
    }

    public void incrementInstallCount(String packId) {
        BpmTemplatePack pack = packDao.findById(packId).orElse(null);
        if (pack != null) {
            pack.setInstallCount(pack.getInstallCount() + 1);
            packDao.updateSelective(pack);
        }
    }

    private Criteria buildVisibilityCriteria(String userId) {
        if (ownershipAccessService.isBpmAdmin()) {
            return new Criteria();
        }
        List<Criteria> or = new ArrayList<>();
        or.add(Criteria.where("visibility").in(
                BpmTemplatePack.Visibility.Org.name(),
                BpmTemplatePack.Visibility.Public.name()));
        if (StringUtils.isNotBlank(userId)) {
            or.add(Criteria.where("publisherId").is(userId));
        }
        return new Criteria().orOperator(or.toArray(Criteria[]::new));
    }

    private void assertCanRead(BpmTemplatePack pack, String userId) {
        if (ownershipAccessService.isBpmAdmin()) {
            return;
        }
        if (pack.getVisibility() == BpmTemplatePack.Visibility.Org
                || pack.getVisibility() == BpmTemplatePack.Visibility.Public) {
            if (pack.getStatus() == BpmTemplatePack.Status.Published) {
                return;
            }
        }
        if (Objects.equals(pack.getPublisherId(), userId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该模板包");
    }
}
