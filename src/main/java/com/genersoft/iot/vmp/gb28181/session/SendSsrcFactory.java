package com.genersoft.iot.vmp.gb28181.session;

import com.genersoft.iot.vmp.conf.SipConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class SendSsrcFactory {

    @Autowired
    private SipConfig sipConfig;

    @PostConstruct
    public void init() {
        // 身份字段可在运行中热更新, domain 必须在每次生成 SSRC 时读取当前快照
    }

    public String getSendSsrc(String prefix) {
        return prefix + currentDomainPart() + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String currentDomainPart() {
        String sipDomain = sipConfig.identitySnapshot().getDomain();
        return sipDomain.length() >= 8 ? sipDomain.substring(3, 8) : sipDomain;
    }
}
