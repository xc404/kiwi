package com.kiwi.framework.permission;

import com.kiwi.project.system.spi.Dict;
import com.kiwi.project.system.spi.DictGroup;
import com.kiwi.project.system.spi.DictsProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class PermissionDictsProvider implements DictsProvider
{
    private final PermissionService permissionService;

    @Override
    public List<DictGroup> getDictGroups() {
        DictGroup dictGroup = new DictGroup();
        dictGroup.setCode("permission");
        dictGroup.setName("权限");
        List<Dict> dicts = permissionService.getPermissions().stream().map(permission ->
        {
            Dict dict = new Dict();
            dict.setCode(permission.getKey());
            dict.setName(permission.getDescription());
            Set<String> requiredByModules = permission.getRequiredByModules();
            if( CollectionUtils.isNotEmpty(requiredByModules) ) {
//                dict.mo(List.copyOf(requiredByModules).get(0));
            }
            return dict;
        }).toList();
        dictGroup.setDict(dicts);
        return List.of(
                dictGroup
        );
    }
}
