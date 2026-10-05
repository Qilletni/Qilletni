package dev.qilletni.api.lang.types;

import dev.qilletni.api.lang.types.collection.CollectionDefinition;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.music.Playlist;

/**
 * Represents a <code>collection</code> type in Qilletni. This is essentially an abstracted playlist, or list of songs.
 * This may be created by the <code>"Collection Name" collection by "Creator Name"</code> expression syntax.
 */
public non-sealed interface CollectionType extends AnyType, OrderableTracksType {

    /**
     * Retrieves the current collection definition associated with the implementing object. The collection definition 
     * determines how the collection data is classified or resolved, such as by URL, name and creator, prepopulated data,
     * or a list of specific songs.
     *
     * @return The {@link CollectionDefinition} representing the current method of collection classification or resolution
     */
    CollectionDefinition getCollectionDefinition();

    /**
     * Sets the collection definition for the implementing object. The collection definition determines
     * how the collection data is classified or resolved, such as by URL, name and creator, prepopulated data,
     * or a list of specific songs.
     *
     * @param collectionDefinition The {@link CollectionDefinition} representing the classification method to be set
     */
    void setCollectionDefinition(CollectionDefinition collectionDefinition);

    /**
     * Retrieves the supplied URL for a specific collection, if the URL was provided.
     *
     * @return A string representing the user-supplied URL
     */
    String getSuppliedUrl();

    /**
     * Retrieves the supplied title for a specific collection, if the title was provided.
     *
     * @return A string representing the user-supplied title
     */
    String getSuppliedName();

    /**
     * Retrieves the supplied creator for a specific collection, if the creator's name was provided.
     *
     * @return A string representing the user-supplied creator's name
     */
    String getSuppliedCreator();

    /**
     * Retrieves the creator as a User {@link EntityType} using the given entity definition manager.
     * The creator is resolved using the provided {@link EntityDefinitionManager}.
     *
     * @param entityDefinitionManager The manager responsible for defining and resolving entity definitions
     * @return The {@link EntityType} representing the creator resolved through the entity definition manager
     */
    EntityType getCreator(EntityDefinitionManager entityDefinitionManager);

    /**
     * Gets the current {@link Playlist} instance associated with the current collection for the current service
     * provider. 
     * 
     * @return An instance of {@link Playlist} backing the collection
     */
    Playlist getPlaylist();

    /**
     * Checks whether service provider data has been successfully populated for the current instance.
     *
     * @return true if service provider data is populated, false otherwise
     */
    boolean isSpotifyDataPopulated();

    /**
     * Populates the music provider instance of the {@link Playlist} stored in the CollectionType. This must be called
     * before invoking methods that deal with the actual contents of the Album, such as getting artists.
     *
     * @param playlist The {@link Playlist} instance to populate
     */
    void populateSpotifyData(Playlist playlist);
}
