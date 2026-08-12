/*
 * Copyright (c) 2026. Benik Arakelyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jsunsoft.http;

import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the address classification behind
 * {@link ClientBuilder#disallowPrivateAndLoopbackHosts()}. All inputs are IP literals —
 * {@link InetAddress#getByName(String)} parses them without touching DNS, so the tests are
 * deterministic and network-free.
 */
class SsrfAddressPolicyTest {

    private static boolean nonGlobal(String ipLiteral) throws UnknownHostException {
        return SsrfAddressPolicy.isNonGlobal(InetAddress.getByName(ipLiteral));
    }

    private static void assertBlocked(String ipLiteral, String why) throws UnknownHostException {
        assertTrue(nonGlobal(ipLiteral), ipLiteral + " must be classified non-global: " + why);
    }

    private static void assertAllowed(String ipLiteral, String why) throws UnknownHostException {
        assertFalse(nonGlobal(ipLiteral), ipLiteral + " must stay allowed: " + why);
    }

    @Test
    void originalRanges_areBlocked() throws UnknownHostException {
        // Pins the pre-existing coverage so the range extension can't regress it.
        assertBlocked("127.0.0.1", "loopback");
        assertBlocked("0.0.0.0", "unspecified");
        assertBlocked("10.0.0.1", "RFC 1918");
        assertBlocked("172.16.0.1", "RFC 1918");
        assertBlocked("192.168.1.1", "RFC 1918");
        assertBlocked("169.254.169.254", "link-local / cloud metadata");
        assertBlocked("::1", "IPv6 loopback");
        assertBlocked("::", "IPv6 unspecified");
        assertBlocked("fe80::1", "IPv6 link-local");
        assertBlocked("fc00::1", "IPv6 unique-local fc00::/7");
        assertBlocked("fd12:3456::1", "IPv6 unique-local fc00::/7");
    }

    @Test
    void reservedAndSpecialPurposeIpv4Ranges_areBlocked() throws UnknownHostException {
        assertBlocked("0.1.2.3", "0.0.0.0/8 'this network' (RFC 791)");
        assertBlocked("100.64.0.1", "100.64.0.0/10 CGNAT (RFC 6598)");
        assertBlocked("100.127.255.254", "100.64.0.0/10 CGNAT upper bound");
        assertBlocked("192.0.0.170", "192.0.0.0/24 IETF protocol assignments (RFC 6890)");
        assertBlocked("192.0.2.1", "192.0.2.0/24 TEST-NET-1 (RFC 5737)");
        assertBlocked("198.18.0.1", "198.18.0.0/15 benchmarking (RFC 2544)");
        assertBlocked("198.19.255.255", "198.18.0.0/15 benchmarking upper bound");
        assertBlocked("198.51.100.7", "198.51.100.0/24 TEST-NET-2 (RFC 5737)");
        assertBlocked("203.0.113.9", "203.0.113.0/24 TEST-NET-3 (RFC 5737)");
        assertBlocked("240.0.0.1", "240.0.0.0/4 reserved (RFC 1112)");
        assertBlocked("255.255.255.255", "limited broadcast");
    }

    @Test
    void multicast_isBlocked() throws UnknownHostException {
        assertBlocked("224.0.0.1", "IPv4 multicast lower bound");
        assertBlocked("239.255.255.255", "IPv4 multicast upper bound");
        assertBlocked("ff02::1", "IPv6 multicast");
    }

    @Test
    void ipv6FormsEmbeddingAnInternalIpv4_areBlocked() throws UnknownHostException {
        // NAT64 well-known prefix (RFC 6052): a NAT64 gateway translates these to the embedded
        // IPv4 — 64:ff9b::<internal v4> is a route to the internal network.
        assertBlocked("64:ff9b::7f00:1", "NAT64 well-known prefix embedding 127.0.0.1");
        assertBlocked("64:ff9b::a00:1", "NAT64 well-known prefix embedding 10.0.0.1");
        // NAT64 local-use prefix (RFC 8215) is only meaningful inside an operator's network.
        assertBlocked("64:ff9b:1::1", "NAT64 local-use prefix 64:ff9b:1::/48");
        // IPv4-compatible form (deprecated, RFC 4291) still parses as a raw Inet6Address.
        assertBlocked("::10.0.0.1", "IPv4-compatible form embedding 10.0.0.1");
    }

    @Test
    void rawIpv4MappedInet6Address_isBlockedWhenEmbeddedIpv4IsInternal() throws UnknownHostException {
        // InetAddress.getByName normalizes ::ffff:a.b.c.d to Inet4Address, but a resolver can
        // hand back the raw 16-byte mapped form — the policy must not be fooled by the wrapper.
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        mapped[12] = 10;
        mapped[13] = 0;
        mapped[14] = 0;
        mapped[15] = 1;
        InetAddress rawMapped = Inet6Address.getByAddress("raw-mapped", mapped, -1);
        assertTrue(SsrfAddressPolicy.isNonGlobal(rawMapped),
                "raw IPv4-mapped ::ffff:10.0.0.1 must be classified non-global");
    }

    @Test
    void publicAddresses_stayAllowed() throws UnknownHostException {
        // Over-blocking guards: neighbours of every newly blocked range must remain global.
        assertAllowed("8.8.8.8", "public DNS");
        assertAllowed("1.1.1.1", "public DNS");
        assertAllowed("9.255.255.255", "just below 10.0.0.0/8");
        assertAllowed("11.0.0.1", "just above 10.0.0.0/8");
        assertAllowed("100.63.255.255", "just below the CGNAT block");
        assertAllowed("100.128.0.1", "just above the CGNAT block");
        assertAllowed("192.0.1.1", "between 192.0.0.0/24 and TEST-NET-1");
        assertAllowed("192.0.3.1", "just above TEST-NET-1");
        assertAllowed("198.17.255.255", "just below the benchmarking block");
        assertAllowed("198.20.0.1", "just above the benchmarking block");
        assertAllowed("198.51.101.1", "just above TEST-NET-2");
        assertAllowed("203.0.114.1", "just above TEST-NET-3");
        assertAllowed("223.255.255.255", "last unicast address before multicast");
        assertAllowed("2001:4860:4860::8888", "public IPv6 DNS");
        assertAllowed("2606:4700:4700::1111", "public IPv6 DNS");
        assertAllowed("64:ff9b::808:808", "NAT64 well-known prefix embedding public 8.8.8.8");
    }
}
