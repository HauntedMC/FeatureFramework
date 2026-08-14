package nl.hauntedmc.featureframework.toolkit.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IpAddressUtilTest {

    @Test
    void acceptsAndNormalizesIpLiteralsWithoutResolvingHostnames() {
        assertEquals("192.168.1.5", IpAddressUtil.normalizeLiteral(" 192.168.1.5 "));
        assertEquals("0:0:0:0:0:0:0:1", IpAddressUtil.normalizeLiteral("[::1]"));
        assertNull(IpAddressUtil.parseLiteral("localhost"));
    }

    @Test
    void rejectsMalformedIpv4Literals() {
        assertNull(IpAddressUtil.parseLiteral("192.168.1"));
        assertNull(IpAddressUtil.parseLiteral("192.168.1.256"));
        assertNull(IpAddressUtil.parseLiteral("192.168.one.1"));
    }
}
