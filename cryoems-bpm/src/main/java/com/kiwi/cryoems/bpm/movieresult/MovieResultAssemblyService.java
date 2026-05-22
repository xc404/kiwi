package com.kiwi.cryoems.bpm.movieresult;

import com.kiwi.cryoems.bpm.model.MovieResult;
import com.kiwi.cryoems.bpm.model.motion.MrcFile;
import com.kiwi.cryoems.bpm.movieresult.ctf.CtfPathResolver;
import com.kiwi.cryoems.bpm.movieresult.ctf.CtfResultSection;
import com.kiwi.cryoems.bpm.movieresult.motion.MotionResultSection;
import com.kiwi.cryoems.bpm.movieresult.vfm.VfmResultSection;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 编排 motion / ctf 业务填充 {@link MovieResult}；VFM 由 {@link com.kiwi.cryoems.bpm.activity.CryoemsApplyVfmMovieResultActivity} 单独处理。
 */
@Service
@RequiredArgsConstructor
public class MovieResultAssemblyService {

    private final ThumbnailsDirectoryResolver thumbnailsDirectoryResolver;
    private final MotionResultSection motionSection;
    private final CtfResultSection ctfSection;
    private final CtfPathResolver ctfPathResolver;
    private final VfmResultSection vfmSection;

    public void assemble(MovieResult result, DelegateExecution execution) throws Exception {
        String motionNoDwMrc = WorkflowVariableReader.requireText(execution, "motionNoDwMrc");
        String ctfOutputFile = WorkflowVariableReader.requireText(execution, "ctfOutputFile");
        String motionVersion = WorkflowVariableReader.resolveMotionVersion(execution);
        String microscope = WorkflowVariableReader.resolveMicroscope(execution);

        Path thumbnailsDir = resolveThumbnailsDir(execution);
        Files.createDirectories(thumbnailsDir);

        Double predictDose = WorkflowVariableReader.resolvePredictDose(execution);
        motionSection.process(result, motionNoDwMrc, motionVersion, thumbnailsDir, predictDose);

        String fileName = ctfPathResolver.resolveFileName(ctfOutputFile);
        ctfSection.process(result, ctfOutputFile, fileName, thumbnailsDir, microscope);
    }

    public void applyVfm(MovieResult result, DelegateExecution execution, String vfmLogFile) throws Exception {
        if (StringUtils.isBlank(vfmLogFile)) {
            return;
        }
        Path thumbnailsDir = resolveThumbnailsDir(execution);
        Files.createDirectories(thumbnailsDir);
        vfmSection.process(result, vfmLogFile, thumbnailsDir);
    }

    private Path resolveThumbnailsDir(DelegateExecution execution) {
        return thumbnailsDirectoryResolver.resolve(WorkflowVariableReader.resolveWorkDir(execution));
    }

    private static String resolveMotionNoDwMrc(DelegateExecution execution, MovieResult result) {
        String motionNoDwMrc = WorkflowVariableReader.readText(execution, "motionNoDwMrc");
        if (StringUtils.isNotBlank(motionNoDwMrc)) {
            return motionNoDwMrc.trim();
        }
        if (result.getMotion() != null && result.getMotion().getNo_dw() != null) {
            MrcFile noDw = result.getMotion().getNo_dw();
            if (noDw != null && StringUtils.isNotBlank(noDw.getPath())) {
                return noDw.getPath().trim();
            }
        }
        throw new IllegalArgumentException("需要流程变量 motionNoDwMrc 或 MovieResult.motion.no_dw.path");
    }
}
