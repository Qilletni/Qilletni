package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.music.Track;

import java.util.List;

/**
 * A weighted pick that was thrown away because it repeated the last one.
 *
 * @param attempt The steps of the rejected pick, from the rejecting level inwards
 * @param track   The track it would have played
 */
public record WeightRejection(List<WeightStep> attempt, Track track, Reason reason) {

    public enum Reason {
        /** The track was the last one picked, and the entry can't repeat its track */
        SAME_TRACK,
        /** The entry was the last one picked, and it can't repeat */
        SAME_ENTRY
    }
}
