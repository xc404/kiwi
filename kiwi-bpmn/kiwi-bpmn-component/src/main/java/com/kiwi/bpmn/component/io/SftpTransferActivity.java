package com.kiwi.bpmn.component.io;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

@ComponentDescription(
        name = "SFTP 传输",
        group = "文件",
        version = "1.0",
        description = "通过 SFTP 上传或下载文件；密码建议用项目环境变量",
        inputs = {
                @ComponentParameter(key = "host", description = "SFTP 主机", required = true),
                @ComponentParameter(
                        key = "port",
                        description = "端口",
                        schema = @Schema(defaultValue = "22")),
                @ComponentParameter(key = "username", description = "用户名", required = true),
                @ComponentParameter(key = "password", description = "密码"),
                @ComponentParameter(
                        key = "action",
                        description = "upload 或 download",
                        required = true,
                        schema = @Schema(defaultValue = "download")),
                @ComponentParameter(key = "remote_path", description = "远端路径", required = true),
                @ComponentParameter(key = "local_path", description = "本地路径", required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "bytes_transferred",
                        description = "传输字节数",
                        schema = @Schema(defaultValue = "bytes_transferred"))
        })
@Component("sftpTransfer")
public class SftpTransferActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws JSchException, SftpException, IOException {
        String host = require(execution, "host");
        int port = ExecutionUtils.getIntInputVariable(execution, "port").orElse(22);
        String username = require(execution, "username");
        String password = ExecutionUtils.getStringInputVariable(execution, "password").orElse(null);
        String action = require(execution, "action").toLowerCase(Locale.ROOT);
        String remotePath = require(execution, "remote_path");
        String localPath = require(execution, "local_path");

        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        if (password != null) {
            session.setPassword(password);
        }
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(30_000);

        long bytes;
        try {
            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(30_000);
            try {
                bytes =
                        switch (action) {
                            case "upload" -> upload(channel, localPath, remotePath);
                            case "download" -> download(channel, remotePath, localPath);
                            default ->
                                    throw new IllegalArgumentException("action 须为 upload 或 download，实际: " + action);
                        };
            } finally {
                channel.disconnect();
            }
        } finally {
            session.disconnect();
        }

        String outVar = ExecutionUtils.getOutputVariableName(execution, "bytes_transferred");
        if (outVar != null && !outVar.isBlank()) {
            execution.setVariable(outVar, bytes);
        }
    }

    private long upload(ChannelSftp channel, String localPath, String remotePath) throws SftpException, IOException {
        Path local = Path.of(localPath);
        if (!Files.isRegularFile(local)) {
            throw new IllegalArgumentException("本地文件不存在: " + localPath);
        }
        channel.put(local.toString(), remotePath);
        return Files.size(local);
    }

    private long download(ChannelSftp channel, String remotePath, String localPath) throws SftpException, IOException {
        Path local = Path.of(localPath);
        Path parent = local.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        channel.get(remotePath, local.toString());
        return Files.size(local);
    }

    private static String require(DelegateExecution execution, String key) {
        return ExecutionUtils.getStringInputVariable(execution, key)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 " + key + " 不能为空"));
    }
}
