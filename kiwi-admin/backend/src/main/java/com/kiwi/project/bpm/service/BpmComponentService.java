package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.utils.BpmComponentDeploymentSignature;
import com.kiwi.project.system.spi.Refreshable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
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
        bpmComponent.setDeploymentSignature(BpmComponentDeploymentSignature.compute(bpmComponent));
        this.bpmComponentDao.save(bpmComponent);
    }

    public void deploy(BpmComponentProvider bpmComponentProvider) {
        List<BpmComponent> bpmComponents = bpmComponentProvider.getComponents();
        bpmComponents.forEach(component -> {
            if( StringUtils.isBlank(component.getId()) ) {
                component.setId(component.getSource() + "_" + component.getKey());
            }
        });
        List<BpmComponent> current = this.bpmComponentDao.findBy(Query.query(Criteria.where("source").is(bpmComponentProvider.getSource())));
        Map<String, BpmComponent> byId = current.stream().collect(Collectors.toMap(BpmComponent::getId, c -> c, (a, b) -> a));
        if( deleteNotExist ) {
            List<BpmComponent> toDelete = current.stream().filter(component -> bpmComponents.stream().noneMatch(c -> Objects.equals(component.getKey(), c.getKey()))).toList();
            this.bpmComponentDao.deleteAll(toDelete);
            for (BpmComponent removed : toDelete) {
                byId.remove(removed.getId());
            }
        }
        List<BpmComponent> toSave = new ArrayList<>();
        for (BpmComponent inc : bpmComponents) {
            String sig = BpmComponentDeploymentSignature.compute(inc);
            BpmComponent existing = byId.get(inc.getId());
            if (existing != null && Objects.equals(sig, existing.getDeploymentSignature())) {
                continue;
            }
            inc.setDeploymentSignature(sig);
            toSave.add(inc);
        }
        if (!toSave.isEmpty()) {
            this.bpmComponentDao.saveAll(toSave);
        }

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
        List<BpmComponentParameter> parentInput = copyParameterList(parent.getInputParameters());
        applyDefaultParameterGroup(parentInput, parent.getName());
        List<BpmComponentParameter> parentOutput = copyParameterList(parent.getOutputParameters());
        applyDefaultParameterGroup(parentOutput, parent.getName());
        result.setInputParameters(mergeParameters(parentInput, bpmComponent.getInputParameters()));
        result.setOutputParameters(mergeParameters(parentOutput, bpmComponent.getOutputParameters()));
        return result;
    }

    /**
     * 父组件参数若未配置分组，则使用父组件名称作为 group（避免直接改缓存中的实体，先 copy 再合并）。
     */
    private void applyDefaultParameterGroup(List<BpmComponentParameter> parameters, String componentName) {
        if (parameters == null || StringUtils.isBlank(componentName)) {
            return;
        }
        for (BpmComponentParameter p : parameters) {
            if (p != null && StringUtils.isBlank(p.getGroup())) {
                p.setGroup(componentName);
            }
        }
    }

    private List<BpmComponentParameter> copyParameterList(List<BpmComponentParameter> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(this::copyParameter).collect(Collectors.toList());
    }

    private BpmComponentParameter copyParameter(BpmComponentParameter p) {
        BpmComponentParameter c = new BpmComponentParameter();
        BeanUtils.copyProperties(p, c);
        return c;
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

    /**
     * 解析继承「命令行」(shell) 父组件时使用的 {@code parentId}，一般为 {@code classpath_shell}。
     */
    public String resolveShellParentComponentId() {
        for (BpmComponent c : CachedComponents.values()) {
            if ("shell".equals(c.getKey())) {
                return c.getId();
            }
        }
        return "classpath_shell";
    }

    /**
     * 解析继承「HTTP 请求」({@code httpRequest}) 父组件时使用的 {@code parentId}，一般为 {@code classpath_httpRequest}。
     */
    public String resolveHttpRequestParentComponentId() {
        for (BpmComponent c : CachedComponents.values()) {
            if ("httpRequest".equals(c.getKey())) {
                return c.getId();
            }
        }
        return "classpath_httpRequest";
    }

    /**
     * 从缓存按 id 解析组件定义，并合并父级参数（与列表接口一致）。
     *
     * @return 不存在时返回 null
     */
    public BpmComponent resolveComponentById(String componentId) {
        BpmComponent c = this.CachedComponents.get(componentId);
        if (c == null) {
            return null;
        }
        return fillComponentProperties(c);
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
