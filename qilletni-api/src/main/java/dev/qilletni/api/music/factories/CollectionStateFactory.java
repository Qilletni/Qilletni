package dev.qilletni.api.music.factories;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.music.orchestration.OrderableTypeState;

/**
 * Creates {@link OrderableTypeState}
 */
public interface CollectionStateFactory {

    /**
     * Creates a {@link OrderableTypeState} from a collection.
     *
     * @param collection The {@link CollectionType} to create a state for
     * @return The created state
     */
    OrderableTypeState createFromCollection(CollectionType collection);

}
