package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.OrderableTracksType;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.orchestration.CollectionState;
import dev.qilletni.impl.lang.types.orderable.OrderableTracksTypeInitializer;

import java.util.List;

/**
 * The active state of a {@link CollectionType} being played, kept so {@link CollectionState} remains supported.
 */
@SuppressWarnings("removal")
public class CollectionStateImpl extends OrderableTypeStateImpl implements CollectionState {

    public CollectionStateImpl(CollectionType collectionType, OrderableTracksTypeInitializer initializer) {
        super(collectionType, initializer);
    }

    @Override
    public CollectionType getCollection() {
        return (CollectionType) getOrderableType();
    }

    @Override
    public List<Track> getTracks() {
        return tracks;
    }

    @Override
    public OrderableTracksType getOrderableType() {
        return super.getOrderableType();
    }
}
