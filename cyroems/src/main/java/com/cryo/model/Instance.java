package com.cryo.model;

import com.cryo.common.model.DataEntity;
import com.cryo.task.engine.StepCmd;
import com.cryo.task.engine.StepOutput;
import com.cryo.task.engine.TaskStep;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Data

public class Instance extends DataEntity
{
    @Indexed()
    private String task_id;
    private String task_name;
    private List<StepOutput> steps;


    //    private Integer index;
    private String data_id;
    /// /    @Deprecated
    private String file_path;
    //    //    private Integer file_name_index;
    private String name;
    private Date file_create_at;

    //    private String currentConfig;
    private boolean forceReset;

    private TaskStep current_step;
    /** Kiwi/Camunda 流程实例 id（启用远程编排时写入）。 */
    private String external_workflow_instance_id;
    private ErrorStatus error;
    private ProcessStatus process_status;
    private boolean waiting;
    private R<Void> status;
    private List<StepCmd> cmds;


    public void addStep(StepOutput step) {
        List<StepOutput> steps1 = this.getSteps();
//        if( step.getStep() == MovieStep.ERROR ) {

        if( !steps1.isEmpty() ) {
            StepOutput lastStep = steps1.get(steps1.size() - 1);
            if( lastStep.getStep().equals(step.getStep()) ) {
                steps1.remove(steps1.size() - 1);
            }
        }
//        }
        steps1.add(step);
    }

    @JsonIgnore
    public List<StepOutput> getSteps() {
        if( this.steps == null ) {
            this.steps = new ArrayList<>();
        }
        return this.steps;
    }


    public void addCmd(String key, TaskStep taskStep, String cmd) {
        if( this.cmds == null ) {
            this.cmds = new ArrayList<>();
        }
        this.cmds.stream().filter(m -> {
            return m.getKey().equals(key) && m.getExeStep().equals(taskStep);
        }).findAny().ifPresentOrElse(m -> m.setCmd(cmd), () -> this.cmds.add(new StepCmd(key, taskStep, cmd)));
    }

    public List<StepCmd> getCmds() {

        if( this.cmds == null ) {
            this.cmds = new ArrayList<>();
        }
        return new ArrayList<>(this.cmds);
    }


    public ProcessStatus getProcess_status() {
        if( this.process_status == null ) {
            this.process_status = new ProcessStatus(false, null);
        }
        return process_status;
    }


    @Data
    public static class ErrorStatus
    {
        public ErrorStatus() {
            this.error_count = 0;
            this.permanent = false;
        }

        private Integer error_count;
        private Boolean permanent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessStatus
    {

        private boolean processing;
        private Date processing_at;


    }

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof Instance movie) ) {
            return false;
        }
        if( !super.equals(o) ) {
            return false;
        }
        return Objects.equals(getId(), movie.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

}
