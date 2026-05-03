package com.cryo.task.tilt.filter;

import com.cryo.service.FilePathService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.tilt.MDocContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class ExcludeHandler implements Handler<MDocContext>
{

    private final FilePathService filePathService;
    private final SoftwareService softwareService;

    @Override
    public HandlerKey support() {
        return HandlerKey.MdocExclude;
    }

    @Override
    public StepResult handle(MDocContext context) {

//        File mdocWorkDir = filePathService.getMdocWorkDir(context);
//        MDocResult result = context.getResult();
////        String name = context.getInstance().getName();
//        MDoc mDoc = context.getMDoc();
//        StackResult stackResult = result.getStackResult();
//        List<String> files = stackResult.getFiles();
//        String outputFile = stackResult.getOutputFile();
//        String name = FileNameUtil.getPrefix(outputFile);
//        // rebuild check
//        if( mDoc.isManualRebuild() || files.size() != result.getMeta().getTilts().size() ) {
//            return StepResult.success("Rebuilding Skipped");
//        }
//        String excludedMrc = mdocWorkDir + "/" + name + "_excluded.mrc";
//        String excludeTitle = mdocWorkDir + "/" + name + "_excluded.rawtlt";
//        String imageDir = filePathService.getImageWorkDir(context).getAbsolutePath();
//        String exclude_plot_img = mdocWorkDir + "/" + name + "_plot.png";
//        String exclude_thunbnail_img = mdocWorkDir + "/" + name + "_thumbnail.png";
//        result.addImage(new MovieImage(MovieImage.Type.EXCLUDED_PLOT, exclude_plot_img));
//        result.addImage(new MovieImage(MovieImage.Type.EXCLUDED_THUMBNAIL, exclude_thunbnail_img));
//        SoftwareService.CmdProcess exclude = this.softwareService.stack_and_filter(outputFile, stackResult.getTitlFile(), excludedMrc, excludeTitle, imageDir);
//        context.getInstance().addCmd("exclude", TaskStep.of(HandlerKey.MDOC_SLURM), exclude.toCmdStr());
//        result.setOrgStackResult(stackResult);
//        result.setStackResult(new StackResult(files, excludedMrc, excludeTitle));
//        result.setExcudedResult(new ExcludeResult(exclude_plot_img, exclude_thunbnail_img, List.of()));
        return StepResult.success("Excluding");
    }
}
