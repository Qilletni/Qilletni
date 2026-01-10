package dev.qilletni.impl.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class LibrarySourceFileResolver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LibrarySourceFileResolver.class);
    
    private final Map<String, Function<String, String>> libraryResolver = new HashMap<>();
    
    public void addLibraryResolver(String library, Function<String, String> sourceResolver) {
        libraryResolver.put(library, sourceResolver);
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
