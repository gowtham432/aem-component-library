package com.mycompany.core.pi.util;

public final class PathUtils {

    private PathUtils() {
    }

    /**
     * True if {@code ancestor} is {@code path} itself, or a JCR path
     * ancestor of it (segment-aware: "/content" is not considered an
     * ancestor of "/contentx").
     */
    public static boolean isAncestorOrSelf(String ancestor, String path) {
        return path.equals(ancestor) || path.startsWith("/".equals(ancestor) ? "/" : ancestor + "/");
    }
}
