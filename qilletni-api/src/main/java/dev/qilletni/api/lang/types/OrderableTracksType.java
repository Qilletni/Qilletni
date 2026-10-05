package dev.qilletni.api.lang.types;

import dev.qilletni.api.lang.types.collection.CollectionOrder;

/**
 * A type that has controls over how tracks from it are played.
 */
public sealed interface OrderableTracksType permits CollectionType, AlbumType {

    /**
     * Retrieves the current ordering of the collection when it is played. The order specifies how the collection items
     * are arranged, such as in a sequential manner or shuffled.
     *
     * @return The {@link CollectionOrder} representing the current ordering of the collection
     */
    CollectionOrder getOrder();

    /**
     * Sets the ordering of the collection when it is played. The order specifies how the items in the collection
     * are arranged, such as sequentially or shuffled.
     *
     * @param order The {@link CollectionOrder} to set for the collection
     */
    void setOrder(CollectionOrder order);

    /**
     * Gets the weights applied to the collection.
     *
     * @return The weights applied to the collection
     */
    WeightsType getWeights();

    /**
     * Sets the weights for the collection.
     *
     * @param weights The {@link WeightsType} object to be associated with the collection
     */
    void setWeights(WeightsType weights);

    /**
     * Gets the string representation of the orderable tracks type.
     * 
     * @return The string representation of the orderable tracks type
     */
    String stringValue();
    
}
