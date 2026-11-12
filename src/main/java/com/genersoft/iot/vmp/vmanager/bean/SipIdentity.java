package com.genersoft.iot.vmp.vmanager.bean;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 国标身份三元组；id/domain/password 必须作为整体替换。
 */
@Schema(description = "国标身份")
public final class SipIdentity {

    private final String id;
    private final String domain;
    private final String password;

    public SipIdentity(String id, String domain, String password) {
        this.id = id;
        this.domain = domain;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getDomain() {
        return domain;
    }

    public String getPassword() {
        return password;
    }
}
