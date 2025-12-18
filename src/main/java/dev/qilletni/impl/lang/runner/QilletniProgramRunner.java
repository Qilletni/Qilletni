package dev.qilletni.impl.lang.runner;

import dev.qilletni.impl.antlr.QilletniLexer;
import dev.qilletni.impl.antlr.QilletniParser;
import dev.qilletni.api.lang.stack.QilletniStackTrace;
import dev.qilletni.api.lang.table.Scope;
import dev.qilletni.api.lang.table.SymbolTable;
import dev.qilletni.api.lang.types.BooleanType;
import dev.qilletni.api.lang.types.DoubleType;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.ImportAliasType;
import dev.qilletni.api.lang.types.IntType;
import dev.qilletni.api.lang.types.JavaType;
import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.StringType;
import dev.qilletni.api.lang.types.conversion.TypeConverter;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.entity.EntityInitializer;
import dev.qilletni.api.lang.types.list.ListInitializer;
import dev.qilletni.api.lib.persistence.PackageConfig;
import dev.qilletni.api.lib.qll.QllInfo;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lang.QilletniVisitor;
import dev.qilletni.impl.lang.docs.exceptions.DocErrorListener;
import dev.qilletni.impl.lang.exceptions.QilletniNativeInvocationException;
import dev.qilletni.impl.lang.exceptions.TypeMismatchException;
import dev.qilletni.impl.lang.internal.BackgroundTaskExecutorImpl;
import dev.qilletni.impl.lang.internal.NativeFunctionHandler;
import dev.qilletni.impl.lang.internal.UnimplementedFunctionInvoker;
import dev.qilletni.impl.lang.internal.adapter.TypeAdapterInvoker;
import dev.qilletni.impl.lang.internal.adapter.TypeAdapterRegistrar;
import dev.qilletni.impl.lang.internal.debug.DebugSupportImpl;
import dev.qilletni.impl.lang.stack.QilletniStackTraceImpl;
import dev.qilletni.impl.lang.table.ScopeImpl;
import dev.qilletni.impl.lang.table.SymbolTableImpl;
import dev.qilletni.impl.lang.types.BooleanTypeImpl;
import dev.qilletni.impl.lang.types.DoubleTypeImpl;
import dev.qilletni.impl.lang.types.EntityTypeImpl;
import dev.qilletni.impl.lang.types.ImportAliasTypeImpl;
import dev.qilletni.impl.lang.types.IntTypeImpl;
import dev.qilletni.impl.lang.types.JavaTypeImpl;
import dev.qilletni.impl.lang.types.ListTypeImpl;
import dev.qilletni.impl.lang.types.StringTypeImpl;
import dev.qilletni.impl.lang.types.conversion.BulkTypeConversion;
import dev.qilletni.impl.lang.types.conversion.TypeConverterImpl;
import dev.qilletni.impl.lang.types.entity.EntityDefinitionManagerImpl;
import dev.qilletni.impl.lang.types.entity.EntityInitializerImpl;
import dev.qilletni.impl.lang.types.list.ListInitializerImpl;
import dev.qilletni.impl.lang.types.list.ListTypeTransformer;
import dev.qilletni.impl.lang.types.list.ListTypeTransformerFactory;
import dev.qilletni.impl.lib.LibraryRegistrar;
import dev.qilletni.impl.lib.LibrarySourceFileResolver;
import dev.qilletni.impl.lib.persistence.PackageConfigImpl;
import dev.qilletni.impl.music.MusicPopulatorImpl;
import dev.qilletni.impl.music.factories.AlbumTypeFactoryImpl;
import dev.qilletni.impl.music.factories.CollectionStateFactoryImpl;
import dev.qilletni.impl.music.factories.CollectionTypeFactoryImpl;
import dev.qilletni.impl.music.factories.SongTypeFactoryImpl;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class QilletniProgramRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(QilletniProgramRunner.class);

    private final Map<SymbolTable, QilletniVisitor> symbolTables;
    private final ScopeImpl globalScope;
    private final PackageConfig internalPackageConfig = PackageConfigImpl.createInternalConfig();
    private final EntityDefinitionManager entityDefinitionManager;
    private final TypeAdapterRegistrar typeAdapterRegistrar;
    private final DebugSupportImpl debugSupport;
    private final NativeFunctionHandler nativeFunctionHandler;
    private final EntityInitializer entityInitializer;
    private final DynamicProvider dynamicProvider;
    private final MusicPopulator musicPopulator;
    private final ListTypeTransformer listTypeTransformer;
    private final ListInitializer listInitializer;
    private final LibraryRegistrar libraryRegistrar;
    private final BackgroundTaskExecutorImpl backgroundTaskExecutor;
    private final QilletniStackTrace qilletniStackTrace;

    // Import cache to avoid importing the same file multiple times
    private final Set<ImportPathState> importCache = new HashSet<>();

    public QilletniProgramRunner(DynamicProvider dynamicProvider, LibrarySourceFileResolver librarySourceFileResolver, List<QllInfo> loadedQllInfos) {
        internalPackageConfig.loadConfig();
        
        dynamicProvider.initializeInitialProvider(internalPackageConfig);
        
        
        this.dynamicProvider = dynamicProvider;
        this.symbolTables = new HashMap<>();
        this.globalScope = new ScopeImpl("global");
        this.entityDefinitionManager = new EntityDefinitionManagerImpl();
        this.typeAdapterRegistrar = new TypeAdapterRegistrar();
        this.debugSupport = new DebugSupportImpl("true".equals(internalPackageConfig.get("debug").orElse("")));
        this.nativeFunctionHandler = new NativeFunctionHandler(new TypeAdapterInvoker(typeAdapterRegistrar), symbolTables, debugSupport);
        
        var bulkTypeConversion = new BulkTypeConversion(typeAdapterRegistrar);
        this.entityInitializer = new EntityInitializerImpl(entityDefinitionManager, bulkTypeConversion);
        this.musicPopulator = new MusicPopulatorImpl(dynamicProvider, internalPackageConfig);
        this.qilletniStackTrace = new QilletniStackTraceImpl();
        this.backgroundTaskExecutor = new BackgroundTaskExecutorImpl(qilletniStackTrace);

        var typeConverter = new TypeConverterImpl(typeAdapterRegistrar, entityInitializer, bulkTypeConversion);

        var songTypeFactory = new SongTypeFactoryImpl(dynamicProvider);
        var collectionTypeFactory = new CollectionTypeFactoryImpl(dynamicProvider);
        var albumTypeFactory = new AlbumTypeFactoryImpl(dynamicProvider);

        dynamicProvider.initFactories(songTypeFactory, collectionTypeFactory, albumTypeFactory);

        var listGeneratorFactory = new ListTypeTransformerFactory(dynamicProvider, musicPopulator);
        this.listTypeTransformer = listGeneratorFactory.createListGenerator();
        this.listInitializer = new ListInitializerImpl(listTypeTransformer, typeConverter);
        this.libraryRegistrar = new LibraryRegistrar(nativeFunctionHandler, librarySourceFileResolver);

        initializeTypeAdapterRegistrar(typeAdapterRegistrar, entityInitializer, typeConverter, listInitializer);

        libraryRegistrar.registerLibraries(loadedQllInfos);

        nativeFunctionHandler.addInjectableInstance(musicPopulator);
        nativeFunctionHandler.addInjectableInstance(entityDefinitionManager);
        nativeFunctionHandler.addInjectableInstance(entityInitializer);
        nativeFunctionHandler.addInjectableInstance(listInitializer);
        nativeFunctionHandler.addInjectableInstance(songTypeFactory);
        nativeFunctionHandler.addInjectableInstance(collectionTypeFactory);
        nativeFunctionHandler.addInjectableInstance(albumTypeFactory);
        nativeFunctionHandler.addInjectableInstance(new UnimplementedFunctionInvoker()); // This is used as a placeholder, and its type is manipulated during injection
        nativeFunctionHandler.addInjectableInstance(typeConverter);
        nativeFunctionHandler.addInjectableInstance(dynamicProvider);
        nativeFunctionHandler.addInjectableInstance(backgroundTaskExecutor);
        nativeFunctionHandler.addInjectableInstance(new CollectionStateFactoryImpl(dynamicProvider));
        
        if (debugSupport.isDebugEnabled()) {
            LOGGER.debug("Debugging enabled");
            nativeFunctionHandler.addScopedInjectableInstanceByNames(debugSupport, List.of("dev.qilletni.lib.core.BreakpointFunctions", "dev.qilletni.lib.core.DebugFunctions"));
        } else {
            LOGGER.debug("Debugging disabled");
        }
    }

    private static TypeAdapterRegistrar initializeTypeAdapterRegistrar(TypeAdapterRegistrar typeAdapterRegistrar, EntityInitializer entityInitializer, TypeConverter typeConverter, ListInitializer listInitializer) {
        typeAdapterRegistrar.registerExactTypeAdapter(BooleanTypeImpl.class, Boolean.class, BooleanTypeImpl::new);
        typeAdapterRegistrar.registerExactTypeAdapter(boolean.class, BooleanTypeImpl.class, BooleanType::getValue);

        typeAdapterRegistrar.registerExactTypeAdapter(IntTypeImpl.class, Long.class, IntTypeImpl::new);
        typeAdapterRegistrar.registerExactTypeAdapter(long.class, IntTypeImpl.class, IntType::getValue);

        typeAdapterRegistrar.registerExactTypeAdapter(IntTypeImpl.class, Integer.class, i -> new IntTypeImpl((long) i));
        typeAdapterRegistrar.registerExactTypeAdapter(int.class, IntTypeImpl.class, intType -> (int) intType.getValue());

        typeAdapterRegistrar.registerExactTypeAdapter(DoubleTypeImpl.class, Double.class, DoubleTypeImpl::new);
        typeAdapterRegistrar.registerExactTypeAdapter(double.class, DoubleTypeImpl.class, DoubleType::getValue);

        typeAdapterRegistrar.registerExactTypeAdapter(StringTypeImpl.class, String.class, StringTypeImpl::new);
        typeAdapterRegistrar.registerExactTypeAdapter(String.class, StringTypeImpl.class, StringType::getValue);

        typeAdapterRegistrar.registerTypeAdapter(ListType.class, List.class, (List list) -> {
            if (list.isEmpty() || list.getFirst() instanceof QilletniType) {
                var qilletniList = (List<QilletniType>) list;

                var typeList = qilletniList.stream().map(QilletniType::getTypeClass).distinct().toList();
                if (typeList.size() > 1) {
                    throw new TypeMismatchException("Multiple types found in list");
                }

                return new ListTypeImpl(qilletniList.getFirst().getTypeClass(), qilletniList);
            } else {
                return listInitializer.createListFromJava(list);
            }
        });
        
        // List, ListType  -- Qilletni passes in ListItem, and Java gets items
        // ListType, List  -- Java returns List, Qilletni gets ListType

        // TODO: Make this transform list items to Java?
        typeAdapterRegistrar.registerTypeAdapter(List.class, ListTypeImpl.class, ListTypeImpl::getItems);
        
        typeAdapterRegistrar.registerExactTypeAdapter(EntityType.class, HashMap.class, map -> {
            var mapEntity = entityInitializer.initializeEntity("Map");
            
            // Convert the map to Qilletni types
            var qilletniTypeMap = new HashMap<QilletniType, QilletniType>();
            map.forEach((key, value) -> qilletniTypeMap.put(typeConverter.convertToQilletniType(key), typeConverter.convertToQilletniType(value)));
            
            mapEntity.getEntityScope().<JavaType>lookup("_map").getValue().setReference(qilletniTypeMap);
            return mapEntity;
        });
        
        typeAdapterRegistrar.registerExactTypeAdapter(HashMap.class, EntityTypeImpl.class, mapEntity ->
                mapEntity.getEntityScope().<JavaType>lookup("_map").getValue().getReference(HashMap.class));

        typeAdapterRegistrar.registerTypeAdapter(JavaType.class, Object.class, JavaTypeImpl::new);

        return typeAdapterRegistrar;
    }

    public void importInitialFiles() {
        LOGGER.debug("Importing {} initial files!", libraryRegistrar.getAutoImportFiles().size());

        libraryRegistrar.getAutoImportFiles().forEach(autoImportFile ->
                importFileFromStream(new ImportPathState(Paths.get(autoImportFile.fileName()), autoImportFile.libName())));
    }

    public SymbolTable runProgram(Path file) throws IOException {
        return runProgram(file, null);
    }

    public SymbolTable runProgram(Path file, Scope global) throws IOException {
        var parent = file.getParent();
        if (file.getParent() == null) {
            LOGGER.debug("File '{}' has no parent", file);
            parent = Paths.get("");
        }
        
        return runProgram(file, new ImportPathState(parent), global);
    }

    private SymbolTable runProgram(Path file, ImportPathState pathState, Scope global) throws IOException {
        return runProgram(CharStreams.fromString(Files.readString(file), file.getFileName().toString()), pathState, global);
    }

    private SymbolTable runProgram(InputStream stream, ImportPathState pathState, Scope globalScope) throws IOException {
        var data = new String(new BufferedInputStream(stream).readAllBytes());
        return runProgram(CharStreams.fromString(data, pathState.path().getFileName().toString()), pathState, globalScope);
    }

    private SymbolTable runProgram(String data, ImportPathState pathState, Scope globalScope) throws IOException {
//        var data = new String(new BufferedInputStream(stream).readAllBytes());
        return runProgram(CharStreams.fromString(data, pathState.path().getFileName().toString()), pathState, globalScope);
    }

    public SymbolTable runProgram(CharStream charStream, ImportPathState pathState) {
        return runProgram(charStream, pathState, null);
    }

    public SymbolTable runProgram(CharStream charStream, ImportPathState pathState, Scope globalScope) {
        var lexer = new QilletniLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var qilletniParser = new QilletniParser(tokenStream);

        // TODO: Temporary, make actual listeners for code!
        lexer.addErrorListener(new DocErrorListener());
        qilletniParser.addErrorListener(new DocErrorListener());

        var globalOverride = globalScope == null ? this.globalScope : globalScope;

        var symbolTable = new SymbolTableImpl(pathState.path().toString());

        LOGGER.debug("global override: {}", globalScope);

        QilletniParser.ProgContext programContext = qilletniParser.prog();
        var qilletniVisitor = new QilletniVisitor(symbolTable, symbolTables, globalOverride, dynamicProvider, entityDefinitionManager, nativeFunctionHandler, musicPopulator, listTypeTransformer, backgroundTaskExecutor, debugSupport, qilletniStackTrace,
                (importedFile, importAs) -> importFileFromStream(pathState.importFrom(importedFile), importAs, globalOverride));
        
        symbolTables.put(symbolTable, qilletniVisitor);

        qilletniVisitor.visit(programContext);

        return symbolTable;
    }

    private Optional<ImportAliasType> importFileFromStream(ImportPathState pathState) {
        return importFileFromStream(pathState, null, globalScope);
    }

    private Optional<ImportAliasType> importFileFromStream(ImportPathState pathState, String importAs, Scope global) {
        try {
            if (importAs == null && importCache.contains(pathState)) {
                LOGGER.debug("Skipping import for {}:{}", pathState.libraryName(), pathState.path());
                // Already imported file, no alias needed
                return Optional.empty();
            }

            if (importAs != null) {
                LOGGER.debug("global override parent for ({}) = {}", pathState.path(), globalScope);
                global = new ScopeImpl(globalScope, Scope.ScopeType.ALIASED_GLOBAL, "FG " + pathState.path());
            }

            var symbolTable = runProgram(libraryRegistrar.findLibraryByPath(pathState.libraryName(), pathState.path()).orElseThrow(FileNotFoundException::new), pathState, global);

            importCache.add(pathState);

            if (importAs != null) {
                LOGGER.debug("Creating import alias '{}' for: {}", importAs, symbolTable.currentScope());
                return Optional.of(new ImportAliasTypeImpl(importAs, symbolTable.currentScope()));
            }
        } catch (QilletniNativeInvocationException e) {
            throw e;
        } catch (IOException e) {
            var qce = new QilletniNativeInvocationException(e);
            qce.setQilletniStackTrace(qilletniStackTrace);
            qce.setMessage("Unable to import file '%s' in library %s".formatted(pathState.path(), pathState.libraryName()));
            throw qce;
        } catch (Exception e) {
            var qce = new QilletniNativeInvocationException(e);
            qce.setQilletniStackTrace(qilletniStackTrace);
            qce.setMessage(e.getMessage());
            throw qce;
        }

        return Optional.empty();
    }
    
    public void shutdown() {
        dynamicProvider.shutdownProviders();
    }

    public Map<SymbolTable, QilletniVisitor> getSymbolTables() {
        return symbolTables;
    }

    public NativeFunctionHandler getNativeFunctionHandler() {
        return nativeFunctionHandler;
    }

    public LibraryRegistrar getLibraryRegistrar() {
        return libraryRegistrar;
    }

    public Scope getGlobalScope() {
        return globalScope;
    }

    public EntityDefinitionManager getEntityDefinitionManager() {
        return entityDefinitionManager;
    }
}
