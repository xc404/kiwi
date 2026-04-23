package com.cryo.task.tilt.movie;

import com.cryo.common.mongo.BaseRepositoryImpl;
import com.cryo.common.mongo.MongoTemplate;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.model.MDocResult;
import com.cryo.model.MovieResult;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.MDocMeta;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MotionWait implements Handler<MDocContext>
{
    private final MovieResultRepository movieResultRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public HandlerKey support() {
        return HandlerKey.MdocMotionWait;
    }

    @Override
    public StepResult handle(MDocContext context) {

        MDocResult result = context.getResult();

//        if(!context.forceReset() && result.getMeta() != null){
//            return StepResult.success("skipped");
//        }
        MDoc mDoc = context.getMDoc();
        MDocMeta meta = mDoc.getMeta();
        List<String> ids = meta.getTilts().stream().map(t -> t.getDataId()).toList();
        Query query = Query.query(Criteria.where("movie_data_id").in(ids).and("config_id").is(context.getTask().getDefault_config_id()));
        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(query);
        long motionCompleted = movieResults.stream().filter(m -> {
            return Optional.ofNullable(m.getMotion()).map(p -> p.getPredict_dose()).isPresent();
        }).count();
        if(motionCompleted != meta.getTilts().size()){
            StepResult movieDatasetNotFound = StepResult.success("motion not completed");
            movieDatasetNotFound.setPersistent(false);
            MDocInstance instance = context.getInstance();
            instance.setStatus(R.fail("motion not completed"));
//            this.mongoTemplate.save(instance);
            return movieDatasetNotFound;
        }
        Map<String, MovieResult> movieResultMap = movieResults.stream()
                .collect(Collectors.toMap(MovieResult::getData_id, Function.identity(), MovieResult::pickNewer));

        meta.getTilts().forEach(t -> {
            MovieResult movieResult = movieResultMap.get(t.getDataId());
//            t.setDataId(result.getData_id());
            t.setMotionResultId(movieResult.getId());
        });
        result.setMeta(meta);
        return StepResult.success("motion wait completed");
    }
}
