package dev.qilletni.impl.music.factories;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.factories.CollectionTypeFactory;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lang.types.CollectionTypeImpl;

public class CollectionTypeFactoryImpl implements CollectionTypeFactory {

    private final DynamicProvider dynamicProvider;
    private final MusicPopulator musicPopulator;

    public CollectionTypeFactoryImpl(DynamicProvider dynamicProvider, MusicPopulator musicPopulator) {
        this.dynamicProvider = dynamicProvider;
        this.musicPopulator = musicPopulator;
    }

    @Override
    public CollectionType createCollectionFromTrack(Playlist playlist) {
        return new CollectionTypeImpl(dynamicProvider, musicPopulator, playlist);
    }
}
