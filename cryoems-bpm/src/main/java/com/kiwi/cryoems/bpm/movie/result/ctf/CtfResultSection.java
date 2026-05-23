package com.kiwi.cryoems.bpm.movie.result.ctf;

import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * CTFFIND5 业务：路径推断、缩略图生成、写入 {@link MovieResult}、日志解析与 stigma 计算、校验。
 */
@Service
@RequiredArgsConstructor
public class CtfResultSection {

    private final CtfPathResolver pathResolver;
    private final CtfThumbnailGenerator thumbnailGenerator;
    private final CtfResultApplier applier;
    private final Ctffind5LogParser logParser;
    private final CtffindStigmaCalculator stigmaCalculator;
    private final CtfResultValidator validator;

    public void process(MovieResult result, String ctfOutputFile, String fileName, Path thumbnailsDir, String microscope)
            throws Exception {
        CtfPaths paths = pathResolver.resolve(ctfOutputFile, fileName, thumbnailsDir);
        thumbnailGenerator.generate(paths);
        applier.apply(result, paths);
        logParser.parse(result.getCtfEstimation());
        stigmaCalculator.calculate(microscope, result.getCtfEstimation());
        validator.validate(result);
    }
}
