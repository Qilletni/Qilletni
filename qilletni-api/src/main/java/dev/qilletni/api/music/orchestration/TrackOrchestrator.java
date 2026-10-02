package dev.qilletni.api.music.orchestration;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.collection.CollectionLimit;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.api.music.Track;

/**
 * A class to handle the way tracks are selected. This should ideally take in a {@link PlayActor} to handle playing.
 */
public interface TrackOrchestrator {

    /**
     * Plays a single track.
     * 
     * @param track The track to play
     */
    void playTrack(Track track);
    
    /**
     * Plays a collection of tracks. If looping, it will continue until the program exits.
     * 
     * @param collectionType The type of collection to play
     * @param loop Whether to loop the collection. If `false`, the collection will play each song once
     */
    void playCollection(CollectionType collectionType, boolean loop);
    
    /**
     * Plays a collection of tracks, with a limit on the number of tracks to play.
     * 
     * @param collectionType The type of collection to play
     * @param collectionLimit The limit of tracks to play
     */
    void playCollection(CollectionType collectionType, CollectionLimit collectionLimit);

    /**
     * Gets a single track from a collection state. If the collection is sequential, it will return the next unplayed
     * track in the collection.
     * 
     * @param collectionState The collection state to get the track from
     * @return The track to play
     */
    Track getTrackFromCollection(CollectionState collectionState);
    
    /**
     * Selects a single track from a weight.
     * 
     * @param weightsType The type of weight to get the track from
     * @return The track to play
     */
    Track getTrackFromWeight(WeightsType weightsType);
    
}
