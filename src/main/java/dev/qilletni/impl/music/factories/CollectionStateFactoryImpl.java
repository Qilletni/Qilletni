package dev.qilletni.impl.music.factories;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.factories.CollectionStateFactory;
import dev.qilletni.api.music.orchestration.CollectionState;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lang.types.orderable.OrderableTracksTypeInitializer;
import dev.qilletni.impl.music.orchestration.CollectionStateImpl;

public class CollectionStateFactoryImpl implements CollectionStateFactory {

    private final DynamicProvider dynamicProvider;
    private final MusicPopulator musicPopulator;

    public CollectionStateFactoryImpl(DynamicProvider dynamicProvider, MusicPopulator musicPopulator) {
        this.dynamicProvider = dynamicProvider;
        this.musicPopulator = musicPopulator;
    }

    @Override
    @SuppressWarnings("removal")
    public CollectionState createFromCollection(CollectionType collection) {
        return new CollectionStateImpl(collection, new OrderableTracksTypeInitializer(musicPopulator, dynamicProvider.getMusicCache()));
    }
}
