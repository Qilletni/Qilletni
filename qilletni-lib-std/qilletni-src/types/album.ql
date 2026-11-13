/**
 * Gets the ID of an album.
 *
 * @returns[@type string] The ID of the album
 */
native fun getId() on album

/**
 * Gets the URL of an album.
 *
 * @returns[@type string] The URL of the album
 */
native fun getUrl() on album

/**
 * Gets the name of an album.
 *
 * @returns[@type string] The name of the album
 */
native fun getName() on album

/**
 * Gets the primary [@type std.Artist] of the album.
 *
 * @returns[@type std.Artist] The artist of the album
 */
native fun getArtist() on album

/**
 * Gets all [@type std.Artist]s of the album.
 *
 * @returns[@type list] A list of all artists of the album
 */
native fun getAllArtists() on album

/**
 * Returns a [@type collection] containin the songs in this album.
 * 
 * @returns[@type collection] The collection with the album's songs
 */
native fun toCollection() on album
