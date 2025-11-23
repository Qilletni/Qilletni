package dev.qilletni.impl.music.supplier;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.lib.persistence.PackageConfig;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicFetcher;
import dev.qilletni.api.music.StringIdentifier;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.factories.AlbumTypeFactory;
import dev.qilletni.api.music.factories.CollectionTypeFactory;
import dev.qilletni.api.music.factories.SongTypeFactory;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.api.music.strategies.MusicStrategies;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lang.exceptions.InvalidProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class DynamicProviderImpl implements DynamicProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicProviderImpl.class);
    
    private final Map<String, ServiceProvider> providers = new HashMap<>();
    
    private ServiceProvider currentProvider;
    private SongTypeFactory songTypeFactory;
    private CollectionTypeFactory collectionTypeFactory;
    private AlbumTypeFactory albumTypeFactory;

    @Override
    public void addServiceProvider(ServiceProvider serviceProvider) {
        providers.put(serviceProvider.getName().toLowerCase(), serviceProvider);
    }

    @Override
    public void switchProvider(String providerName) {
        currentProvider = getProvider(providerName);
        
        LOGGER.debug("Switched provider to {}", providerName);
    }

    @Override
    public void initializeInitialProvider(PackageConfig packageConfig) {
        var initialProviderName = packageConfig.get("initialProvider").orElse("spotify");
        
        if (providers.containsKey(initialProviderName)) {
            switchProvider(initialProviderName);
        } else {
            switchProvider(providers.keySet().toArray(String[]::new)[0]);
        }
    }

    @Override
    public ServiceProvider getProvider(String providerName) {
        if (!providers.containsKey(providerName.toLowerCase())) {
            throw new InvalidProviderException(String.format("No provider of name '%s' found", providerName));
        }

        return providers.get(providerName.toLowerCase());
    }

    @Override
    public ServiceProvider getCurrentProvider() {
        return currentProvider;
    }

    @Override
    public void initFactories(SongTypeFactory songTypeFactory, CollectionTypeFactory collectionTypeFactory, AlbumTypeFactory albumTypeFactory) {
        this.songTypeFactory = songTypeFactory;
        this.collectionTypeFactory = collectionTypeFactory;
        this.albumTypeFactory = albumTypeFactory;
    }

    @Override
    public void shutdownProviders() {
        providers.values().forEach(ServiceProvider::shutdown);
    }

    @Override
    public MusicCache getMusicCache() {
        return currentProvider.getMusicCache();
    }

    @Override
    public MusicFetcher getMusicFetcher() {
        return currentProvider.getMusicFetcher();
    }

    @Override
    public TrackOrchestrator getTrackOrchestrator() {
        return currentProvider.getTrackOrchestrator();
    }

    @Override
    public StringIdentifier getStringIdentifier() {
        if (songTypeFactory == null || collectionTypeFactory == null || albumTypeFactory == null) {
            throw new IllegalStateException("DynamicProvider#initFactories() must be invoked before getting a StringIdentifier");
        }
        
        return currentProvider.getStringIdentifier(songTypeFactory, collectionTypeFactory, albumTypeFactory);
    }

    @Override
    public MusicStrategies<?, ?> getMusicStrategies() {
        return currentProvider.getMusicStrategies();
    }

    @Override
    public PlayActor getPlayActor() {
        return currentProvider.getPlayActor();
    }
}
