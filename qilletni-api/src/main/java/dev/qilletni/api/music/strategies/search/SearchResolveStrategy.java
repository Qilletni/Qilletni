package dev.qilletni.api.music.strategies.search;

import dev.qilletni.api.music.Track;

import java.util.List;

/**
 * A strategy to pick a track after being searched for. When a track is searched for, a music API will generally give a
 * list of results. The order may not always be ideal, so this interface provides a mechanism of choosing what track
 * gets picked.
 *
 * @param <T> The input type of a track collection. This may be a list or another containing object (such as an API
 *           DTO). This is not a list of {@link Track}s because it is not an entity yet
 */
public interface SearchResolveStrategy<T, O> {

    /**
     * The name of the resolve strategy.
     *
     * @return The strategy name
     */
    String getName();

    /**
     * Chooses a track from a list of fetched tracks.
     *
     * @param tracks The tracks fetched
     * @return The single track to resolve to
     */
    O resolveTrack(T tracks, String title, String artist);

    /**
     * Gets the type of the track, for type checking during registration.
     *
     * @return The class of the track type
     */
    Class<T> getTrackType();

}
