package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dto.BpmComponentPreviewConflictItem;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.system.spi.Refreshable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.DependsOn("bpmComponentPluginLoader")
public class BpmComponentService implements InitializingBean, Refreshable
{

    private final BpmComponentDao bpmComponentDao;
    private final BpmComponentDeploymentService bpmComponentDeploymentService;
    @Value("${bpm.component.auto-deploy:true}")
    private boolean autoDeploy;
    @Autowired(required = false)
    private List<BpmComponentProvider> bpmComponentProviderList;

    private final Map<String, BpmComponent> cachedComponents = new ConcurrentHashMap<>();

    public void deployComponent(BpmComponent bpmComponent) {
        this.bpmComponentDeploymentService.deployComponent(bpmComponent);
    }

    public void deploy(BpmComponentProvider bpmComponentProvider) {
        this.bpmComponentDeploymentService.deploy(bpmComponentProvider);
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
        result.setSourceKey(bpmComponent.getSourceKey());
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
        List<BpmComponentParameter> result = new ArrayList<>(self);
        Set<String> selfKeySet = self.stream().map(BpmComponentParameter::getKey).collect(Collectors.toSet());
        parent = parent.stream().filter(pp -> !selfKeySet.contains(pp.getKey())).toList();
        result.addAll(parent);
        return result;
    }

    private BpmComponent getComponent(String parentId) {
        return this.cachedComponents.get(parentId);
    }

    /**
     * 批量预检：按 {@code sourceKey} 判是否与库中或其它草稿冲突。
     */
    public List<BpmComponentPreviewConflictItem> previewConflicts(List<BpmComponent> components) {
        List<BpmComponentPreviewConflictItem> out = new ArrayList<>();
        if (components == null || components.isEmpty()) {
            return out;
        }
        Map<String, Integer> firstBatchIndex = new LinkedHashMap<>();
        for (int i = 0; i < components.size(); i++) {
            BpmComponent d = components.get(i);
            String sk = StringUtils.trimToNull(d != null ? d.getSourceKey() : null);
            if (StringUtils.isBlank(sk)) {
                out.add(new BpmComponentPreviewConflictItem(i, false, null, null, null, null));
                continue;
            }
            Optional<BpmComponent> inDb = bpmComponentDao.findFirstBySourceKey(sk);
            if (inDb.isPresent()) {
                BpmComponent ex = inDb.get();
                out.add(new BpmComponentPreviewConflictItem(
                        i, true, ex.getId(), ex.getName(), sk, null));
                continue;
            }
            if (firstBatchIndex.containsKey(sk)) {
                out.add(new BpmComponentPreviewConflictItem(
                        i, true, null, null, sk, firstBatchIndex.get(sk)));
                continue;
            }
            firstBatchIndex.put(sk, i);
            out.add(new BpmComponentPreviewConflictItem(i, false, null, null, sk, null));
        }
        return out;
    }

    /**
     * 生成不与库冲突的 {@code sourceKey}（用于「新增」保存）。
     */
    public String allocateUniqueSourceKey(String baseSourceKey) {
        if (StringUtils.isBlank(baseSourceKey)) {
            baseSourceKey = "generated";
        }
        String candidate = baseSourceKey;
        int n = 2;
        while (bpmComponentDao.findFirstBySourceKey(candidate).isPresent()) {
            candidate = baseSourceKey + "__" + n;
            n++;
        }
        return candidate;
    }

    /**
     * 解析继承「命令行」(shell) 父组件时使用的 {@code parentId}，一般为 {@code classpath_shell}。
     */
    public String resolveShellParentComponentId() {
        for (BpmComponent c : cachedComponents.values()) {
            if ("shell".equals(c.getKey())) {
                return c.getId();
            }
        }
        return "classpath_shell";
    }

    public String resolveHttpRequestParentComponentId() {
        for (BpmComponent c : cachedComponents.values()) {
            if ("httpRequest".equals(c.getKey())) {
                return c.getId();
            }
        }
        return "classpath_httpRequest";
    }

    /**
     * 解析继承「JDBC/SQL」(jdbcActivity) 父组件时使用的 {@code parentId}。
     */
    public String resolveJdbcParentComponentId() {
        for (BpmComponent c : cachedComponents.values()) {
            if ("jdbcActivity".equals(c.getKey())) {
                return c.getId();
            }
        }
        return "classpath_jdbcActivity";
    }

    public BpmComponent resolveComponentById(String componentId) {
        BpmComponent c = this.cachedComponents.get(componentId);
        if (c == null) {
            return null;
        }
        return fillComponentProperties(c);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        if( this.autoDeploy && this.bpmComponentProviderList != null ) {
            this.bpmComponentProviderList.forEach(this.bpmComponentDeploymentService::deploy);
        }
        refresh();
    }

    @Override
    public void refresh() {
        List<BpmComponent> all = this.bpmComponentDao.findAll();
        // 原子替换：先构建新 map 再 putAll，避免 clear() 与并发读之间的窗口期
        Map<String, BpmComponent> snapshot = new ConcurrentHashMap<>(all.size() * 2);
        all.forEach(component -> snapshot.put(component.getId(), component));
        this.cachedComponents.clear();
        this.cachedComponents.putAll(snapshot);
    }
}
