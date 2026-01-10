package dev.qilletni.impl.lang.types;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.Artist;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.User;
import dev.qilletni.api.music.supplier.DynamicProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An internal type representing a track, album, artist, playlist, or user that can be dynamically converted to the
 * current service provider. Each of the parent QilletniTypes hold an instance of this instead of the actual type, so
 * the actual type can be switched out at runtime and allow for cross-service provider interaction.
 * 
 * @param <T> The type of music entity this represents
 */
public class DynamicMusicType<T> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicMusicType.class);

    private final Class<T> type;
    private final DynamicProvider dynamicProvider;
    private final Map<ServiceProvider, T> musicTypeMap;

    public DynamicMusicType(Class<T> type, DynamicProvider dynamicProvider) {
        this.type = type;
        this.dynamicProvider = dynamicProvider;
        this.musicTypeMap = new HashMap<>();
    }

    public DynamicMusicType(Class<T> type, DynamicProvider dynamicProvider, T initialMusicType) {
        this.type = type;
        this.dynamicProvider = dynamicProvider;
        this.musicTypeMap = new HashMap<>(Map.of(dynamicProvider.getCurrentProvider(), initialMusicType));
    }

    /**
     * Sets the music type for the current service provider.
     * 
     * @param musicType The music type to set
     */
    public void put(T musicType) {
        musicTypeMap.put(dynamicProvider.getCurrentProvider(), musicType);
    }
    
    /**
     * Gets the music type for the current service provider.
     * 
     * @return The music type for the current service provider
     */
    public T get() {
        return get(false);
    }
    
    /**
     * Gets the music type for the current service provider.
     * 
     * @param silent Whether to suppress errors if the music type cannot be converted
     * @return The music type for the current service provider
     */
    // TODO: In things like PlaylistToolFunctions#addToPlaylist(), lots of songs may be queried at once. Find a way to batch convert them all at once
    public T get(boolean silent) {
        var currentProvider = dynamicProvider.getCurrentProvider();
        return musicTypeMap.computeIfAbsent(currentProvider, k -> {
            var musicTypeConverter = currentProvider.getMusicTypeConverter();
            
            var optionalType = switch (type.getSimpleName()) {
                case "Track" -> musicTypeConverter.convertTrack(castValues(Track.class));
                case "Album" -> musicTypeConverter.convertAlbum(castValues(Album.class));
                case "Artist" -> musicTypeConverter.convertArtist(castValues(Artist.class));
                case "Playlist" -> musicTypeConverter.convertPlaylist(castValues(Playlist.class));
                case "User" -> musicTypeConverter.convertUser(castValues(User.class));
                default -> Optional.empty();
            };
            
            if (optionalType.isEmpty()) {
                if (!silent && !musicTypeMap.isEmpty()) {
                    LOGGER.error("Unable to convert a {} to service provider {} from the following: {}", type.getSimpleName(), currentProvider.getName(), musicTypeMap.keySet().stream().map(ServiceProvider::getName).collect(Collectors.joining(", ")));
                }
                
                return null;
            }
            
//            LOGGER.debug("Converted a {} to service provider {}:  {}", type.getSimpleName(), currentProvider.getName(), optionalType.get());
            
            return (T) optionalType.get();
        });
    }

    /**
     * Checks if the music type exists for the current service provider.
     */
    public boolean isPopulated() {
        // Check if it's present, or if it's unable to convert to the current provider
        return musicTypeMap.containsKey(dynamicProvider.getCurrentProvider()) || get(true) != null;
    }
    
    private <V> List<V> castValues(Class<V> clazz) {
        return musicTypeMap.values().stream().map(clazz::cast).toList();
    }
}
