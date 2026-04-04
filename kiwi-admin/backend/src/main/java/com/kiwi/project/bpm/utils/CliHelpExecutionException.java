package com.kiwi.project.bpm.utils;

/**
 * 执行用于获取 help 文本的外部命令失败或超时。
 */
public class CliHelpExecutionException extends RuntimeException {

    public CliHelpExecutionException(String message) {
        super(message);
    }

    public CliHelpExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
