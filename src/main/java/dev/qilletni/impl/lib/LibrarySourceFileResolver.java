package dev.qilletni.impl.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class LibrarySourceFileResolver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LibrarySourceFileResolver.class);
    
    private final Map<String, Function<String, String>> libraryResolver = new HashMap<>();

    private final Map<String, Function<String, Path>> diskResolver = new HashMap<>();
    
    public void addLibraryResolver(String library, Function<String, String> sourceResolver) {
        libraryResolver.put(library, sourceResolver);
    }

    /**
     * Registers where a library's sources are on disk, for a library that isn't read from an archive.
     *
     * @param diskPathResolver Gives the file of a path under {@code qilletni-src}, or {@code null}
     */
    public void addLibraryDiskResolver(String library, Function<String, Path> diskPathResolver) {
        diskResolver.put(library, diskPathResolver);
    }

    /**
     * @return The file a library's source is read from, if the library is on disk
     */
    public Optional<Path> resolveDiskPath(String libraryName, String path) {
        var resolver = diskResolver.get(libraryName);
        return resolver == null ? Optional.empty() : Optional.ofNullable(resolver.apply(path));
    }
    
    public Optional<String> resolveSourcePath(String libraryName, String path) {
        LOGGER.debug("resolveSourcePath({}, {}) with: {}", libraryName, path, libraryResolver.keySet());
        if (!libraryResolver.containsKey(libraryName)) {
            return Optional.empty();
        }

        var apply = libraryResolver.get(libraryName).apply(path);
        // Quite verbose
//        LOGGER.debug("applies: {}", apply);
        return Optional.ofNullable(apply);
    }
    
}
