package com.cryo.task.tilt.movie;

import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.MDocMeta;
import com.cryo.task.tilt.TiltMeta;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieConnect implements Handler<MDocContext>
{
    private final MovieDataSetRepository movieDataSetRepository;
    private final MDocRepository mDocRepository;

    @Override
    public HandlerKey support() {
        return HandlerKey.MovieConnect;
    }

    @Override
    public StepResult handle(MDocContext context) {
        Task task = context.getTask();
        MDoc mDoc = context.getMDoc();
        MDocMeta meta = mDoc.getMeta();
        List<TiltMeta> tilts = meta.getTilts();
        List<String> names = tilts.stream().map(t -> t.getName()).toList();
        Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(task.getTaskSettings().getDataset_id()), task.getTaskSettings().getDataset_id()));
        query.addCriteria(Criteria.where("name").in(names));
        List<MovieDataset> movieDatasets = this.movieDataSetRepository.findByQuery(query);
        if( movieDatasets.size() != names.size() ) {
            StepResult movieDatasetNotFound = StepResult.success("movie is not ready");
            movieDatasetNotFound.setPersistent(false);
            MDocInstance instance = context.getInstance();
            instance.setStatus(R.fail("movies are not ready"));
//            this.mongoTemplate.save(instance);
            return movieDatasetNotFound;
        }
        Map<String, MovieDataset> datasetMap = movieDatasets.stream().collect(Collectors.toMap(MovieDataset::getName, Function.identity()));
        tilts.forEach(t -> {
            MovieDataset movieDataset = datasetMap.get(t.getName());
            if(movieDataset == null){
                throw new RuntimeException("movie not found");
            }
            t.setDataId(movieDataset.getId());
        });
        if(mDoc.getMovie_data_ids() == null){
            mDoc.setMovie_data_ids(tilts.stream().map(TiltMeta::getDataId).toList());
        }
//        createTilt(context,meta);
        this.mDocRepository.save(mDoc);
        return StepResult.success("MovieDataset is ready");
    }

}
