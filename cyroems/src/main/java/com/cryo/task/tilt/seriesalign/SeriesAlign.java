package com.cryo.task.tilt.seriesalign;

import com.cryo.model.MDocResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.settings.ImodSetting;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.SeriesAlignArgs;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.MDocMeta;
import com.cryo.task.utils.MdocUtils;
import com.cryo.task.utils.NumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeriesAlign implements Handler<MDocContext>
{
    private final SoftwareService softwareService;
    private final FilePathService filePathService;

    @Override
    public HandlerKey support() {
        return HandlerKey.MdocSeriesAlign;
    }

    @Override
    public StepResult handle(MDocContext context) {
        Task task = context.getTask();
        MDocResult result = context.getResult();
        ImodSetting etSettings = task.getEtSettings();
        SeriesAlignArgs params = new SeriesAlignArgs();
        MDocMeta meta = result.getMeta();
        params.setTilt_axis_angle(String.valueOf(meta.getTiltAxisAngle()));
        MDoc mDoc = context.getMDoc();
        double scale = MdocUtils.getScale(mDoc);
//        int border = (int) (etSettings.getBorder() * scale);
        params.setTilt_path(context.getResult().getStackResult().getTitlFile());
        params.setMrc_file(result.getCoarseAlignrResult().getNewstackOutput());
        params.setModel_file(result.getPatchTrackingResult().getImodchopcontsOutput());
        params.setTilt_axis_angle(String.valueOf(meta.getTiltAxisAngle()));
        params.setMax_avg(String.valueOf(etSettings.getError_threshold()));
        double pixel_size = mDoc.getMeta().getPixelSpacing() / 10;
        params.setPixel_size(NumberUtils.toString(pixel_size));
        SoftwareService.CmdProcess series_align = softwareService.series_align(params);
        context.getInstance().addCmd("series_align", TaskStep.of(HandlerKey.MDOC_SLURM), series_align.toCmdStr());
        SeriesAlignResult seriesAlignResult = getSeriesAlignResult(context);
        result.setSeriesAlignResult(seriesAlignResult);

        return StepResult.success("success");
    }

    private  SeriesAlignResult getSeriesAlignResult(MDocContext context) {
        SeriesAlignResult seriesAlignResult = new SeriesAlignResult();
        String name = filePathService.getMdocWorkDir(context) + "/" + context.getInstance().getName();
        seriesAlignResult.setFidXYZOutput(name+"fid.xyz");
        seriesAlignResult.setModelFileOutput(name+".3dmod");
        seriesAlignResult.setResidualFileOutput(name+".resid");
        seriesAlignResult.setTiltFileOutput(name+".tlt");
        seriesAlignResult.setXAxisTiltOutput(name+".xtilt");
        seriesAlignResult.setTransformOutput(name+".tltxf");
        seriesAlignResult.setFilledInModelOutput(name+"_nogaps.fid");
        return seriesAlignResult;
    }
}
