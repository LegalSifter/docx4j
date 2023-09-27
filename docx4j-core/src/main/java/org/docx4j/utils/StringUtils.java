package org.docx4j.utils;

public class StringUtils {

    /**
     * Escape filepath to avoid directory traversal attacks.
     *
     * @param filepath
     * @return
     */
    public static String escapeFilepath(String filepath) {
        return filepath.replaceAll("[^a-zA-Z0-9.-\\\/]", "_");
    }

    /**
     * Check if the given file name is valid. If there are characters that need
     * escaped, it is likely a malicious user trying to access a file outside of
     * the intended directory.
     *
     * @param fileName
     * @return
     */
    public static boolean validFilePath(String path) {
        return path.equals(escapeFilepath(path)) && !path.contains("..");
    }

}
