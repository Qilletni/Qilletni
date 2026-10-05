package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.OrderableTracksType;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.orchestration.OrderableTypeState;
import dev.qilletni.impl.lang.types.orderable.OrderableTracksTypeInitializer;

import java.util.List;

/**
 * The active state of an {@link OrderableTracksType} being played.
 * <br/>
 * <b>NOTE</b>: This class is NOT dynamic
 */
public class OrderableTypeStateImpl implements OrderableTypeState {
    
    private final OrderableTracksType orderableType;
    
    // Lazily initialized
    private final List<Track> tracks;
    private int currentIndex = 0;

    public OrderableTypeStateImpl(OrderableTracksType orderableTracksType, OrderableTracksTypeInitializer initializer) {
        this.orderableType = orderableTracksType;
        this.tracks = initializer.getTracks(orderableTracksType);
    }

    @Override
    public OrderableTracksType getOrderableType() {
        return orderableType;
    }

    @Override
    public int getSequentialIndex() {
        return currentIndex;
    }

    @Override
    public int getAndIncrementSequentialIndex() {
        int index = currentIndex++;
        
        if (currentIndex >= tracks.size()) {
            currentIndex = 0;
        }
        
        return index;
    }

    @Override
    public String stringValue() {
        return "collection-state(index=%d, coll=%s)".formatted(currentIndex, orderableType.stringValue());
    }
}
