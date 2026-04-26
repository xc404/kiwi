package com.kiwi.cryoems.bpm.movie.delegate;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@ComponentDescription(
        name = "Movie Header",
        group = "CryoEMS",
        version = "1.0",
        description = "校验 movie 文件并生成 header 相关输出变量",
        outputs = {
                @ComponentParameter(
                        key = "headerFilePath",
                        name = "headerFilePath",
                        type = "string",
                        description = "Header 处理目标文件路径"),
                @ComponentParameter(
                        key = "movieFilePath",
                        name = "movieFilePath",
                        type = "string",
                        description = "Movie 文件路径"),
                @ComponentParameter(
                        key = "fileName",
                        name = "fileName",
                        type = "string",
                        description = "Movie 文件名")
        }
)
@Component("movieHeaderJavaDelegate")
public class MovieHeaderJavaDelegate extends AbstractMovieJavaDelegate {
    public MovieHeaderJavaDelegate(MovieDelegateVariableService variableService) {
        super(variableService);
    }

    @Override
    protected Map<String, Object> doExecute(
            DelegateExecution execution,
            Object movie,
            Object task,
            Object taskDataset
    ) {
        if (MovieDelegateValueHelper.bool(execution.getVariable("movieDelegateRollback"))) {
            Map<String, Object> rollback = new LinkedHashMap<>();
            rollback.put("headerSkipped", true);
            rollback.put("headerSkipReason", "rollback_enabled");
            return rollback;
        }
        if (!(movie instanceof Map<?, ?> movieMap)) {
            throw new MovieFatalException("流程变量 movie 不是 Map/Object 结构");
        }
        String filePath = asText(movieMap.get("file_path"));
        if (!StringUtils.hasText(filePath)) {
            throw new MovieFatalException("movie.file_path 不能为空");
        }
        File source = new File(filePath);
        if (!source.exists()) {
            throw new MovieFatalException("Mrc file not found: " + filePath);
        }

        String fileName = asText(movieMap.get("file_name"));
        String resolvedName = StringUtils.hasText(fileName) ? fileName : source.getName();
        String headerFilePath = source.getParent() == null
                ? resolvedName + "_meta.txt"
                : new File(source.getParent(), resolvedName + "_meta.txt").getAbsolutePath();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("movieFilePath", source.getAbsolutePath());
        data.put("headerFilePath", headerFilePath);
        data.put("fileName", resolvedName);
        return data;
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
