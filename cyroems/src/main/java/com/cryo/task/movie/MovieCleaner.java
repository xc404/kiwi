package com.cryo.task.movie;

import com.cryo.model.Movie;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.motion.MotionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@Slf4j
public class MovieCleaner
{


    public void delete(String path) {
        if( StringUtils.isNotBlank(path) ) {
            try {
                File file = new File(path);
                file.delete();
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        }

    }
}
