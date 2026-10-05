package dev.qilletni.impl;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.lib.qll.QllInfo;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lib.persistence.PackageConfigImpl;
import dev.qilletni.impl.music.MusicPopulatorImpl;
import dev.qilletni.impl.music.orchestration.DefaultTrackOrchestrator;
import dev.qilletni.impl.music.supplier.DynamicProviderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ServiceManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceManager.class);
    
    public static DynamicProviderCreation createDynamicProvider(List<QllInfo> qllInfos) {
        var providers = qllInfos.stream()
                .map(QllInfo::providerClass)
                .filter(Objects::nonNull)
                .map(ServiceManager::loadServiceProviderClass)
                .filter(Optional::isPresent).map(Optional::get).toList();
        
        var dynamicProvider = new DynamicProviderImpl();
        var musicPopulator = new MusicPopulatorImpl(dynamicProvider);
        
        for (var provider : providers) {
            // Pass in unloaded PackageConfig to the provider. They don't have to load it (or create one) if they don't need to.
            var packageConfig = PackageConfigImpl.createPackageConfig(provider.getName());
            provider.initialize((playActor, musicCache) -> new DefaultTrackOrchestrator(playActor, musicCache, musicPopulator), packageConfig).exceptionally(t -> {
                LOGGER.error("Failed to initialize service provider: {}", provider.getName(), t);
                return null;
            }).join();
            
            dynamicProvider.addServiceProvider(provider);
        }
        
        return new DynamicProviderCreation(dynamicProvider, musicPopulator);
    }
    
    private static Optional<ServiceProvider> loadServiceProviderClass(String className) {
        try {
            LOGGER.debug("Loading service provider: {}", className);
            return Optional.of((ServiceProvider) Thread.currentThread().getContextClassLoader().loadClass(className).getConstructor().newInstance());
        } catch (Exception e) {
            LOGGER.error("An exception occurred while loading service provider: " + className, e);
            return Optional.empty();
        }
    }
    
    public record DynamicProviderCreation(DynamicProvider dynamicProvider, MusicPopulator musicPopulator) {}
    
}
