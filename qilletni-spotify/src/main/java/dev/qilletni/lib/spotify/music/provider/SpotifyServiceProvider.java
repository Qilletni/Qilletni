package dev.qilletni.lib.spotify.music.provider;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.exceptions.config.ConfigInitializeException;
import dev.qilletni.api.lib.persistence.PackageConfig;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicFetcher;
import dev.qilletni.api.music.MusicTypeConverter;
import dev.qilletni.api.music.StringIdentifier;
import dev.qilletni.api.music.factories.AlbumTypeFactory;
import dev.qilletni.api.music.factories.CollectionTypeFactory;
import dev.qilletni.api.music.factories.SongTypeFactory;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.play.DefaultRoutablePlayActor;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.api.music.strategies.MusicStrategies;
import dev.qilletni.lib.spotify.async.ExecutorServiceUtility;
import dev.qilletni.lib.spotify.database.HibernateUtil;
import dev.qilletni.lib.spotify.music.QueuePlayActor;
import dev.qilletni.lib.spotify.music.SpotifyMusicCache;
import dev.qilletni.lib.spotify.music.SpotifyMusicFetcher;
import dev.qilletni.lib.spotify.music.SpotifyMusicTypeConverter;
import dev.qilletni.lib.spotify.music.SpotifyStringIdentifier;
import dev.qilletni.lib.spotify.music.auth.SpotifyApiSingleton;
import dev.qilletni.lib.spotify.music.auth.SpotifyAuthorizer;
import dev.qilletni.lib.spotify.music.auth.pkce.SpotifyPKCEAuthorizer;
import dev.qilletni.lib.spotify.music.creator.PlaylistCreator;
import dev.qilletni.lib.spotify.music.strategies.SpotifyMusicStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class SpotifyServiceProvider implements ServiceProvider {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SpotifyServiceProvider.class);
    
    private PackageConfig packageConfig;
    private SpotifyMusicCache musicCache;
    private MusicFetcher musicFetcher;
    private TrackOrchestrator trackOrchestrator;
    private MusicTypeConverter musicTypeConverter;
    private StringIdentifier stringIdentifier;
    private SpotifyAuthorizer authorizer;
    private SpotifyMusicStrategies musicStrategies;
    private PlayActor playActor;
    
    private static ServiceProvider serviceProviderInstance;
    
    @Override
    public CompletableFuture<Void> initialize(BiFunction<PlayActor, MusicCache, TrackOrchestrator> defaultTrackOrchestratorFunction, PackageConfig packageConfig) {
        this.packageConfig = packageConfig;
        populateInitialConfig();
        initConfig();
        
        authorizer = SpotifyPKCEAuthorizer.createWithCodes(packageConfig, packageConfig.getOrThrow("clientId"), packageConfig.getOrThrow("redirectUri"));
        
        return authorizer.authorizeSpotify().thenRun(() -> {
            var spotifyMusicFetcher = new SpotifyMusicFetcher(authorizer);
            SpotifyApiSingleton.setSpotifyAuthorizer(authorizer);
            musicFetcher = spotifyMusicFetcher;
            musicCache = new SpotifyMusicCache(spotifyMusicFetcher);
            playActor = new DefaultRoutablePlayActor(new QueuePlayActor());
            trackOrchestrator = defaultTrackOrchestratorFunction.apply(playActor, musicCache);

            musicTypeConverter = new SpotifyMusicTypeConverter(musicCache);
            musicStrategies = new SpotifyMusicStrategies();
            
            serviceProviderInstance = this;
        });
    }

    @Override
    public void shutdown() {
        authorizer.shutdown();
        ExecutorServiceUtility.shutdown(PlaylistCreator.EXECUTOR_SERVICE);
    }

    @Override
    public String getName() {
        return "Spotify";
    }

    @Override
    public MusicCache getMusicCache() {
        return Objects.requireNonNull(musicCache, "ServiceProvider#initialize must be invoked to initialize MusicCache");
    }

    @Override
    public MusicFetcher getMusicFetcher() {
        return Objects.requireNonNull(musicFetcher, "ServiceProvider#initialize must be invoked to initialize MusicFetcher");
    }

    @Override
    public TrackOrchestrator getTrackOrchestrator() {
        return Objects.requireNonNull(trackOrchestrator, "ServiceProvider#initialize must be invoked to initialize TrackOrchestrator");
    }

    @Override
    public MusicStrategies<?, ?> getMusicStrategies() {
        return Objects.requireNonNull(musicStrategies, "ServiceProvider#initialize must be invoked to initialize MusicStrategies");
    }

    @Override
    public MusicTypeConverter getMusicTypeConverter() {
        return Objects.requireNonNull(musicTypeConverter, "ServiceProvider#initialize must be invoked to initialize MusicTypeConverter");
    }

    @Override
    public StringIdentifier getStringIdentifier(SongTypeFactory songTypeFactory, CollectionTypeFactory collectionTypeFactory, AlbumTypeFactory albumTypeFactory) {
        if (stringIdentifier == null) {
            return stringIdentifier = new SpotifyStringIdentifier(musicCache, songTypeFactory, collectionTypeFactory, albumTypeFactory);
        }
        
        return stringIdentifier;
    }

    @Override
    public PlayActor getPlayActor() {
        return Objects.requireNonNull(playActor, "ServiceProvider#initialize must be invoked to initialize PlayActor");
    }

    /**
     * Populate the config if it's empty (i.e. first run).
     */
    private void populateInitialConfig() {
        packageConfig.loadConfig();
        
        if (packageConfig.get("dbUrl").isEmpty() && packageConfig.get("dbUsername").isEmpty() && packageConfig.get("dbPassword").isEmpty()) {
            LOGGER.debug("Spotify config is empty, populating with default values");
            
            packageConfig.set("dbUrl", "jdbc:postgresql://localhost:5435/qilletni");
            packageConfig.set("dbUsername", "qilletni");
            packageConfig.set("dbPassword", "pass");
            packageConfig.saveConfig();
        } else {
            LOGGER.debug("Spotify config already populated, skipping");
        }
    }

    private void initConfig() {
        packageConfig.loadConfig();
        
        var requiredOptions = List.of("clientId", "redirectUri", "dbUrl", "dbUsername", "dbPassword");
        var allFound = true;

        for (var option : requiredOptions) {
            if (packageConfig.get(option).isEmpty()) {
                allFound = false;
                LOGGER.error("Required config value '{}' not found in Spotify config", option);
            }
        }
        
        if (!allFound) {
            throw new ConfigInitializeException("Spotify config is missing required options, aborting");
        }

        HibernateUtil.initializeSessionFactory(packageConfig.getOrThrow("dbUrl"), packageConfig.getOrThrow("dbUsername"), packageConfig.getOrThrow("dbPassword"));
    }
    
    public static ServiceProvider getServiceProviderInstance() {
        return Objects.requireNonNull(serviceProviderInstance, "ServiceProvider#initialize must be invoked to initialize ServiceProvider");
    }
}
