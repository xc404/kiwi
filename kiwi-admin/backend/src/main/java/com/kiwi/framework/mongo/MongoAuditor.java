package com.kiwi.framework.mongo;

import com.kiwi.framework.session.SessionService;
import com.kiwi.framework.session.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MongoAuditor implements AuditorAware<String>
{
    private final SessionService service;
    @Override
    public Optional<String> getCurrentAuditor() {
        try {
            SessionUser currentUser = service.getCurrentUser();
            if (currentUser != null) {
                return Optional.of(currentUser.getId());
            }
        }catch( Exception e ){
            log.error(e.getMessage());
        }
        return Optional.empty();
    }
}
