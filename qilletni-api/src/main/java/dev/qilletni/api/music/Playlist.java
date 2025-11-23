package dev.qilletni.api.music;

import dev.qilletni.api.auth.ServiceProvider;

import java.util.List;
import java.util.Optional;

/**
 * A playlist/collection that is stored in a database. The implementation of this is service provider-dependent.
 */
public interface Playlist {

    /**
     * The unique ID of the playlist. This means different things for each implementation.
     * 
     * @return The unique ID of the playlist
     */
    String getId();

    /**
     * The name of the playlist.
     * 
     * @return The name of the playlist
     */
    String getTitle();

    /**
     * The user that created the playlist.
     * 
     * @return The user that created the playlist
     */
    User getCreator();

    /**
     * The number of tracks in the playlist.
     * 
     * @return The number of tracks in the playlist
     */
    int getTrackCount();

    /**
     * If the playlist was created with a pre-populated set of songs, a list of contained tracks are returned.
     * Otherwise, an empty optional will be returned, and it should be fetched via the implementation-specific way. 
     * 
     * @return An optional containing pre-populated tracks, if no additional work should be done to search tracks
     */
    default Optional<List<Track>> getTracks() {
        return Optional.empty();
    }

    /**
     * The service provider that this playlist is from.
     * 
     * @return The service provider that this playlist is from
     */
    Optional<ServiceProvider> getServiceProvider();
    
}
