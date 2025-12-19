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
