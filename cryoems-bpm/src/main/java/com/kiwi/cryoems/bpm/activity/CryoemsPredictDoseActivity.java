package com.kiwi.cryoems.bpm.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.support.EffectivePixelSizeResolver;
import com.kiwi.cryoems.bpm.support.PredictDoseRunner;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对 MotionCor2 no_dw MRC 执行 Titan contrast_mean，换算 predict dose 并写入流程变量。
 * 逻辑对齐 cyroems {@code MotionCor2Support#predictDose}。
 */
@ComponentDescription(
        name = "CryoEMS Predict Dose",
        group = "CryoEM",
        version = "1.0",
        description =
                "对 MotionCor2 主输出 no_dw MRC 执行显微镜对应的 contrast_mean，"
                        + "按 5.1 * dose / pSize² 写入 predictDose。"
                        + "有效像素尺寸由 p_size、binning_factor、Titan3 的 eer_sampling 计算。",
        inputs = {
                @ComponentParameter(
                        key = "motionNoDwMrc",
                        name = "MotionCor2 no_dw MRC",
                        description = "MotionCor2 主输出 .mrc 路径（no_dw）",
                        required = true),
                @ComponentParameter(
                        key = "microscope",
                        name = "显微镜类型",
                        description = "显微镜标识（如 Titan1_k3）；可省略时从 task.microscope 读取"),
                @ComponentParameter(
                        key = "p_size",
                        name = "PSize",
                        description = "原始像素尺寸；可省略时从 task.p_size 读取"),
                @ComponentParameter(
                        key = "binning_factor",
                        name = "Binning",
                        description = "binning 因子，默认 1"),
                @ComponentParameter(
                        key = "eer_sampling",
                        name = "EER sampling",
                        description = "Titan3_falcon 的 EER sampling；非零时参与有效像素尺寸计算"),
                @ComponentParameter(
                        key = "predictDoseCommand",
                        name = "Predict dose 命令",
                        description = "覆盖默认的 contrast_mean 可执行路径")
        },
        outputs = {
                @ComponentParameter(
                        key = "predictDose",
                        name = "predictDose",
                        description = "换算后的 predict dose",
                        schema = @Schema(defaultValue = "predictDose"))
        })
@Component("cyroemsPredictDoseActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsPredictDoseActivity implements JavaDelegate {

    private final PredictDoseRunner predictDoseRunner;
    private final EffectivePixelSizeResolver effectivePixelSizeResolver;

    @Value("${cryoems.bpm.predict-dose.enabled:true}")
    private boolean predictDoseEnabled;

    @Override
    public void execute(DelegateExecution execution) {
        if (!predictDoseEnabled) {
            log.debug("predict dose 已禁用（cryoems.bpm.predict-dose.enabled=false），跳过");
            return;
        }
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (Exception e) {
            throw new BpmnError("PREDICT_DOSE", "predict dose 失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) throws Exception {
        String motionNoDwMrc = WorkflowVariableReader.requireText(execution, "motionNoDwMrc");
        String microscope = resolveMicroscope(execution);
        double effectivePixelSize = effectivePixelSizeResolver.resolve(execution);
        String commandOverride = ExecutionUtils.getStringInputVariable(execution, "predictDoseCommand").orElse(null);

        double predictDose = predictDoseRunner.predict(motionNoDwMrc, microscope, effectivePixelSize, commandOverride);
        execution.setVariable("predictDose", predictDose);
        log.info("predict dose 完成: {} (microscope={}, pSize={})", predictDose, microscope, effectivePixelSize);
    }

    private static String resolveMicroscope(DelegateExecution execution) {
        String microscope = WorkflowVariableReader.resolveMicroscope(execution);
        if (microscope == null || microscope.isBlank()) {
            throw new IllegalArgumentException("需要流程变量 microscope 或 task.microscope");
        }
        return microscope;
    }
}
