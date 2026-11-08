package dev.qilletni.impl.debug;

import java.util.List;

/**
 * The songs a list, collection or album weight entry picks from.
 *
 * @param evenChance If every song is equally likely when the entry is picked, which isn't so for a collection or album
 *                   with its own weights
 */
public record WeightEntrySongs(List<Song> songs, boolean evenChance) {

    /**
     * @param song Display text, e.g. {@code "Title" by "Artist"}
     */
    public record Song(String song, String trackId) {}
}
