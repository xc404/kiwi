package com.kiwi.framework.web.response;

import net.dreamlu.mica.core.result.IResultCode;

/**
 * 统一响应业务码：与前端 {@code successCode} / {@code warningCode} 对齐。
 * <ul>
 *   <li>{@link #Success}（1）：常规成功，无额外提示</li>
 *   <li>{@link #Warning}（2）：操作成功但需向用户展示 {@code R.msg}（如自动修正类提示）</li>
 * </ul>
 */
public enum AppResultCode implements IResultCode
{
    Success(1, "操作成功"),
    Warning(2, "");

    private final int code;
    private final String msg;

    AppResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
