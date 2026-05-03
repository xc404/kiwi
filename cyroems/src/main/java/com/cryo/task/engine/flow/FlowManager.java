package com.cryo.task.engine.flow;

import com.cryo.model.Task;
import com.cryo.model.export.ExportTask;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内置 {@link ListFlow} 顺序表：单颗粒 <b>movie</b> 的调度编排已迁至 Kiwi BPM（见 {@link com.cryo.task.movie.MovieEngine}），
 * 本类返回的 {@link IFlow} 仍用于 Handler 上下文及导出/MDoc 等在本进程内推进的流程。
 */
@Service
@SuppressWarnings("deprecation")
public class FlowManager implements InitializingBean
{


//    private final Map<String,IFlow> flowMap = new CaseInsensitiveKeyMap<>();

    public IFlow getMovieFlow(Task task) {
//        if(task.isCryosparc()){
//            return cryosparcFlow();
//        }
        if( task.getIs_tomo() ) {
            return defaultEtFlow();
        }
        return this.defaultFlow();
    }

    public IFlow getMovieExportFlow(Task task, ExportTask exportTask) {
        if( exportTask.isCryosparc() ) {
            return this.getCryosparcExportFlow();
        }
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.INIT),
                TaskStep.of(HandlerKey.RawMovieExport),
                TaskStep.of(HandlerKey.MotionAndCtfExport),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }

    public IFlow getMdocExportFlow(Task task, ExportTask exportTask) {
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.INIT),
                TaskStep.of(HandlerKey.MDOC_EXPORT),
//                TaskStep.of(HandlerKey.MotionAndCtfExport),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }

    public IFlow getCryosparcExportFlow() {
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.INIT),
                TaskStep.of(HandlerKey.CryosparcWait),
                TaskStep.of(HandlerKey.VFM),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    public IFlow defaultFlow() {
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.INIT),
                TaskStep.of(HandlerKey.HEADER),
                TaskStep.of(HandlerKey.MOTION_CORRECTION),
                TaskStep.of(HandlerKey.CTF_ESTIMATION),
                TaskStep.of(HandlerKey.Slurm),
                TaskStep.of(HandlerKey.Result),
                TaskStep.of(HandlerKey.VFM),
//                TaskStep.of(HandlerKey.WriteBack),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }

    public IFlow defaultEtFlow() {
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.INIT),
                TaskStep.of(HandlerKey.HEADER),
                TaskStep.of(HandlerKey.MOTION_CORRECTION),
                TaskStep.of(HandlerKey.CTF_ESTIMATION),
                TaskStep.of(HandlerKey.Slurm),
                TaskStep.of(HandlerKey.Result),
//                TaskStep.of(HandlerKey.VFM),
//                TaskStep.of(HandlerKey.WriteBack),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }

    public IFlow cryosparcFlow() {
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.INIT),
                TaskStep.of(HandlerKey.CryosparcWait),
                TaskStep.of(HandlerKey.VFM),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }

    public IFlow getMDocFlow(Task task) {
        return new ListFlow(List.of(
                TaskStep.of(HandlerKey.MDocInit),
                TaskStep.of(HandlerKey.MdodParser),
                TaskStep.of(HandlerKey.MovieConnect),
                TaskStep.of(HandlerKey.MdocMotionWait),
                TaskStep.of(HandlerKey.MdocStack),
//                TaskStep.of(HandlerKey.MdocExclude),
                TaskStep.of(HandlerKey.MdocCoarseAlign),
                TaskStep.of(HandlerKey.MdocPatchTracking),
                TaskStep.of(HandlerKey.MdocSeriesAlign),
                TaskStep.of(HandlerKey.AlignRecon),
                TaskStep.of(HandlerKey.MDOC_SLURM),
//                TaskStep.of(HandlerKey.MDOC_EXPORT),
                TaskStep.of(HandlerKey.FINISHED)
        ));
    }
}
