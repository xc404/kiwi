package com.kiwi.project.tools.jdbc.connection.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class JdbcConnectionErrorUtils {

    public String toUserMessage(Throwable throwable) {
        String message = rootMessage(throwable);
        if (StringUtils.isBlank(message)) {
            return "JDBC 连接失败，请检查连接地址、用户名、密码及数据库服务状态。";
        }
        if (message.contains("Access denied")) {
            if (message.contains("172.17.") || message.contains("using password: YES")) {
                String host = extractMysqlClientHost(message);
                if (host != null) {
                    return "MySQL 拒绝了用户从主机「" + host + "」的连接（Access denied）。"
                            + " 常见于后端经 Docker 访问本机/容器内 MySQL：请在 MySQL 执行授权，例如 "
                            + "CREATE USER '你的用户'@'" + host + "' IDENTIFIED BY '密码'; "
                            + "GRANT ALL ON 数据库名.* TO '你的用户'@'" + host + "'; FLUSH PRIVILEGES;"
                            + " 或使用 '你的用户'@'%' 允许任意主机。";
                }
            }
            return "MySQL 认证失败（Access denied），请确认用户名、密码正确，且 MySQL 已授权该用户从当前客户端主机连接。"
                    + " 原始信息：" + message;
        }
        return "JDBC 连接失败：" + message;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String last = null;
        while (current != null) {
            if (StringUtils.isNotBlank(current.getMessage())) {
                last = current.getMessage();
            }
            current = current.getCause();
        }
        return last;
    }

    private String extractMysqlClientHost(String message) {
        int at = message.indexOf('@');
        if (at < 0) {
            return null;
        }
        int start = message.indexOf('\'', at);
        if (start < 0) {
            return null;
        }
        int end = message.indexOf('\'', start + 1);
        if (end <= start) {
            return null;
        }
        return message.substring(start + 1, end);
    }
}
