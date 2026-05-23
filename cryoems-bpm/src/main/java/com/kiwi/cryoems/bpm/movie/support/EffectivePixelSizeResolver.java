package com.kiwi.cryoems.bpm.movie.support;

import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 计算 MotionCor 校正后的有效像素尺寸，对齐 cyroems {@code TaskUtils#getP_size}。
 */
@Component
public class EffectivePixelSizeResolver {

    private static final String TITAN3_FALCON = "Titan3_falcon";

    public double resolve(DelegateExecution execution) {
        double basePSize = readPSize(execution);
        double binning = readBinning(execution);
        String microscope = WorkflowVariableReader.resolveMicroscope(execution);
        Integer eerSampling = readEerSampling(execution);
        if (TITAN3_FALCON.equals(microscope) && eerSampling != null && eerSampling != 0) {
            binning = binning / eerSampling;
        }
        return basePSize * binning;
    }

    private static double readPSize(DelegateExecution execution) {
        Object direct = execution.getVariable("p_size");
        if (direct == null) {
            Map<String, Object> task = WorkflowVariableReader.readMap(execution, "task");
            Object fromTask = task.get("p_size");
            if (fromTask == null) {
                throw new IllegalArgumentException("流程变量 p_size 不能为空");
            }
            direct = fromTask;
        }
        return toDouble(direct, "p_size");
    }

    private static double readBinning(DelegateExecution execution) {
        Object value = execution.getVariable("binning_factor");
        if (value == null) {
            Map<String, Object> task = WorkflowVariableReader.readMap(execution, "task");
            value = task.get("binning_factor");
        }
        if (value == null) {
            return 1.0;
        }
        return toDouble(value, "binning_factor");
    }

    private static Integer readEerSampling(DelegateExecution execution) {
        Object value = execution.getVariable("eer_sampling");
        if (value == null) {
            value = execution.getVariable("eerSampling");
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String text = value.toString().trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("eer_sampling 不是合法整数: " + value, e);
        }
    }

    private static double toDouble(Object value, String name) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        String text = value.toString().trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 不是合法数字: " + value, e);
        }
    }
}
