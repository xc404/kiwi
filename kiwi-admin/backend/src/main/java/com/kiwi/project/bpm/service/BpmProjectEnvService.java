package com.kiwi.project.bpm.service;

import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.bpm.dao.BpmProjectEnvVarDao;
import com.kiwi.project.bpm.dto.BpmProjectEnvVarDto;
import com.kiwi.project.bpm.model.BpmProjectEnvVar;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.variable.VariableMap;
import org.operaton.bpm.engine.variable.Variables;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BpmProjectEnvService {

    private final BpmProjectEnvVarDao bpmProjectEnvVarDao;
    private final PasswordService passwordService;

    public Page<BpmProjectEnvVarDto> pageByProject(String projectId, Pageable pageable) {
        Query query = Query.query(Criteria.where("projectId").is(projectId))
                .with(Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("key")));
        List<BpmProjectEnvVarDto> all = bpmProjectEnvVarDao.findBy(query).stream().map(this::toDto).toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<BpmProjectEnvVarDto> slice = start >= all.size() ? List.of() : all.subList(start, end);
        return new PageImpl<>(slice, pageable, all.size());
    }

    public BpmProjectEnvVarDto getDto(String id) {
        return toDto(require(id));
    }

    public BpmProjectEnvVarDto create(BpmProjectEnvVar input) {
        validateKey(input.getKey());
        if (StringUtils.isBlank(input.getProjectId())) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        assertKeyUnique(input.getProjectId(), input.getKey(), null);
        input.setEncrypted(Boolean.TRUE.equals(input.getEncrypted()));
        if (Boolean.TRUE.equals(input.getEncrypted()) && StringUtils.isBlank(input.getValue())) {
            throw new IllegalArgumentException("加密项必须提供 value");
        }
        encryptIfNeeded(input);
        bpmProjectEnvVarDao.insert(input);
        return toDto(input);
    }

    public BpmProjectEnvVarDto update(String id, BpmProjectEnvVar patch) {
        BpmProjectEnvVar existing = require(id);
        if (StringUtils.isNotBlank(patch.getKey()) && !Objects.equals(patch.getKey(), existing.getKey())) {
            validateKey(patch.getKey());
            assertKeyUnique(existing.getProjectId(), patch.getKey(), id);
            existing.setKey(patch.getKey().trim());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        if (patch.getSort() != null) {
            existing.setSort(patch.getSort());
        }
        if (patch.getEncrypted() != null) {
            existing.setEncrypted(patch.getEncrypted());
        }
        if (StringUtils.isNotBlank(patch.getValue())) {
            existing.setValue(patch.getValue());
            encryptIfNeeded(existing);
        } else if (!Boolean.TRUE.equals(existing.getEncrypted()) && patch.getValue() != null) {
            existing.setValue(patch.getValue());
        }
        bpmProjectEnvVarDao.updateSelective(existing);
        return toDto(bpmProjectEnvVarDao.findById(id).orElseThrow());
    }

    public void delete(String id) {
        bpmProjectEnvVarDao.deleteById(id);
    }

    /**
     * 启动流程用：合并项目 env 与用户 variables（用户优先），拆分为普通变量与瞬态（加密）变量。
     */
    public VariableMap mergeForProcessStart(String projectId, Map<String, Object> userVariables) {
        Map<String, Object> user = userVariables != null ? userVariables : Map.of();
        VariableMap variables = Variables.createVariables();

        if (StringUtils.isNotBlank(projectId)) {
            Query query = Query.query(Criteria.where("projectId").is(projectId));
            for (BpmProjectEnvVar env : bpmProjectEnvVarDao.findBy(query)) {
                if (StringUtils.isBlank(env.getKey()) || user.containsKey(env.getKey())) {
                    continue;
                }
                String plain = decryptValue(env);
                if (Boolean.TRUE.equals(env.getEncrypted())) {
                    variables.putValueTyped(env.getKey(), Variables.stringValue(plain, true));
                } else {
                    variables.putValue(env.getKey(), plain);
                }
            }
        }

        for (Map.Entry<String, Object> entry : user.entrySet()) {
            if (entry.getKey() != null) {
                variables.putValue(entry.getKey(), entry.getValue());
            }
        }

        return variables;
    }

    private BpmProjectEnvVar require(String id) {
        return bpmProjectEnvVarDao.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "环境变量不存在: " + id));
    }

    private void assertKeyUnique(String projectId, String key, String excludeId) {
        Query query = Query.query(Criteria.where("projectId").is(projectId).and("key").is(key.trim()));
        List<BpmProjectEnvVar> found = bpmProjectEnvVarDao.findBy(query);
        boolean conflict = found.stream().anyMatch(e -> excludeId == null || !excludeId.equals(e.getId()));
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "项目内已存在 key: " + key);
        }
    }

    private void validateKey(String key) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("key 不能为空");
        }
        String trimmed = key.trim();
        if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("key 仅允许字母、数字、下划线，且不能以数字开头");
        }
    }

    private void encryptIfNeeded(BpmProjectEnvVar env) {
        if (!Boolean.TRUE.equals(env.getEncrypted()) || StringUtils.isBlank(env.getValue())) {
            return;
        }
        String encoded = AesUtil.encryptToBase64(env.getValue(), passwordService.getPasswordSecret());
        env.setValue(encoded);
    }

    private String decryptValue(BpmProjectEnvVar env) {
        if (StringUtils.isBlank(env.getValue())) {
            return "";
        }
        if (!Boolean.TRUE.equals(env.getEncrypted())) {
            return env.getValue();
        }
        return AesUtil.decryptFormBase64ToString(env.getValue(), passwordService.getPasswordSecret());
    }

    private BpmProjectEnvVarDto toDto(BpmProjectEnvVar entity) {
        BpmProjectEnvVarDto dto = new BpmProjectEnvVarDto();
        BeanUtils.copyProperties(entity, dto, "value");
        if (!Boolean.TRUE.equals(entity.getEncrypted())) {
            dto.setValue(entity.getValue());
        }
        return dto;
    }

}
