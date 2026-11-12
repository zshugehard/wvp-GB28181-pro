package com.genersoft.iot.vmp.gb28181.session;

import com.genersoft.iot.vmp.conf.SipConfig;
import com.genersoft.iot.vmp.vmanager.bean.SipIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SipIdentityRuntimeTest {

    @Test
    void ssrcFactories_readDomainFromCurrentIdentitySnapshot() {
        SipConfig sipConfig = new SipConfig();
        sipConfig.applyIdentity(new SipIdentity("41010500002000000001", "4101050000", "old"));
        SendSsrcFactory sendSsrcFactory = new SendSsrcFactory();
        org.springframework.test.util.ReflectionTestUtils.setField(sendSsrcFactory, "sipConfig", sipConfig);
        sendSsrcFactory.init();

        sipConfig.applyIdentity(new SipIdentity("41010500002000000002", "9999999999", "new"));

        assertTrue(sendSsrcFactory.getSendSsrc("0").startsWith("09999"),
                "send SSRC must use the current runtime domain");
    }

    @Test
    void ssrcFactory_readsDomainFromCurrentIdentitySnapshot() throws Exception {
        SipConfig sipConfig = new SipConfig();
        sipConfig.applyIdentity(new SipIdentity("41010500002000000001", "4101050000", "old"));
        SSRCFactory factory = new SSRCFactory();
        org.springframework.test.util.ReflectionTestUtils.setField(factory, "sipConfig", sipConfig);
        factory.init();
        java.lang.reflect.Field schedulerField = SSRCFactory.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        ((java.util.concurrent.ScheduledExecutorService) schedulerField.get(factory)).shutdownNow();

        sipConfig.applyIdentity(new SipIdentity("41010500002000000002", "9999999999", "new"));

        String ssrc = factory.getPlaySsrc("server");
        assertTrue(ssrc.startsWith("09999"), "play SSRC must use the current runtime domain");
    }

    @Test
    void identitySnapshot_isImmutableViewOfTheThreeFields() {
        SipConfig sipConfig = new SipConfig();
        sipConfig.applyIdentity(new SipIdentity("41010500002000000001", "4101050000", "old"));

        SipIdentity snapshot = sipConfig.identitySnapshot();

        assertEquals("41010500002000000001", snapshot.getId());
        assertEquals("4101050000", snapshot.getDomain());
        assertEquals("old", snapshot.getPassword());
    }
}
