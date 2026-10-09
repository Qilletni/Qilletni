package dev.qilletni.api.music;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.SongType;

/**
 * Populates music types with data from the {@link MusicCache}, used before accessing the data.
 */
public interface MusicPopulator {

    /**
     * Sets the eager loading flag. Eager loading will populate music types as soon as possible. Non-eager loading will
     * wait until necessary.
     * 
     * @param eagerMusicLoad Whether to enable eager loading.
     * @throws UnsupportedOperationException If the implementation doesn't support changing eager loading
     * @since 1.1.0
     */
    default void setEagerMusicLoad(boolean eagerMusicLoad) {
        throw new UnsupportedOperationException("setEagerMusicLoad is not supported by this " + getClass().getName());
    }

    /**
     * Gets whether eager loading is enabled.
     * 
     * @return Whether eager loading is enabled. Defaults to <code>false</code>.
     * @since 1.1.0
     */
    default boolean getEagerMusicLoad() {
        return false;
    }

    /**
     * If eager loading is enabled, the given song is populated via {@link #populateSong(SongType)}.
     * Otherwise, nothing occurs.
     * TODO: Make this into some kind of factory for SongTypes?
     *
     * @param songType The song to populate
     * @return The supplied {@link SongType}
     */
    SongType initiallyPopulateSong(SongType songType);

    /**
     * Takes a {@link SongType} and collects all relevant data via {@link MusicCache} and populates the type with
     * {@link SongType#populateSpotifyData(Track)}.
     * 
     * @param songType The song to populate
     */
    void populateSong(SongType songType);

    /**
     * If eager loading is enabled, the given album is populated via {@link #populateAlbum(AlbumType)}.
     * Otherwise, nothing occurs.
     * TODO: Make this into some kind of factory for AlbumTypes?
     *
     * @param albumType The album to populate
     * @return The supplied {@link AlbumType}
     */
    AlbumType initiallyPopulateAlbum(AlbumType albumType);

    /**
     * Takes an {@link AlbumType} and collects all relevant data via {@link MusicCache} and populates the type with
     * {@link SongType#populateSpotifyData(Track)}.
     * 
     * @param albumType The album to populate
     */
    void populateAlbum(AlbumType albumType);

    /**
     * If eager loading is enabled, the given album is populated via {@link #populateCollection(CollectionType)}.
     * Otherwise, nothing occurs.
     * TODO: Make this into some kind of factory for CollectionTypes?
     *
     * @param collectionType The album to populate
     * @return The supplied {@link AlbumType}
     */
    CollectionType initiallyPopulateCollection(CollectionType collectionType);

    /**
     * Takes an {@link CollectionType} and collects all relevant data via {@link MusicCache} and populates the type
     * with {@link CollectionType#populateSpotifyData(Playlist)}.
     * 
     * @param collectionType The collection to populate
     */
    void populateCollection(CollectionType collectionType);
}
