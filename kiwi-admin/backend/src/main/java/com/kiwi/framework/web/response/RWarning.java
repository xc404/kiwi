package com.kiwi.framework.web.response;

import net.dreamlu.mica.core.result.R;

/**
 * {@link R} 工厂：成功载荷 + 业务 warning 码。
 */
public final class RWarning {

    private RWarning() {
    }

    public static <T> R<T> of(T data, String msg) {
        R<T> response = R.success(data);
        response.setCode(AppResultCode.Warning.getCode());
        response.setMsg(msg);
        return response;
    }
}
