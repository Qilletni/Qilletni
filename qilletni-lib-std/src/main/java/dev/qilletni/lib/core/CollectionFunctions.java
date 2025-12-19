package dev.qilletni.lib.core;

import dev.qilletni.api.lang.internal.FunctionInvoker;
import dev.qilletni.api.lang.types.*;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.list.ListInitializer;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.lib.annotations.BeforeAnyInvocation;
import dev.qilletni.api.lib.annotations.NativeOn;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.factories.CollectionStateFactory;
import dev.qilletni.api.music.factories.SongTypeFactory;
import dev.qilletni.api.music.orchestration.CollectionState;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.supplier.DynamicProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@NativeOn("collection")
public class CollectionFunctions {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionFunctions.class);

    private final MusicPopulator musicPopulator;
    private final EntityDefinitionManager entityDefinitionManager;
    private final FunctionInvoker functionInvoker;
    private final SongTypeFactory songTypeFactory;
    private final MusicCache musicCache;
    private final TrackOrchestrator trackOrchestrator;
    private final CollectionStateFactory collectionStateFactory;
    private final ListInitializer listInitializer;

    // TODO: Yeah this will probably (definitely) cause memory leaks
    //       Better fix this later or figure out a better way
    private static final Map<CollectionType, CollectionState> collectionStates = new HashMap<>();

    public CollectionFunctions(MusicPopulator musicPopulator, EntityDefinitionManager entityDefinitionManager, FunctionInvoker functionInvoker, SongTypeFactory songTypeFactory, DynamicProvider dynamicProvider, CollectionStateFactory collectionStateFactory, ListInitializer listInitializer) {
        this.musicPopulator = musicPopulator;
        this.entityDefinitionManager = entityDefinitionManager;
        this.functionInvoker = functionInvoker;
        this.songTypeFactory = songTypeFactory;
        this.musicCache = dynamicProvider.getMusicCache();
        this.trackOrchestrator = dynamicProvider.getTrackOrchestrator();
        this.collectionStateFactory = collectionStateFactory;
        this.listInitializer = listInitializer;
    }

    @BeforeAnyInvocation
    public void setupCollection(CollectionType collectionType) {
        musicPopulator.populateCollection(collectionType);
    }

    public String getId(CollectionType collectionType) {
        return collectionType.getPlaylist().getId();
    }

    public String getUrl(CollectionType collectionType) {
        return Objects.requireNonNullElse(collectionType.getSuppliedUrl(), "");
    }

    public String getName(CollectionType collectionType) {
        return collectionType.getPlaylist().getTitle();
    }

    public EntityType getCreator(CollectionType collectionType) {
        return collectionType.getCreator(entityDefinitionManager);
    }

    public int getTrackCount(CollectionType collectionType) {
        return collectionType.getPlaylist().getTrackCount();
    }

    public String toVerboseString(CollectionType collectionType) {
        var tracks = musicCache.getPlaylistTracks(collectionType.getPlaylist());

        var songString = tracks.stream()
                .map(track -> "\"%s\" by \"%s\"".formatted(track.getName(), track.getArtist().getName()))
                .collect(Collectors.joining(", "));

        return "collection(%s)".formatted(songString);
    }

    public boolean anySongMatches(CollectionType collectionType, FunctionType functionType) {
        var tracks = musicCache.getPlaylistTracks(collectionType.getPlaylist());
        
        return tracks.stream().anyMatch(track -> {
            var song = songTypeFactory.createSongFromTrack(track);

            return functionInvoker.invokeFunction(functionType, List.of(song)).map(result -> {
                if (result instanceof BooleanType booleanType) {
                    LOGGER.debug("Song matches: {}", song);
                    return booleanType.getValue();
                } else {
                    return false;
                }
            }).orElse(false);
        });
    }

    public boolean containsArtist(CollectionType collectionType, EntityType artistEntity) {
        var artistId = artistEntity.getEntityScope().<StringType>lookup("_id").getValue().getValue();

        LOGGER.debug("Does playlist contain artist: {}", artistId);

        var tracks = musicCache.getPlaylistTracks(collectionType.getPlaylist());

        LOGGER.debug("Tracks: {}", tracks);

        return tracks.stream()
                .anyMatch(track -> {
                    LOGGER.debug("Track \"{}\" checking {} == {}", track.getName(), track.getArtist().getId(), artistId);
                    return track.getArtist().getId().equals(artistId);
                });
    }

    /**
     * This holds an internal collection state, specific to this method. It's not ideal but saves the user from
     * passing around a state, or potentially have inconsistent states with different plays.
     *
     * @param collectionType The collection to play from
     * @return The song that is next
     */
    public SongType nextSong(CollectionType collectionType) {
        var state = collectionStates.computeIfAbsent(collectionType, _ ->
                collectionStateFactory.createFromCollection(collectionType));

        return songTypeFactory.createSongFromTrack(trackOrchestrator.getTrackFromCollection(state));
    }

    public ListType getSongs(CollectionType collectionType) {
        List<Track> playlistTracks = musicCache.getPlaylistTracks(collectionType.getPlaylist());

        var recommendedSongs = playlistTracks.stream()
                .map(songTypeFactory::createSongFromTrack)
                .map(QilletniType.class::cast)
                .toList();

        return listInitializer.createList(recommendedSongs, QilletniTypeClass.SONG);
    }
}
