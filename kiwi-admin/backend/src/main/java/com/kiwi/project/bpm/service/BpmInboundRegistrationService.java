package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmInboundRegistrationDao;
import com.kiwi.project.bpm.model.BpmInboundRegistration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BpmInboundRegistrationService {

    private final BpmInboundRegistrationDao inboundRegistrationDao;

    public Page<BpmInboundRegistration> page(Pageable pageable) {
        return inboundRegistrationDao.findAll(pageable);
    }

    public BpmInboundRegistration get(String id) {
        return inboundRegistrationDao
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "入站注册不存在"));
    }

    public BpmInboundRegistration create(BpmInboundRegistration body) {
        validate(body);
        if (inboundRegistrationDao.findByComponentKey(body.getComponentKey()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "componentKey 已存在");
        }
        if (body.getEnabled() == null) {
            body.setEnabled(true);
        }
        return inboundRegistrationDao.save(body);
    }

    public BpmInboundRegistration update(String id, BpmInboundRegistration body) {
        BpmInboundRegistration existing = get(id);
        if (StringUtils.isNotBlank(body.getComponentKey())
                && !body.getComponentKey().equals(existing.getComponentKey())) {
            if (inboundRegistrationDao.findByComponentKey(body.getComponentKey()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "componentKey 已存在");
            }
            existing.setComponentKey(body.getComponentKey());
        }
        if (body.getMessageName() != null) {
            existing.setMessageName(body.getMessageName());
        }
        if (body.getProjectId() != null) {
            existing.setProjectId(body.getProjectId());
        }
        if (body.getSecretToken() != null) {
            existing.setSecretToken(body.getSecretToken());
        }
        if (body.getDescription() != null) {
            existing.setDescription(body.getDescription());
        }
        if (body.getEnabled() != null) {
            existing.setEnabled(body.getEnabled());
        }
        inboundRegistrationDao.updateSelective(existing);
        return get(id);
    }

    public void delete(String id) {
        inboundRegistrationDao.deleteById(id);
    }

    private void validate(BpmInboundRegistration body) {
        if (body == null || StringUtils.isBlank(body.getComponentKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "componentKey 不能为空");
        }
        if (StringUtils.isBlank(body.getMessageName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messageName 不能为空");
        }
    }
}
