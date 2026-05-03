package com.cryo.task.export.cryosparc;

import com.cryo.model.export.ExportMovie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.export.MovieExportContext;
import com.cryo.task.movie.handler.wait.AsyncHandler;
import org.springframework.stereotype.Service;

@Service
public class CryosparcWaitHandler implements AsyncHandler<MovieExportContext>
{
    @Override
    public HandlerKey support() {
        return HandlerKey.CryosparcWait;
    }

    @Override
    public StepResult handle(MovieExportContext context) {
//        ExportResult result = context.getResult();
//        String ctfFile = Optional.ofNullable(result).map(r -> r.getCtfEstimation())
//                .map(ctf -> ctf.getOutputFile()).orElse(null);
        ExportMovie instance = context.getInstance();
        ExportMovie.CryospacStatus cryospacStatus = instance.getCryospacStatus();
        if( cryospacStatus != null ) {
            instance.setCryospacStatus(null);
        }
        if( cryospacStatus != ExportMovie.CryospacStatus.Complete ) {
            return StepResult.waitCondition("Waiting for cryosparc");
        }
        return StepResult.success("Cryosparc completed");
    }
}
