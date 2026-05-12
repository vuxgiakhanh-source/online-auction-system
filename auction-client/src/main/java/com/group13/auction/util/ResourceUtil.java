package com.group13.auction.util;

import java.net.URL;

/**
 * Utility methods for loading classpath resources.
 */
public final class ResourceUtil {

    private ResourceUtil() {
        // Utility class.
    }

    /**
     * Kiểm tra resource có tồn tại trên classpath hay không.
     *
     * @param path classpath resource path
     * @return true nếu resource tồn tại
     */
    public static boolean exists(String path) {
        return ResourceUtil.class.getResource(path) != null;
    }

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

    /**
     * Chuyển resource path thành external form để JavaFX stylesheet/image dùng được.
     *
     * @param path classpath resource path
     * @return external form của resource
     */
    public static String toExternalForm(String path) {
        return requireResource(path).toExternalForm();
    }
}