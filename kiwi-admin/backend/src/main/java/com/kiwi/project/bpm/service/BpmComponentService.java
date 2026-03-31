package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.system.spi.Refreshable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BpmComponentService implements InitializingBean, Refreshable
{

    private final BpmComponentDao bpmComponentDao;
    @Value("${bpm.component.auto-deploy:true}")
    private boolean autoDeploy;
    @Value("${bpm.component.delete-not-exist:true}")
    private boolean deleteNotExist;
    @Autowired(required = false)
    private List<BpmComponentProvider> bpmComponentProviderList;

    private final Map<String, BpmComponent> CachedComponents = new HashMap<String, BpmComponent>();

    public void deployComponent(BpmComponent bpmComponent) {
//        component.setId(component.getSource() + ""+component.getId());
        if( StringUtils.isBlank(bpmComponent.getId()) ) {
            bpmComponent.setId(bpmComponent.getSource() + "_" + bpmComponent.getKey());
        }
        this.bpmComponentDao.save(bpmComponent);
    }

    public void deploy(BpmComponentProvider bpmComponentProvider) {
        List<BpmComponent> bpmComponents = bpmComponentProvider.getComponents();
        bpmComponents.forEach(component -> {
            if( StringUtils.isBlank(component.getId()) ) {
                component.setId(component.getSource() + "_" + component.getKey());
            }
        });
        if( deleteNotExist ) {
            List<BpmComponent> current = this.bpmComponentDao.findBy(Query.query(Criteria.where("source").is(bpmComponentProvider.getSource())));
            List<BpmComponent> toDelete = current.stream().filter(component -> bpmComponents.stream().noneMatch(c -> Objects.equals(component.getKey(), c.getKey()))).toList();
            this.bpmComponentDao.deleteAll(toDelete);
        }
        this.bpmComponentDao.saveAll(bpmComponents);

    }

    public BpmComponent fillComponentProperties(BpmComponent bpmComponent) {
        if(bpmComponent.getParentId() == null){
            return bpmComponent;
        }
        BpmComponent parent = this.getComponent(bpmComponent.getParentId());
        if( parent != null ) {
            parent =  fillComponentProperties(parent);
        }else {
            return bpmComponent;
        }
        BpmComponent result = new BpmComponent();
        result.setId(bpmComponent.getId());
        result.setKey(Optional.ofNullable(bpmComponent.getKey()).orElse(parent.getKey()));
        result.setName(bpmComponent.getName());
        result.setDescription(bpmComponent.getDescription());
        result.setGroup(Optional.ofNullable(bpmComponent.getGroup()).orElse(parent.getGroup()))  ;
        result.setType(Optional.ofNullable(bpmComponent.getType()).orElse(parent.getType()))  ;
        result.setParentId(bpmComponent.getParentId());
        result.setVersion(Optional.ofNullable(bpmComponent.getVersion()).orElse(parent.getVersion()))  ;
        result.setInputParameters(mergeParameters(parent.getInputParameters(), bpmComponent.getInputParameters()));
        result.setOutputParameters(mergeParameters(parent.getOutputParameters(), bpmComponent.getOutputParameters()));
        return result;
    }

    private List<BpmComponentParameter> mergeParameters(List<BpmComponentParameter> parent, List<BpmComponentParameter> self) {
        if(parent == null){
            return self;
        }
        if(self == null){
            return parent;
        }
        List<BpmComponentParameter> result = new ArrayList<>(parent);
        Set<String> keySet = self.stream().map(p -> p.getKey()).collect(Collectors.toSet());
        result = result.stream().filter(p -> !keySet.contains(p.getKey())).collect(Collectors.toList());
        result.addAll(self);
        return result;
    }

    private BpmComponent getComponent(String parentId) {
        return this.CachedComponents.get(parentId);
    }


    @Override
    public void afterPropertiesSet() throws Exception {

        if( this.autoDeploy && this.bpmComponentProviderList != null ) {
            this.bpmComponentProviderList.forEach(bpmComponentProvider -> {
                deploy(bpmComponentProvider);
            });
        }
        refresh();
    }

    @Override
    public void refresh() {
        List<BpmComponent> all = this.bpmComponentDao.findAll();
        this.CachedComponents.clear();
        all.forEach(component -> {
            this.CachedComponents.put(component.getId(), component);
        });
    }
}
