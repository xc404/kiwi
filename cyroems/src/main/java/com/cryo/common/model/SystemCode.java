package com.cryo.common.model;

import net.dreamlu.mica.core.result.IResultCode;

public enum SystemCode implements IResultCode {

    SystemError(500, "系统错误"),
    TokenError(401, "token错误"),
    NotPermission(403, "没有权限");
    private final int code;
    private final String msg;

    public int getCode() {
        return this.code;
    }

    public String getMsg() {
        return this.msg;
    }

    private SystemCode(final int code, final String msg) {
        this.code = code;
        this.msg = msg;
    }
}
