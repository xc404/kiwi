package com.kiwi.cryoems.bpm.movieresult.vfm;

import com.kiwi.cryoems.bpm.model.MovieResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * VFM 业务：路径推断、PNG 复制、写入 {@link MovieResult}、日志解析。
 */
@Service
@RequiredArgsConstructor
public class VfmResultSection {

    private final VfmPathResolver pathResolver;
    private final VfmPngCopier pngCopier;
    private final VfmResultApplier applier;
    private final VfmLogParser logParser;

    public void process(MovieResult result, String vfmLogFile, Path thumbnailsDir) throws Exception {
        if( StringUtils.isEmpty(vfmLogFile) ){
            return;
        }
        VfmPaths paths = pathResolver.resolve(vfmLogFile, thumbnailsDir);
        pngCopier.copyToThumbnails(paths);
        applier.apply(result, paths);
        logParser.parse(result.getVfmResult());
    }
}
