package com.kiwi.cryoems.bpm.movie.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public abstract class AbstractMovieJavaDelegate implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(AbstractMovieJavaDelegate.class);

    private final MovieDelegateVariableService variableService;

    protected AbstractMovieJavaDelegate(MovieDelegateVariableService variableService) {
        this.variableService = variableService;
    }

    @Override
    public final void execute(DelegateExecution execution) {
        Object movie = variableService.requiredVariable(execution, "movie");
        Object task = variableService.requiredVariable(execution, "task");
        Object taskDataset = variableService.optionalVariable(execution, "taskDataset");
        String stepKey = this.getClass().getSimpleName();
        try {
            Map<String, Object> data = doExecute(execution, movie, task, taskDataset);
            variableService.writeData(execution, data);
            log.info("Movie delegate [{}] finished", stepKey);
        } catch (MovieRetryableException ex) {
            variableService.writeExceptionData(execution, ex, true, false);
            log.warn("Movie delegate [{}] retryable error: {}", stepKey, ex.getMessage(), ex);
            throw ex;
        } catch (MovieFatalException ex) {
            variableService.writeExceptionData(execution, ex, false, true);
            log.error("Movie delegate [{}] fatal error: {}", stepKey, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            variableService.writeExceptionData(execution, ex, false, false);
            log.error("Movie delegate [{}] unexpected error: {}", stepKey, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    protected abstract Map<String, Object> doExecute(
            DelegateExecution execution,
            Object movie,
            Object task,
            Object taskDataset
    ) throws Exception;
}
