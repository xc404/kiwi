package com.cryo.task.export.handler.batch;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.thread.BlockPolicy;
import com.cryo.model.Task;
import com.cryo.task.support.ExportSupport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class BatchExportSupport implements InitializingBean
{
    private final ExportSupport exportSupport;
    private ThreadPoolTaskExecutor executor;
    @Getter
    private int defaultBatchSize = 10;

    public CompletableFuture<Void> export(List<String> files, File destination, Task task, int batchSize) {
        List<List<String>> split = ListUtil.split(files, batchSize);
        return CompletableFuture.allOf(split.stream().map(strings -> {
            return export(strings, destination, task);
        }).toList().toArray(new CompletableFuture[]{}));
    }

    public CompletableFuture<String> export(List<String> files, File destination, Task task) {

        return this.executor.submitCompletable(() -> {
            return this.exportSupport.copyToUser(task, files, destination);
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("batch-export");
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(-1);
        executor.setRejectedExecutionHandler(new BlockPolicy());
        executor.initialize();
    }
}
