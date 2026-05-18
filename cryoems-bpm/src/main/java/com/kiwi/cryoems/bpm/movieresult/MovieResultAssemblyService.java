package com.kiwi.cryoems.bpm.movieresult;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.cryoems.bpm.model.MovieResult;
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
 * 编排 motion / ctf / vfm 三段业务，填充 {@link MovieResult}。
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
        String vfmLogFile = ExecutionUtils.getStringInputVariable(execution, "vfmLogFile").orElse(null);
        String motionVersion = WorkflowVariableReader.resolveMotionVersion(execution);
        String microscope = WorkflowVariableReader.resolveMicroscope(execution);

        Path thumbnailsDir =
                thumbnailsDirectoryResolver.resolve(motionNoDwMrc, WorkflowVariableReader.resolveWorkDir(execution));
        Files.createDirectories(thumbnailsDir);

        Double predictDose = WorkflowVariableReader.resolvePredictDose(execution);
        motionSection.process(result, motionNoDwMrc, motionVersion, thumbnailsDir, predictDose);

        String fileName = ctfPathResolver.resolveFileName(ctfOutputFile);
        ctfSection.process(result, ctfOutputFile, fileName, thumbnailsDir, microscope);
        if( !StringUtils.isBlank(vfmLogFile) && Files.exists(Path.of(vfmLogFile)) ) {
            vfmSection.process(result, vfmLogFile, thumbnailsDir);
        }
    }
}
