package com.cryo.task.tilt.align;

import com.cryo.model.MDocResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.settings.ImodSetting;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.NewstackArgs;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.cmd.TiltxcorrParams;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.MDocMeta;
import com.cryo.task.tilt.stack.StackResult;
import com.cryo.task.utils.NumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CoarseAlign implements Handler<MDocContext>
{
    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    @Override
    public HandlerKey support() {
        return HandlerKey.MdocCoarseAlign;
    }

    @Override
    public StepResult handle(MDocContext context) {
        tiltxcorr(context);
        xftoxg(context);
        newStack(context);
        return StepResult.success("Coarse align");
    }


    private void tiltxcorr(MDocContext context) {
        Task task = context.getTask();
        MDocResult result = context.getResult();
        ImodSetting etSettings = task.getEtSettings();
        StackResult stackResult = result.getStackResult();
        TiltxcorrParams params = new TiltxcorrParams();
        MDocMeta meta = result.getMeta();
        params.setTilt_axis_angle( NumberUtils.toString(meta.getTiltAxisAngle()));
        MDoc mDoc = context.getMDoc();
        params.setTilt_path(result.getStackResult().getTitlFile());
        params.setSigma1( NumberUtils.toString(etSettings.getTiltxcorr_sigma1()));
        params.setSigma2( NumberUtils.toString(etSettings.getTiltxcorr_sigma2()));
        params.setRadius2( NumberUtils.toString(etSettings.getTiltxcorr_radius2()));
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/"+name + ".prexf";
        SoftwareService.CmdProcess tiltxcorr = softwareService.tiltxcorr(stackResult.getOutputFile(), output, params);
        context.getInstance().addCmd("tiltxcorr", TaskStep.of(HandlerKey.MDOC_SLURM),tiltxcorr.toCmdStr());
        CoarseAlignrResult coarseAlignrResult = new CoarseAlignrResult();
        coarseAlignrResult.setTiltxcorrOutput(output);
        result.setCoarseAlignrResult(coarseAlignrResult);
    }

    private void xftoxg(MDocContext context) {
        MDocResult result = context.getResult();
        CoarseAlignrResult coarseAlignrResult1 = result.getCoarseAlignrResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/"+name + ".prexg";
        SoftwareService.CmdProcess tiltxcorr = softwareService.xftoxg(coarseAlignrResult1.getTiltxcorrOutput(), output,"0");
        context.getInstance().addCmd("xftoxg", TaskStep.of(HandlerKey.MDOC_SLURM),tiltxcorr.toCmdStr());
        coarseAlignrResult1.setXftoxgOutput(output);
    }

    private void newStack(MDocContext context) {
        Task task = context.getTask();
        MDocResult result = context.getResult();
        ImodSetting etSettings = task.getEtSettings();
        CoarseAlignrResult coarseAlignrResult = result.getCoarseAlignrResult();
        NewstackArgs params = new NewstackArgs();
        MDocMeta meta = result.getMeta();
        params.setBin( NumberUtils.toString(meta.getTiltAxisAngle()));
        params.setFl( NumberUtils.toString(etSettings.getNewstack_fl()));
        params.setBin( NumberUtils.toString(etSettings.getNewstack_bin()));
        params.setMo( NumberUtils.toString(etSettings.getNewstack_mo()));
        params.setIm( NumberUtils.toString(etSettings.getNewstack_im()));
        params.setPrexg(coarseAlignrResult.getXftoxgOutput());
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/"+name + "_preali.mrc";
        SoftwareService.CmdProcess cmdProcess = softwareService.newStack(result.getStackResult().getOutputFile(), output,params);
        context.getInstance().addCmd("newstack", TaskStep.of(HandlerKey.MDOC_SLURM),cmdProcess.toCmdStr());
        coarseAlignrResult.setNewstackOutput(output);
    }

}
