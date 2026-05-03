package com.cryo.task.movie;

import com.cryo.dao.InstanceRepository;
import com.cryo.dao.MovieRepository;
import com.cryo.model.Movie;
import com.cryo.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MovieSelector
{


    private final MovieRepository movieRepository;
    private final TaskService taskService;

    private final Duration priorityMovieDuration = Duration.ofHours(12);

    public List<Movie> getHighPriorityMovies(String taskId, int count) {
        Query query = Query.query(Criteria.where("task_id").is(taskId)).addCriteria(InstanceRepository.unprocessed())
                .addCriteria(priorityCriteria()).addCriteria(Criteria.where("index").lt(100))
                .with(Sort.by(Sort.Order.asc("file_create_at")));
        query.limit(count);
        return this.movieRepository.findByQuery(query);
    }

    public List<Movie> getMidPriorityMovies(String taskId, int count) {
        Query query = Query.query(Criteria.where("task_id").is(taskId)).addCriteria(InstanceRepository.unprocessed())
                .addCriteria(priorityCriteria()).with(Sort.by(Sort.Order.asc("file_create_at")));
        query.limit(count);
        return this.movieRepository.findByQuery(query);
    }

    public boolean existOtherHighPriorityMovies(String taskId) {
        List<String> otherTaskIds = getOtherTaskIds(taskId);
        if( otherTaskIds.isEmpty() ) {
            return false;
        }
        Query query = Query.query(Criteria.where("task_id").in(otherTaskIds)).addCriteria(InstanceRepository.unprocessed())
                .addCriteria(priorityCriteria()).addCriteria(Criteria.where("index").lt(100));
        return this.movieRepository.countByQuery(query) > 0;
    }


    public boolean existOtherMidPriorityMovies(String taskId) {
        List<String> otherTaskIds = getOtherTaskIds(taskId);
        if( otherTaskIds.isEmpty() ) {
            return false;
        }
        Query query = Query.query(Criteria.where("task_id").in(otherTaskIds)).addCriteria(InstanceRepository.unprocessed())
                .addCriteria(priorityCriteria());
        return this.movieRepository.countByQuery(query) > 0;
    }

    public CriteriaDefinition priorityCriteria() {
        Date from = Date.from(Instant.now().minus(this.priorityMovieDuration));
        return Criteria.where("file_create_at").gt(from);
    }

    private List<String> getOtherTaskIds(String taskId) {
        return this.taskService.getRunningTasks().stream().filter(t -> {
            return !t.getId().equals(taskId);
        }).map(t -> t.getId()).toList();
    }
}
