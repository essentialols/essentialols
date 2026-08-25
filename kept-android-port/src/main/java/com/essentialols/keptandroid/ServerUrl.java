package com.essentialols.keptandroid;

import android.net.Uri;

final class ServerUrl {
    private ServerUrl() {}
    static String normalize(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;
        if (!raw.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) raw = "https://" + raw;
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || empty(uri.getHost())) return null;
        String normalized = uri.toString();
        while (normalized.endsWith("/") && normalized.length() > scheme.length() + 3) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
    private static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
}
