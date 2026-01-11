package dev.qilletni.impl.lib;

import dev.qilletni.api.lib.NativeFunctionBindingFactory;
import dev.qilletni.api.lib.qll.QllInfo;
import dev.qilletni.impl.lang.exceptions.lib.LibraryNotFoundException;
import dev.qilletni.impl.lang.internal.NativeFunctionHandler;
import dev.qilletni.impl.lib.persistence.PackageConfigImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LibraryRegistrar {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryRegistrar.class);
    
    private final NativeFunctionHandler nativeFunctionHandler;
    private final LibrarySourceFileResolver librarySourceFileResolver; 
    private final Map<String, QllInfo> libraries = new HashMap<>();
    private final List<AutoImportFile> autoImportFiles = new ArrayList<>();

    public LibraryRegistrar(NativeFunctionHandler nativeFunctionHandler, LibrarySourceFileResolver librarySourceFileResolver) {
        this.nativeFunctionHandler = nativeFunctionHandler;
        this.librarySourceFileResolver = librarySourceFileResolver;
    }

    public void registerLibraries(List<QllInfo> loadedQllInfos) {
        var orderedQllInfos = PackageSorter.getOrderedPackageList(loadedQllInfos);
        
        for (var qllInfo : orderedQllInfos) {
            try {
                LOGGER.debug("Loading library {} v{}", qllInfo.name(), qllInfo.version().getVersionString());

                if (libraries.containsKey(qllInfo.name())) {
                    LOGGER.error("Attempted to import library of duplicate name: {}", qllInfo.name());
                } else {
                    libraries.put(qllInfo.name(), qllInfo);
                    var classes = loadNativeClasses(qllInfo.nativeClasses());
                    nativeFunctionHandler.registerClasses(classes);

                    var packageConfig = PackageConfigImpl.createPackageConfig(qllInfo.name());
                    nativeFunctionHandler.addScopedInjectableInstance(packageConfig, List.of(classes));

                    instantiateNativeFunctionBindingFactory(qllInfo.nativeBindFactoryClass())
                            .ifPresent(factory -> factory.applyNativeFunctionBindings(nativeFunctionHandler));

                    autoImportFiles.addAll(qllInfo.autoImportFiles()
                            .stream()
                            .map(file -> new AutoImportFile(file, qllInfo.name())).toList());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load library {} v{}", qllInfo.name(), qllInfo.version().getVersionString(), e);
            }
        }
    }
    
    private Class<?>[] loadNativeClasses(List<String> nativeClasses) {
        return nativeClasses.stream().map(className -> {
            try {
                return Thread.currentThread().getContextClassLoader().loadClass(className);
            } catch (ClassNotFoundException e) {
                LOGGER.error("Failed to load native class: {}", className, e);
                return (Class<?>) null;
            }
        }).filter(Objects::nonNull).toArray(Class<?>[]::new);
    }
    
    private Optional<NativeFunctionBindingFactory> instantiateNativeFunctionBindingFactory(String className) {
        if (className == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of((NativeFunctionBindingFactory) Thread.currentThread().getContextClassLoader().loadClass(className).getConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to instantiate NativeFunctionBindingFactory: {}", className, e);
            return Optional.empty();
        }
    }

    /**
     * Reads a .ql file from a library, returning its contents.
     * 
     * @param libraryName The name of the library
     * @param path The path of the file
     * @return The contents of the file, if present
     */
    public Optional<String> findLibraryByPath(String libraryName, Path path) throws IOException {
        if (libraryName == null) {
            try (var resourceStream = getClass().getClassLoader().getResourceAsStream(path.toString())) {
                if (resourceStream != null) {
                    return Optional.of(new String(resourceStream.readAllBytes()));
                }
                
                return Optional.empty();
            }
        }

        if (!libraries.containsKey(libraryName)) {
            throw new LibraryNotFoundException("Library '%s' not found".formatted(libraryName));
        }
        
        var library = libraries.get(libraryName);
        
        return librarySourceFileResolver.resolveSourcePath(libraryName, path.toString().replace("\\", "/"));
//                .or(() -> library.readPath(path.toString().replace("\\", "/")).map(stream -> {
//                    try {
//                        return new String(stream.readAllBytes());
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                }));
    }

    public LibrarySourceFileResolver getLibrarySourceFileResolver() {
        return librarySourceFileResolver;
    }

    public List<AutoImportFile> getAutoImportFiles() {
        return autoImportFiles;
    }

    public record AutoImportFile(String fileName, String libName) {}
}
