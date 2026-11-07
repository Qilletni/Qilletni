package dev.qilletni.impl.debug;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The debugger's identity for a source file, separate from the short name used in errors and stack traces.
 *
 * @param path An absolute real path or {@code qll://<library>/<path>}, or {@code null} for sources not in a file
 * @param name The short file name
 */
public record SourceId(String path, String name) {

    public static SourceId ofFile(Path file) throws IOException {
        var realPath = file.toRealPath();
        return new SourceId(realPath.toString(), realPath.getFileName().toString());
    }

    public static SourceId ofLibrary(String library, String path) {
        var normalized = path.replace("\\", "/");
        var name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return new SourceId("qll://%s/%s".formatted(library, normalized), name);
    }

    public static SourceId ofName(String name) {
        return new SourceId(null, name);
    }
}
