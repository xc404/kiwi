package com.kiwi.framework.mongo.migration.primary;

import com.kiwi.framework.mongo.migration.support.ClasspathJsonMigrationSupport;
import com.kiwi.project.system.dao.SysDictDao;
import com.kiwi.project.system.dao.SysDictGroupDao;
import com.kiwi.project.system.dao.SysMenuDao;
import com.kiwi.project.system.entity.SysDict;
import com.kiwi.project.system.entity.SysDictGroup;
import com.kiwi.project.system.entity.SysMenu;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import lombok.RequiredArgsConstructor;

@ChangeUnit(id = "20250601-002-load-system-reference-data", order = "002", author = "kiwi")
@RequiredArgsConstructor
public class LoadSystemReferenceDataChangeUnit {

    private static final String DATA_PREFIX = "mongo/migration/data/";

    private final ClasspathJsonMigrationSupport jsonMigrationSupport;
    private final SysDictGroupDao sysDictGroupDao;
    private final SysDictDao sysDictDao;
    private final SysMenuDao sysMenuDao;

    @Execution
    public void execute() {
        jsonMigrationSupport.loadAndUpsert(
                DATA_PREFIX + "sys_dict_group.json", SysDictGroup.class, sysDictGroupDao);
        jsonMigrationSupport.loadAndUpsert(DATA_PREFIX + "sys_dict.json", SysDict.class, sysDictDao);
        jsonMigrationSupport.loadAndUpsert(DATA_PREFIX + "sys_menu.json", SysMenu.class, sysMenuDao);
    }
}
