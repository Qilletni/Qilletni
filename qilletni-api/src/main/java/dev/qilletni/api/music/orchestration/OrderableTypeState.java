package dev.qilletni.api.music.orchestration;

import dev.qilletni.api.lang.types.OrderableTracksType;

/**
 * Holds any stateful information that a {@link OrderableTracksType} may need for selecting its next track.
 * This could be any type that contains multiple tracks that could be ordered.
 * <br>
 * This holds an index that is incremented when a track is chosen, used for when the orderable type is being selected
 * from sequentially. This allows the first song to be chosen, then the second, third, etc.
 */
public interface OrderableTypeState {

    /**
     * Retrieves the current {@link OrderableTracksType} associated with this state. This represents any type
     * containing multiple songs that could be ordered.
     *
     * @return The {@link OrderableTracksType}
     */
    OrderableTracksType getOrderableType();

    /**
     * Gets the current index that is incremented by {@link #getAndIncrementSequentialIndex()}, used for getting tracks
     * in order. This is effectively a "peek" of {@link #getAndIncrementSequentialIndex()}.
     * 
     * @return Gets the index of the current track that should be chosen
     */
    int getSequentialIndex();

    /**
     * Gets the index to choose a song by, and then increments it. If the new index goes past the length of the
     * collection, it wraps back to <code>0</code>. 
     * 
     * @return The index of the current track that should be chosen
     */
    int getAndIncrementSequentialIndex();

    /**
     * Returns a Qilletni-user-friendly string to display this state.
     * 
     * @return A string representation of this state
     */
    String stringValue();
    
}
