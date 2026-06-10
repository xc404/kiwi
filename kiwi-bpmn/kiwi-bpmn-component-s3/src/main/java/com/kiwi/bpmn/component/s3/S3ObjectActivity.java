package com.kiwi.bpmn.component.s3;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@ComponentDescription(
        name = "S3 对象读写",
        group = "存储",
        version = "1.0",
        description = "AWS S3 或 MinIO 上传/下载对象；密钥建议用项目环境变量",
        inputs = {
                @ComponentParameter(key = "endpoint", description = "可选 MinIO 端点，如 http://localhost:9000"),
                @ComponentParameter(key = "region", description = "区域", required = true),
                @ComponentParameter(key = "access_key", description = "Access Key", required = true),
                @ComponentParameter(key = "secret_key", description = "Secret Key", required = true),
                @ComponentParameter(key = "bucket", description = "桶名", required = true),
                @ComponentParameter(key = "object_key", description = "对象键", required = true),
                @ComponentParameter(
                        key = "action",
                        description = "upload 或 download",
                        required = true,
                        schema = @Schema(defaultValue = "download")),
                @ComponentParameter(key = "local_path", description = "本地文件路径（upload/download）"),
                @ComponentParameter(key = "content", description = "upload 时文本内容（与 local_path 二选一）")
        },
        outputs = {
                @ComponentParameter(key = "bytes", schema = @Schema(defaultValue = "bytes")),
                @ComponentParameter(key = "content", schema = @Schema(defaultValue = "content"))
        })
@Component("s3Object")
public class S3ObjectActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String endpoint = ExecutionUtils.getStringInputVariable(execution, "endpoint").orElse(null);
        String region = require(execution, "region");
        String accessKey = require(execution, "access_key");
        String secretKey = require(execution, "secret_key");
        String bucket = require(execution, "bucket");
        String objectKey = require(execution, "object_key");
        String action = require(execution, "action").toLowerCase(Locale.ROOT);

        S3ClientBuilder builder =
                S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(
                            S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        try (S3Client client = builder.build()) {
            switch (action) {
                case "upload" -> upload(client, execution, bucket, objectKey);
                case "download" -> download(client, execution, bucket, objectKey);
                default -> throw new IllegalArgumentException("action 须为 upload 或 download，实际: " + action);
            }
        }
    }

    private void upload(S3Client client, DelegateExecution execution, String bucket, String objectKey)
            throws Exception {
        String localPath = ExecutionUtils.getStringInputVariable(execution, "local_path").orElse(null);
        String text = ExecutionUtils.getStringInputVariable(execution, "content").orElse(null);

        byte[] bytes;
        if (localPath != null && !localPath.isBlank()) {
            Path path = Path.of(localPath);
            bytes = Files.readAllBytes(path);
        } else if (text != null) {
            bytes = text.getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("upload 须指定 local_path 或 content");
        }

        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(objectKey).build(),
                RequestBody.fromBytes(bytes));

        setBytesOutput(execution, bytes.length);
    }

    private void download(S3Client client, DelegateExecution execution, String bucket, String objectKey)
            throws Exception {
        byte[] bytes =
                client.getObject(
                        GetObjectRequest.builder().bucket(bucket).key(objectKey).build(),
                        ResponseTransformer.toBytes())
                        .asByteArray();

        String localPath = ExecutionUtils.getStringInputVariable(execution, "local_path").orElse(null);
        if (localPath != null && !localPath.isBlank()) {
            Path path = Path.of(localPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, bytes);
        }

        setBytesOutput(execution, bytes.length);
        String contentVar = ExecutionUtils.getOutputVariableName(execution, "content");
        if (contentVar != null && !contentVar.isBlank()) {
            execution.setVariable(contentVar, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private void setBytesOutput(DelegateExecution execution, long bytes) {
        String bytesVar = ExecutionUtils.getOutputVariableName(execution, "bytes");
        if (bytesVar != null && !bytesVar.isBlank()) {
            execution.setVariable(bytesVar, bytes);
        }
    }

    private static String require(DelegateExecution execution, String key) {
        return ExecutionUtils.getStringInputVariable(execution, key)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 " + key + " 不能为空"));
    }
}
