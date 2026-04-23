package com.cryo.service.cmd;

public class CmdException extends RuntimeException{
    public CmdException() {
    }

    public CmdException(String message) {
        super(message);
    }

    public CmdException(String message, Throwable cause) {
        super(message, cause);
    }

    public CmdException(Throwable cause) {
        super(cause);
    }

    public CmdException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
