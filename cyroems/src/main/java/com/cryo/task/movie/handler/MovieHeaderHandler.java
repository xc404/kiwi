package com.cryo.task.movie.handler;

import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import com.cryo.model.Movie;
import com.cryo.model.MovieResult;
import com.cryo.model.MrcMetadata;
import com.cryo.service.FilePathService;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.movie.MrcFileMetaSupport;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Optional;

@Service
@AllArgsConstructor
public class MovieHeaderHandler implements Handler<MovieContext>
{
    private final MrcFileMetaSupport mrcFileMetaSupport;
    private final FilePathService pathService;

    @Override
    public HandlerKey support() {
        return HandlerKey.HEADER;
    }

    @Override
    public StepResult handle(MovieContext movieContext) {
        MrcMetadata mrcMetadata = Optional.ofNullable(movieContext.getResult()).map(m -> m.getMrcMetadata()).orElse(null);
        if( mrcMetadata == null || movieContext.forceReset() ) {
            return doHeader(movieContext);

        } else {
            movieContext.getResult().setMrcMetadata(mrcMetadata);
            return StepResult.success("Mrc file header copied");
        }

    }

    private StepResult doHeader(MovieContext movieContext) {
        try {
            File workDir = pathService.getWorkDir(movieContext);
            Movie movie = movieContext.getMovie();
            if( !new File(movie.getFile_path()).exists() ) {
                throw new FatalException("Mrc file not found");
            }
            File headerFile = new File(workDir, movie.getFile_name() + "_meta.txt");
            MrcMetadata mrcMetadata = this.mrcFileMetaSupport.getMetaData(movie.getFile_path(), headerFile);
//            movie.setFile_create_at(FileUtils.lastModified(new File(movie.getFile_path())));
            MovieResult result = movieContext.getResult();
            result.setMrcMetadata(mrcMetadata);
            mrcMetadata.setFile(headerFile.getAbsolutePath());
            return StepResult.success("Mrc file header processed");
        } catch( Exception e ) {
            throw new RetryException(e);
        }
    }
}
