package dev.qilletni.impl.lang.types.orderable;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.OrderableTracksType;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Track;

import java.util.List;

public class OrderableTracksTypeInitializer {
    
    private final MusicPopulator musicPopulator;
    private final MusicCache musicCache;

    public OrderableTracksTypeInitializer(MusicPopulator musicPopulator, MusicCache musicCache) {
        this.musicPopulator = musicPopulator;
        this.musicCache = musicCache;
    }

    public List<Track> getTracks(OrderableTracksType orderableTracksType) {
        return switch (orderableTracksType) {
            case AlbumType albumType -> {
                musicPopulator.populateAlbum(albumType);
                yield musicCache.getAlbumTracks(albumType.getAlbum());
            }
            case CollectionType collectionType -> {
                musicPopulator.populateCollection(collectionType);
                yield musicCache.getPlaylistTracks(collectionType.getPlaylist());
            }
        };
    }
    
}
