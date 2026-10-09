package dev.qilletni.api.music.factories;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.music.orchestration.CollectionState;

/**
 * Creates {@link CollectionState}
 */
public interface CollectionStateFactory {

    /**
     * Creates a {@link CollectionState} from a collection.
     *
     * @param collection The {@link CollectionType} to create a state for
     * @return The created state
     */
    @SuppressWarnings("removal")
    CollectionState createFromCollection(CollectionType collection);

}
