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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Address classification behind {@link ClientBuilder#disallowPrivateAndLoopbackHosts()}: decides
 * whether a resolved {@link InetAddress} is non-global — i.e. one an SSRF guard should refuse to
 * connect to because it addresses the local machine or an internal network rather than the
 * public internet.
 * <p>
 * This is a best-effort deny-list of well-known non-global ranges, not a capability boundary —
 * exotic IPv4-embedding schemes (6to4 {@code 2002::/16}, Teredo {@code 2001::/32}) are not
 * decoded. Combine with network-layer egress filtering for defence-in-depth.
 */
final class SsrfAddressPolicy {

    private SsrfAddressPolicy() {
    }

    /**
     * @return {@code true} when connecting to {@code addr} should be refused by the SSRF guard
     */
    static boolean isNonGlobal(InetAddress addr) {
        if (addr.isLoopbackAddress()        // 127.0.0.0/8, ::1
                || addr.isAnyLocalAddress()     // 0.0.0.0, ::
                || addr.isLinkLocalAddress()    // 169.254.0.0/16, fe80::/10
                || addr.isSiteLocalAddress()    // 10/8, 172.16/12, 192.168/16, fec0::/10
                || addr.isMulticastAddress()) { // 224.0.0.0/4, ff00::/8
            return true;
        }
        if (addr instanceof Inet4Address) {
            return isReservedIpv4(addr.getAddress());
        }
        if (addr instanceof Inet6Address) {
            return isNonGlobalIpv6(addr.getAddress());
        }
        return false;
    }

    /**
     * IANA special-purpose IPv4 ranges not covered by the {@link InetAddress} convenience checks.
     */
    private static boolean isReservedIpv4(byte[] b) {
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        int b2 = b[2] & 0xff;
        if (b0 == 0) return true;                            // 0.0.0.0/8 "this network" (RFC 791)
        if (b0 == 100 && (b1 & 0xc0) == 0x40) return true;   // 100.64.0.0/10 carrier-grade NAT (RFC 6598)
        if (b0 == 192 && b1 == 0 && b2 == 0) return true;    // 192.0.0.0/24 IETF protocol assignments (RFC 6890)
        if (b0 == 192 && b1 == 0 && b2 == 2) return true;    // 192.0.2.0/24 TEST-NET-1 (RFC 5737)
        if (b0 == 198 && (b1 & 0xfe) == 18) return true;     // 198.18.0.0/15 benchmarking (RFC 2544)
        if (b0 == 198 && b1 == 51 && b2 == 100) return true; // 198.51.100.0/24 TEST-NET-2 (RFC 5737)
        if (b0 == 203 && b1 == 0 && b2 == 113) return true;  // 203.0.113.0/24 TEST-NET-3 (RFC 5737)
        return b0 >= 240;                                    // 240.0.0.0/4 reserved (RFC 1112), incl. 255.255.255.255
    }

    private static boolean isNonGlobalIpv6(byte[] b) {
        if ((b[0] & 0xfe) == 0xfc) {
            return true;                                     // fc00::/7 unique-local (RFC 4193)
        }
        if (isNat64LocalUsePrefix(b)) {
            return true;                                     // 64:ff9b:1::/48 (RFC 8215) — operator-internal by definition
        }
        // Forms embedding an IPv4 address in the low 32 bits are as reachable as that address
        // (via a NAT64 gateway or the dual-stack socket layer) — apply the full policy to it.
        if (isNat64WellKnownPrefix(b) || isIpv4Mapped(b) || isIpv4Compatible(b)) {
            return isNonGlobal(embeddedIpv4(b));
        }
        return false;
    }

    /**
     * NAT64 / DNS64 well-known prefix {@code 64:ff9b::/96} (RFC 6052).
     */
    private static boolean isNat64WellKnownPrefix(byte[] b) {
        if (b[0] != 0x00 || (b[1] & 0xff) != 0x64 || (b[2] & 0xff) != 0xff || (b[3] & 0xff) != 0x9b) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (b[i] != 0) return false;
        }
        return true;
    }

    /**
     * NAT64 local-use prefix {@code 64:ff9b:1::/48} (RFC 8215).
     */
    private static boolean isNat64LocalUsePrefix(byte[] b) {
        return b[0] == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b
                && b[4] == 0x00 && b[5] == 0x01;
    }

    /**
     * IPv4-mapped {@code ::ffff:0:0/96} (RFC 4291 §2.5.5.2). The JDK normalizes these to
     * {@link Inet4Address} when parsing, but a resolver can hand back the raw 16-byte form.
     */
    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    }

    /**
     * IPv4-compatible {@code ::/96} (deprecated, RFC 4291 §2.5.5.1). {@code ::} and {@code ::1}
     * never reach this check — they are caught by the unspecified/loopback checks first.
     */
    private static boolean isIpv4Compatible(byte[] b) {
        for (int i = 0; i < 12; i++) {
            if (b[i] != 0) return false;
        }
        return true;
    }

    private static InetAddress embeddedIpv4(byte[] b) {
        byte[] v4 = {b[12], b[13], b[14], b[15]};
        try {
            return InetAddress.getByAddress(v4);
        } catch (UnknownHostException e) {
            // Unreachable: getByAddress only rejects arrays that are not 4/16 bytes long.
            throw new IllegalStateException(e);
        }
    }
}
