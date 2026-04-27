package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.utils.BpmComponentDeploymentSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BPM 组件定义与 Mongo 的同步部署（classpath 等 {@link BpmComponentProvider} 来源）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BpmComponentDeploymentService {

    private final BpmComponentDao bpmComponentDao;

    @Value("${bpm.component.delete-not-exist:true}")
    private boolean deleteNotExist;

    public void deployComponent(BpmComponent bpmComponent) {
        if (StringUtils.isBlank(bpmComponent.getId())) {
            bpmComponent.setId(bpmComponent.getSource() + "_" + bpmComponent.getKey());
        }
        String sig = BpmComponentDeploymentSignature.compute(bpmComponent);
        bpmComponent.setDeploymentSignature(sig);
        log.info(
                "BPM 组件单条部署: id={} key={} source={} name={} type={} signaturePrefix={}",
                bpmComponent.getId(),
                bpmComponent.getKey(),
                bpmComponent.getSource(),
                bpmComponent.getName(),
                bpmComponent.getType(),
                signaturePrefix(sig));
        this.bpmComponentDao.save(bpmComponent);
        log.info("BPM 组件单条部署已落库: id={}", bpmComponent.getId());
    }

    public void deploy(BpmComponentProvider bpmComponentProvider) {
        String source = bpmComponentProvider.getSource();
        List<BpmComponent> bpmComponents = bpmComponentProvider.getComponents();
        log.info(
                "BPM 组件批量部署开始: source={} incomingCount={} deleteNotExist={}",
                source,
                bpmComponents.size(),
                deleteNotExist);

        bpmComponents.forEach(component -> {
            if (StringUtils.isBlank(component.getId())) {
                component.setId(component.getSource() + "_" + component.getKey());
            }
        });

        List<BpmComponent> current = this.bpmComponentDao.findBy(Query.query(Criteria.where("source").is(source)));
        Map<String, BpmComponent> byId = current.stream().collect(Collectors.toMap(BpmComponent::getId, c -> c, (a, b) -> a));
        log.debug(
                "BPM 组件批量部署: source={} dbExistingCount={} dbIdsSample={}",
                source,
                current.size(),
                sampleIds(current, 10));

        int removedCount = 0;
        if (deleteNotExist) {
            List<BpmComponent> toDelete = current.stream()
                    .filter(component -> bpmComponents.stream().noneMatch(c -> Objects.equals(component.getKey(), c.getKey())))
                    .toList();
            if (!toDelete.isEmpty()) {
                log.info(
                        "BPM 组件批量部署: source={} 将删除库中多余定义 count={} detail={}",
                        source,
                        toDelete.size(),
                        formatRemovedSummary(toDelete));
                this.bpmComponentDao.deleteAll(toDelete);
                for (BpmComponent removed : toDelete) {
                    byId.remove(removed.getId());
                }
                removedCount = toDelete.size();
            }
        }

        int unchanged = 0;
        List<BpmComponent> toSave = new ArrayList<>();
        for (BpmComponent inc : bpmComponents) {
            String sig = BpmComponentDeploymentSignature.compute(inc);
            BpmComponent existing = byId.get(inc.getId());
            if (existing != null && Objects.equals(sig, existing.getDeploymentSignature())) {
                unchanged++;
                log.debug(
                        "BPM 组件批量部署跳过未变更: source={} id={} key={}",
                        source,
                        inc.getId(),
                        inc.getKey());
                continue;
            }
            inc.setDeploymentSignature(sig);
            toSave.add(inc);
            log.info(
                    "BPM 组件批量部署待写入: source={} id={} key={} name={} reason={} signaturePrefix={}",
                    source,
                    inc.getId(),
                    inc.getKey(),
                    inc.getName(),
                    existing == null ? "新增" : "签名变更",
                    signaturePrefix(sig));
        }

        if (!toSave.isEmpty()) {
            this.bpmComponentDao.saveAll(toSave);
            log.info(
                    "BPM 组件批量部署已落库: source={} savedCount={} savedKeys={}",
                    source,
                    toSave.size(),
                    toSave.stream().map(BpmComponent::getKey).collect(Collectors.joining(", ")));
        } else {
            log.info("BPM 组件批量部署无需写库: source={} (全部与库中签名一致)", source);
        }

        log.info(
                "BPM 组件批量部署结束: source={} incoming={} removed={} unchanged={} saved={}",
                source,
                bpmComponents.size(),
                removedCount,
                unchanged,
                toSave.size());
    }

    private static String signaturePrefix(String sig) {
        if (sig == null) {
            return "null";
        }
        return sig.length() <= 12 ? sig : sig.substring(0, 12) + "...";
    }

    private static String sampleIds(List<BpmComponent> list, int max) {
        return list.stream().map(BpmComponent::getId).limit(max).collect(Collectors.joining(", "));
    }

    private static String formatRemovedSummary(List<BpmComponent> toDelete) {
        return toDelete.stream()
                .map(c -> c.getId() + "(key=" + c.getKey() + ")")
                .limit(30)
                .collect(Collectors.joining(", "))
                + (toDelete.size() > 30 ? " ... 共" + toDelete.size() + "条" : "");
    }
}
