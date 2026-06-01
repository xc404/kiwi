package com.kiwi.framework.mongo.migration;

import com.kiwi.project.system.service.DictService;
import com.kiwi.project.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoMigrationCacheRefresh {

    private final MenuService menuService;
    private final DictService dictService;

    @EventListener(ApplicationReadyEvent.class)
    public void refreshCachesAfterStartup() {
        menuService.refresh();
        dictService.refresh();
        log.debug("Refreshed menu and dict caches after application ready");
    }
}
