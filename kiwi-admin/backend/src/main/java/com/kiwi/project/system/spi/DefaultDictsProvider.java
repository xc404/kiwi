package com.kiwi.project.system.spi;

import com.kiwi.project.system.dao.SysDictDao;
import com.kiwi.project.system.dao.SysDictGroupDao;
import com.kiwi.project.system.entity.SysDict;
import com.kiwi.project.system.entity.SysDictGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service

@RequiredArgsConstructor
public class DefaultDictsProvider implements DictsProvider
{
    private final SysDictDao sysDictDao;
    private final SysDictGroupDao sysDictGroupDao;

    @Override
    public List<DictGroup> getDictGroups() {
        List<SysDictGroup> sysDictGroups = sysDictGroupDao.findAll();
        List<SysDict> dicts = this.sysDictDao.findAll();
        Map<String, List<SysDict>> dictGroup = dicts.stream()
                .filter(dict -> dict.getCode() != null && dict.getGroupCode()!= null)
                .collect(Collectors.groupingBy(SysDict::getGroupCode));
        return sysDictGroups.stream().filter(SysDictGroup::enabled).map(group -> {
            String groupKey = group.getGroupCode();
            List<SysDict> sysDicts = dictGroup.get(groupKey);
            if( sysDicts == null ) {
                return null;
            }
            List<Dict> list = sysDicts.stream().map(d -> {
                return new Dict(
                        d.getCode(),
                        d.getName(),
                        d.getSubGroup(),
                        d.getRemark()
                );
            }).toList();
            return new DictGroup(groupKey, group.getGroupName(), list);
        }).filter(Objects::nonNull).toList();
    }


}
