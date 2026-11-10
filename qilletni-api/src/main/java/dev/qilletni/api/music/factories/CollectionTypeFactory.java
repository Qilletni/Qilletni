package dev.qilletni.api.music.factories;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.SongType;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;

import java.util.List;
import java.util.Set;

/**
 * Creates {@link CollectionType}s.
 */
public interface CollectionTypeFactory {

    /**
     * Creates a fully populated {@link CollectionType} from a given {@link Playlist}.
     * 
     * @param playlist The playlist to create the {@link CollectionType} from
     * @return The created {@link CollectionType}
     */
    CollectionType createCollectionFromTrack(Playlist playlist);

    /**
     * Creates a {@link CollectionType} with the provided songs.
     * 
     * @param tracks The songs to create the {@link CollectionType} from
     * @return The created {@link CollectionType}
     */
    CollectionType createCollectionFromTracks(List<Track> tracks);

    /**
     * Creates a {@link CollectionType} with the provided songs.
     * 
     * @param tracks The songs to create the {@link CollectionType} from
     * @return The created {@link CollectionType}
     */
    CollectionType createCollectionFromTracks(Set<Track> tracks);
    
}
