package com.flowsight.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

// Single authority for the client IP used in rate limiting and audit logs.
//
// X-Forwarded-For is attacker-controlled: any client can prepend arbitrary
// entries. Only the last N entries are trustworthy, where N is the number of
// reverse proxies we actually sit behind (each trusted hop appends exactly one
// entry). With N=1 (Render/Vercel) that is the rightmost entry; behind an
// extra CDN set application.security.trusted-proxy-count=2 and the entry one
// step further left is used. Taking the first entry (the original behavior)
// let attackers rotate spoofed IPs to bypass per-IP rate limits entirely.
//
// When the app is reached directly (local dev, no proxy) the header is absent
// and we fall back to the socket address.
@Component
public class ClientIpResolver {

    private final int trustedProxyCount;

    public ClientIpResolver(
        @org.springframework.beans.factory.annotation.Value(
            "${application.security.trusted-proxy-count:1}") int trustedProxyCount
    ) {
        this.trustedProxyCount = Math.max(1, trustedProxyCount);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) return "unknown";
        try {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                // the client IP as seen by the outermost trusted proxy; clamp so a
                // short header (fewer hops than expected) degrades to the leftmost entry
                int idx = Math.max(0, parts.length - trustedProxyCount);
                String candidate = parts[idx].trim();
                if (!candidate.isEmpty()) return candidate;
            }
            String addr = request.getRemoteAddr();
            return addr != null ? addr : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
