package com.genersoft.iot.vmp.vmanager.bean;

import com.genersoft.iot.vmp.conf.exception.ControllerException;

/**
 * 国标身份校验与运行时快照读取。
 */
public final class SipIdentityValidator {

    private SipIdentityValidator() {
    }

    public static SipIdentity validate(String id, String domain, String password) {
        String normalizedId = id == null ? "" : id.trim();
        if (!normalizedId.matches("\\d{20}")) {
            throw new ControllerException(400, "国标编号必须为20位数字");
        }
        String normalizedDomain = domain == null ? "" : domain.trim();
        if (!normalizedDomain.matches("\\d{10}")) {
            throw new ControllerException(400, "国标域必须为10位数字");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new ControllerException(400, "认证密码不能为空");
        }
        return new SipIdentity(normalizedId, normalizedDomain, password);
    }
}
