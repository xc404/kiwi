package com.cryo.task.tilt.patchtracking;

import com.cryo.model.MDocResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.settings.ImodSetting;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.ImodchopcontsArgs;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.cmd.TiltxcorrParams;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.MDocMeta;
import com.cryo.task.tilt.align.CoarseAlignrResult;
import com.cryo.task.tilt.stack.StackResult;
import com.cryo.task.utils.MdocUtils;
import com.cryo.task.utils.NumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatchTracking implements Handler<MDocContext>
{
    private final SoftwareService softwareService;
    private final FilePathService filePathService;

    @Override
    public HandlerKey support() {
        return HandlerKey.MdocPatchTracking;
    }

    @Override
    public StepResult handle(MDocContext context) {
        tiltxcorr(context);
        imodchopconts(context);
        return StepResult.success("success");
    }



    private void tiltxcorr(MDocContext context) {
        Task task = context.getTask();
        MDocResult result = context.getResult();
        ImodSetting etSettings = task.getEtSettings();
        StackResult stackResult = result.getStackResult();
        CoarseAlignrResult coarseAlignrResult = result.getCoarseAlignrResult();
        TiltxcorrParams params = new TiltxcorrParams();
        MDocMeta meta = result.getMeta();
        params.setTilt_axis_angle( NumberUtils.toString(meta.getTiltAxisAngle()));
        MDoc mDoc = context.getMDoc();
        double scale = MdocUtils.getScale(mDoc);
        int border = (int)(etSettings.getBorder_size() * scale);
        int patch_size = (int)(etSettings.getPatch_size() * scale);
        params.setTilt_path(result.getStackResult().getTitlFile());
        params.setSigma1( NumberUtils.toString(etSettings.getTiltxcorr_sigma1()));
        params.setSigma2( NumberUtils.toString(etSettings.getTiltxcorr_sigma2()));
        params.setRadius2( NumberUtils.toString(etSettings.getTiltxcorr_radius2()));
        params.setBorder( NumberUtils.toString(border));
        params.setIt( NumberUtils.toString(etSettings.getTiltxcorr_it()));
        params.setIm( NumberUtils.toString(etSettings.getTiltxcorr_im()));
        params.setPrexf(coarseAlignrResult.getXftoxgOutput());
        params.setPatch_size( NumberUtils.toString(patch_size));
        params.setOverlap( NumberUtils.toString(etSettings.getTiltxcorr_overlap()));
        String name = context.getInstance().getName();
        String output =filePathService.getMdocWorkDir(context) + "/"+ name + "_pt.fid";
        SoftwareService.CmdProcess tiltxcorr = softwareService.tiltxcorr(result.getCoarseAlignrResult().getNewstackOutput(), output, params);
        context.getInstance().addCmd("patch_tracking_tiltxcorr", TaskStep.of(HandlerKey.MDOC_SLURM),tiltxcorr.toCmdStr());
        PatchTrackingResult patchTrackingResult = new PatchTrackingResult();
        patchTrackingResult.setTiltxcorrOutput(output);
        result.setPatchTrackingResult(patchTrackingResult);
    }

    private void imodchopconts(MDocContext context) {
        MDocResult result = context.getResult();
        PatchTrackingResult patchTrackingResult = result.getPatchTrackingResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/"+name + ".fid";
        ImodSetting etSettings = context.getTask().getEtSettings();
        ImodchopcontsArgs args = new ImodchopcontsArgs();
        args.setOverlap( NumberUtils.toString(etSettings.getImodchopconts_overlap()));
        args.setS( NumberUtils.toString(etSettings.getImodchopconts_s()));
        SoftwareService.CmdProcess tiltxcorr = softwareService.imodchopconts(patchTrackingResult.getTiltxcorrOutput(), output,args);
        context.getInstance().addCmd("imodchopconts", TaskStep.of(HandlerKey.MDOC_SLURM),tiltxcorr.toCmdStr());
        patchTrackingResult.setImodchopcontsOutput(output);
    }
}
