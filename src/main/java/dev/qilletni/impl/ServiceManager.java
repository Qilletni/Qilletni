package dev.qilletni.impl;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.lib.qll.QllInfo;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lib.persistence.PackageConfigImpl;
import dev.qilletni.impl.music.orchestration.DefaultTrackOrchestrator;
import dev.qilletni.impl.music.supplier.DynamicProviderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ServiceManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceManager.class);
    
    public static DynamicProvider createDynamicProvider(List<QllInfo> qllInfos) {
        var providers = qllInfos.stream()
                .map(QllInfo::providerClass)
                .filter(Objects::nonNull)
                .map(ServiceManager::loadServiceProviderClass)
                .filter(Optional::isPresent).map(Optional::get).toList();
        
        var dynamicProvider = new DynamicProviderImpl();
        
        for (var provider : providers) {
            // Pass in unloaded PackageConfig to the provider. They don't have to load it (or create one) if they don't need to.
            var packageConfig = PackageConfigImpl.createPackageConfig(provider.getName());
            provider.initialize(DefaultTrackOrchestrator::new, packageConfig).exceptionally(t -> {
                LOGGER.error("Failed to initialize service provider: {}", provider.getName(), t);
                return null;
            }).join();
            
            dynamicProvider.addServiceProvider(provider);
        }
        
        return dynamicProvider;
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
    
}
