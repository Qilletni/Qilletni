package dev.qilletni.api.music.supplier;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.lib.persistence.PackageConfig;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicFetcher;
import dev.qilletni.api.music.StringIdentifier;
import dev.qilletni.api.music.factories.AlbumTypeFactory;
import dev.qilletni.api.music.factories.CollectionTypeFactory;
import dev.qilletni.api.music.factories.SongTypeFactory;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.api.music.strategies.MusicStrategies;

/**
 * Represents a service provider that can be switched at runtime. It is important to pass around an instance of this
 * interface rather than individual service provider interfaces (e.g. {@link MusicCache}, {@link MusicFetcher}, etc.)
 * because if the service provider is switched, the underlying implementations will need to be changed too.
 */
public interface DynamicProvider {

    /**
     * Adds a service provider to the list of available service providers.
     * 
     * @param serviceProvider The service provider to add
     */
    void addServiceProvider(ServiceProvider serviceProvider);

    /**
     * Switches the current service provider to the one with the given name.
     * 
     * @param providerName The name of the service provider to switch to
     */
    void switchProvider(String providerName);

    /**
     * Initializes the service provider that is initially used by the program.
     * 
     * @param internalPackageConfig The {@link PackageConfig} used by the internal system
     */
    void initializeInitialProvider(PackageConfig internalPackageConfig);

    /**
     * Gets the service provider with the given name.
     * 
     * @param providerName The name of the service provider to get
     * @return The found service provider
     */
    ServiceProvider getProvider(String providerName);

    /**
     * Gets the current service provider.
     * 
     * @return The current service provider
     */
    ServiceProvider getCurrentProvider();

    /**
     * Initializes the music factories. This only needs to be called once.
     * 
     * @param songTypeFactory The song type factory to use
     * @param collectionTypeFactory The collection type factory to use
     * @param albumTypeFactory The album type factory to use
     */
    void initFactories(SongTypeFactory songTypeFactory, CollectionTypeFactory collectionTypeFactory, AlbumTypeFactory albumTypeFactory);

    /**
     * Shuts down all service providers. This should clean up any async tasks currently running.
     */
    void shutdownProviders();

    /**
     * Gets the music cache used by the current service provider. Do not keep a reference to this object that may span
     * a nonatomic context, as the implementation may switch.
     * 
     * @return The current music cache
     */
    MusicCache getMusicCache();

    /**
     * Gets the music fetcher used by the current service provider. Do not keep a reference to this object that may span
     * a nonatomic context, as the implementation may switch.
     * 
     * @return The current music fetcher
     */
    MusicFetcher getMusicFetcher();

    /**
     * Gets the track orchestrator used by the current service provider. Do not keep a reference to this object that may
     * span a nonatomic context, as the implementation may switch.
     * 
     * @return The current track orchestrator
     */
    TrackOrchestrator getTrackOrchestrator();

    /**
     * Gets the {@link StringIdentifier} used by the current service provider. Do not keep a reference to this object
     * that may span a nonatomic context, as the implementation may switch.
     * 
     * @return The current {@link StringIdentifier}
     */
    StringIdentifier getStringIdentifier();

    /**
     * Gets the music strategies for the current service provider.
     *
     * @return The current {@link MusicStrategies}
     */
    MusicStrategies<?, ?> getMusicStrategies();

    /**
     * Gets the {@link PlayActor} used by the current service provider. Do not keep a reference to this object that may
     * span a nonatomic context, as the implementation may switch.
     * 
     * @return The current {@link PlayActor}
     */
    PlayActor getPlayActor();
}
