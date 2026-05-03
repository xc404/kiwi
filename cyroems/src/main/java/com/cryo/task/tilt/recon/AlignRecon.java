package com.cryo.task.tilt.recon;

import com.cryo.model.MDocResult;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.NewstackArgs;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.cmd.TiltArgs;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.tilt.MDocContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlignRecon implements Handler<MDocContext>
{
    private final SoftwareService softwareService;
    private final FilePathService filePathService;

    @Override
    public HandlerKey support() {
        return HandlerKey.AlignRecon;
    }

    @Override
    public StepResult handle(MDocContext context) {
        xfproduct(context);
        patch2imod(context);
        newstack1(context);
        newstack2(context);
        tilt(context);
        binvol(context);
        align_recon(context);
        return StepResult.success("success");
    }

    private void xfproduct(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output =filePathService.getMdocWorkDir(context) + "/" +name + "_fid.xf";
        SoftwareService.CmdProcess xfproduct = softwareService.xfproduct(result.getCoarseAlignrResult().getXftoxgOutput(),
                result.getSeriesAlignResult().getTransformOutput(),
                output
        );
        context.getInstance().addCmd("xfproduct", TaskStep.of(HandlerKey.MDOC_SLURM), xfproduct.toCmdStr());
        AlignReconResult alignReconResult = new AlignReconResult();
        alignReconResult.setXfproductOutput(output);
        result.setAlignReconResult(alignReconResult);
    }

    private void patch2imod(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/" +name + ".resmod";
        SoftwareService.CmdProcess patch2imod = softwareService.patch2imod(result.getSeriesAlignResult().getResidualFileOutput(),
                output
        );
        context.getInstance().addCmd("patch2imod", TaskStep.of(HandlerKey.MDOC_SLURM), patch2imod.toCmdStr());
        result.getAlignReconResult().setPatch2imodOutput(output);
    }

    private void newstack1(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/" +name + "_ali.mrc";
        String path = context.getResult().getStackResult().getOutputFile();
        NewstackArgs newstackArgs = new NewstackArgs();
        newstackArgs.setXf(result.getAlignReconResult().getXfproductOutput());
        newstackArgs.setBin("4");
        SoftwareService.CmdProcess newstack1 = softwareService.newStack(path,
                output,
                newstackArgs
        );
        context.getInstance().addCmd("align_recon_newstack1", TaskStep.of(HandlerKey.MDOC_SLURM), newstack1.toCmdStr());
        result.getAlignReconResult().setStack1Output(output);
    }

    private void newstack2(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/" +name + "_ali_bin1.mrc";
        String path = context.getResult().getStackResult().getOutputFile();
        NewstackArgs newstackArgs = new NewstackArgs();
        newstackArgs.setBin("1");
        newstackArgs.setXf(result.getAlignReconResult().getXfproductOutput());
        SoftwareService.CmdProcess newstack2 = softwareService.newStack(path,
                output,
                newstackArgs
        );
        context.getInstance().addCmd("align_recon_newstack2", TaskStep.of(HandlerKey.MDOC_SLURM), newstack2.toCmdStr());
        result.getAlignReconResult().setStack2Output(output);
    }

    private void tilt(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/" +name + "_full_rec_bin4.mrc";
        String path = result.getAlignReconResult().getStack1Output();
        TiltArgs args = new TiltArgs();
        args.setTilt_path(context.getResult().getStackResult().getTitlFile());
        args.setXtilt(result.getSeriesAlignResult().getTransformOutput());
        SoftwareService.CmdProcess cmdProcess = softwareService.tilt(path,
                output,
                args
        );
        context.getInstance().addCmd("align_tilt", TaskStep.of(HandlerKey.MDOC_SLURM), cmdProcess.toCmdStr());
        result.getAlignReconResult().setTiltOutput(output);
    }
    private void binvol(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output = filePathService.getMdocWorkDir(context) + "/" +name + "_full_rec_bin4.mrc";
        String path = result.getAlignReconResult().getTiltOutput();
        SoftwareService.CmdProcess cmdProcess = softwareService.binvol(path,
                output
        );
        context.getInstance().addCmd("align_binvol", TaskStep.of(HandlerKey.MDOC_SLURM), cmdProcess.toCmdStr());
        result.getAlignReconResult().setBinvolOutput(output);
    }

    private void align_recon(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        String output = filePathService.getImageWorkDir(context) + "/" +name + "_full_rec_bin8_unit8";
        String path = result.getAlignReconResult().getBinvolOutput();
        SoftwareService.CmdProcess cmdProcess = softwareService.align_recon(path,
                output+".mrc"
        );
        context.getInstance().addCmd("align_recon_align_recon", TaskStep.of(HandlerKey.MDOC_SLURM), cmdProcess.toCmdStr());
        result.getAlignReconResult().setAlign_reconOutput(output+".mrc");
        result.getAlignReconResult().setAlign_recon_x_y_view(output+"_xy.png");
        result.getAlignReconResult().setAlign_recon_y_z_view(output+"_yz.png");
        result.getAlignReconResult().setAlign_recon_x_z_view(output+"_xz.png");
    }

}
