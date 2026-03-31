package com.kiwi.project.system.service;

import com.kiwi.common.tree.Node;
import com.kiwi.project.system.spi.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DictService implements InitializingBean, Refreshable
{
    private final List<DictsProvider> providers;
    private final List<DictProvider> dictProviders;
    private final List<TreeProvider> treeProviders;


    private Map<String, TreeProvider> treeProviderMap;

    private List<DictGroup> dictGroups;
    private Map<String, DictGroup> dictGroupMap;
    private Map<String, DictProvider> dictProviderMap;


    public List<DictGroup> getDictGroups() {
        return List.copyOf(this.dictGroups);
    }

    public List<Dict> getDictList(String groupCode) {
        return getDictList(groupCode, null, Pageable.unpaged()).stream().toList();
    }

    public Page<Dict> getDictList(String groupCode, String pattern, Pageable pageable) {

        return Optional.ofNullable(getDictFromGroup(groupCode, pattern, pageable))
                .orElseGet(() -> {
                    DictProvider dictProvider = dictProviderMap.get(groupCode);
                    if( dictProvider != null ) {
                        return dictProvider.getDict(pattern, pageable);
                    }
                    return Page.<Dict>empty();
                });
    }

    private Page<Dict> getDictFromGroup(String key, String pattern, Pageable pageable) {
        return Optional.ofNullable(this.dictGroupMap.get(key))
                .map(g -> {
                    Stream<Dict> dictStream = g.getDict().stream()
                            .filter(d -> pattern == null || d.getDescription().contains(pattern) || d.getName().contains(pattern));
                    if( !pageable.isPaged() ) {
                        return new PageImpl<>(dictStream.collect(Collectors.toList()), pageable, dictStream.count());
                    } else {
                        return new PageImpl<>(dictStream.skip(pageable.getOffset()).limit(pageable.getPageSize()).collect(Collectors.toList()));
                    }
                }).orElse(null);
    }

    private void loadDict() {
        dictGroups = providers.stream().map(DictsProvider::getDictGroups).flatMap(List::stream).filter(Objects::nonNull).toList();
        this.dictGroupMap = dictGroups.stream().collect(Collectors.toMap(DictGroup::getCode, Function.identity()));
        this.dictProviderMap = this.dictProviders.stream().collect(Collectors.toMap(DictProvider::group, Function.identity()));
        this.treeProviderMap = this.treeProviders.stream().collect(Collectors.toMap(TreeProvider::group, Function.identity()));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadDict();
    }


    @Override
    public void refresh() {
        this.loadDict();
    }

    public List<Node> getTree(String groupCode, String parentId, boolean loadAll, Map<String, Object> extraParams) {
        List<Node> list = Optional.ofNullable(this.treeProviderMap.get(groupCode)).map(p -> {
            if (loadAll) {
                return p.getTree(parentId, extraParams);
            } else {
                return p.getChildren(parentId, extraParams);
            }
        }).orElse(List.of());
        return list;
    }
}
