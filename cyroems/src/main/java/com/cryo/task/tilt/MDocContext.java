package com.cryo.task.tilt;


import com.cryo.model.MDocResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.flow.IFlow;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

import java.util.List;

public class MDocContext extends com.cryo.task.engine.BaseContext<MDocInstance, MDocResult>
{
    private final static List<HandlerKey> stepHandlers = List.of(HandlerKey.MdodParser, HandlerKey.MdocMotionWait, HandlerKey.MdocStack, HandlerKey.MdocCoarseAlign);
    //    private StringWriter slurmCmds = new StringWriter();
    @Getter
    private final MDoc mDoc;

    public MDocContext(ApplicationContext applicationContext, TaskDataset taskDataset, IFlow<MDocInstance, MDocResult> flow, Task task, MDocInstance movie, MDoc mDoc) {
        super(applicationContext, taskDataset, flow, task, movie, MDocResult.class);

        this.mDoc = mDoc;
    }

    @Override
    public boolean autoNext() {
        return !stepHandlers.contains(getCurrentStep().getKey());
    }
}

