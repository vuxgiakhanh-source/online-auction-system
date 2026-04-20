package com.group13.auction.util;

import java.net.URL;

/**
 * Utility methods for loading classpath resources.
 */
public final class ResourceUtil {

    private ResourceUtil() {}

    /**
     * Returns a required resource URL.
     *
     * @param path classpath resource path
     * @return resolved URL
     */
    public static URL requireResource(String path) {
        URL resource = ResourceUtil.class.getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found: " + path);
        }
        return resource;
    }
}