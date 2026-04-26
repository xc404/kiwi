package com.kiwi.cryoems.bpm.movie.delegate;

import com.kiwi.bpmn.component.slurm.SbatchConfig;
import org.camunda.bpm.engine.delegate.DelegateExecution;

final class MovieSlurmConfigSupport {
    private MovieSlurmConfigSupport() {
    }

    static SbatchConfig buildConfig(
            DelegateExecution execution,
            String jobName,
            String outputFile,
            String errorFile,
            String partitionVar,
            String timeVar,
            String gresVar,
            String taskNumVar
    ) {
        SbatchConfig cfg = new SbatchConfig();
        cfg.setJobName(jobName);
        cfg.setOutput_file(outputFile);
        cfg.setError_file(errorFile);
        cfg.setPartition(text(execution, partitionVar));
        cfg.setTime(text(execution, timeVar));
        cfg.setGres(text(execution, gresVar));
        cfg.setTask_num(intValue(execution.getVariable(taskNumVar)));
        cfg.setChdir(text(execution, "slurm_chdir"));
        return cfg;
    }

    static String text(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
