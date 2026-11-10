/**
 * Gets the ID of the collection. This is an ID identifiable by the provider which owns this collection.
 *
 * @returns[@type string] The ID of the collection
 */
native fun getId() on collection

/**
 * Gets the URL of the collection.
 *
 * @returns[@type string] The URL of the collection
 */
native fun getUrl() on collection

/**
 * Gets the name of the collection.
 *
 * @returns[@type string] The name of the collection
 */
native fun getName() on collection

/**
 * Gets the creator of the collection.
 *
 * @returns[@type std.Artist] The creator of the collection
 */
native fun getCreator() on collection

/**
 * Gets the number of tracks in the collection.
 *
 * @returns[@type int] The number of tracks
 */
native fun getTrackCount() on collection

/**
 * Returns a string value including all the songs in the collection appended together in a string.
 * For instance, this will return `collection("Lost in Echoes" by "Caskets", "Cremation Party" by "Ithaca")`
 *
 * @returns[@type string] The string representation of the collection
 */
native fun toVerboseString() on collection

/**
 * Loops through all songs of the collection and checks if the condition is matched.
 *
 * @param fn The function to check if the condition is matched. A single parameter `song` is passed into the function.
 * @returns[@type boolean] If the condition is matched
 */
native fun anySongMatches(fn) on collection

/**
 * Checks if the collection contains any song with the given artist as the song's first artist.
 *
 * @param artist The artist to check
 * @returns[@type boolean] If the collection contains the artist
 */
native fun containsArtist(artist) on collection

/**
 * Gets the next song as if it were being played.
 */
native fun nextSong() on collection

/**
 * Gets all songs from the sollection.
 *
 * @returns[@type list] A song list
 */
native fun getSongs() on collection

/**
 * Returns a new collection that is a union of this and the provided collection, containing every song in either.
 * The resulting collection is effectively a set: there will only be one of every song, and order is not preserved.
 *
 * @param col The collection to union with
 * @returns[@type collection] A new collection
 */
native fun union(col) on collection

/**
 * Returns a new collection that is an intersection between this and the provided collection, containing only the songs
 * in both.
 * The resulting collection is effectively a set: there will only be one of every song, and order is not preserved.
 *
 * @param col The collection to intersect with
 * @returns[@type collection] A new collection
 */
native fun intersect(col) on collection

/**
 * Returns a new collection that is the difference of this and the provided collection, containing only the songs in
 * this collection that are not in the provided one.
 * The resulting collection is effectively a set: there will only be one of every song, and order is not preserved.
 *
 * @param col The collection whose songs should be removed
 * @returns[@type collection] A new collection
 */
native fun difference(col) on collection

/**
 * Returns a new collection that is the symmetric difference of this and the provided collection, containing only the
 * songs that are in exactly one of the two collections.
 * The resulting collection is effectively a set: there will only be one of every song, and order is not preserved.
 *
 * @param col The collection to compare against
 * @returns[@type collection] A new collection
 */
native fun symmetricDifference(col) on collection

/**
 * Checks if every song in this collection is also in the provided collection.
 * A collection is always a subset of itself.
 *
 * @param col The collection to check against
 * @returns[@type boolean] If this collection is a subset of the provided collection
 */
native fun isSubset(col) on collection

/**
 * Checks if every song in the provided collection is also in this collection.
 * A collection is always a superset of itself.
 *
 * @param col The collection to check against
 * @returns[@type boolean] If this collection is a superset of the provided collection
 */
native fun isSuperset(col) on collection

/**
 * Checks if every song in this collection is also in the provided collection, and the provided collection contains at
 * least one song that this collection does not.
 *
 * @param col The collection to check against
 * @returns[@type boolean] If this collection is a proper subset of the provided collection
 */
native fun isProperSubset(col) on collection

/**
 * Checks if every song in the provided collection is also in this collection, and this collection contains at least one
 * song that the provided collection does not.
 *
 * @param col The collection to check against
 * @returns[@type boolean] If this collection is a proper superset of the provided collection
 */
native fun isProperSuperset(col) on collection

/**
 * Checks if this collection and the provided collection have no songs in common.
 * This is the opposite of `overlaps`.
 *
 * @param col The collection to check against
 * @returns[@type boolean] If the collections share no songs
 */
native fun isDisjoint(col) on collection

/**
 * Checks if this collection and the provided collection have at least one song in common.
 * This is the opposite of `isDisjoint`.
 *
 * @param col The collection to check against
 * @returns[@type boolean] If the collections share any songs
 */
native fun overlaps(col) on collection
