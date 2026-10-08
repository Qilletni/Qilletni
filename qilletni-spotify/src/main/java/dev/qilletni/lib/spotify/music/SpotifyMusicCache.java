package dev.qilletni.lib.spotify.music;

import dev.qilletni.api.CollectionUtility;
import dev.qilletni.api.exceptions.IncompatibleProviderTypeException;
import dev.qilletni.api.exceptions.InvalidURLOrIDException;
import dev.qilletni.lib.spotify.database.EntityTransaction;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.Artist;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicFetcher.TrackNameArtist;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.lib.spotify.music.entities.SpotifyAlbum;
import dev.qilletni.lib.spotify.music.entities.SpotifyArtist;
import dev.qilletni.lib.spotify.music.entities.SpotifyPlaylist;
import dev.qilletni.lib.spotify.music.entities.SpotifyPlaylistIndex;
import dev.qilletni.lib.spotify.music.entities.SpotifyTrack;
import dev.qilletni.lib.spotify.music.entities.SpotifyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpotifyMusicCache implements MusicCache {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SpotifyMusicCache.class);

    private final SpotifyMusicFetcher spotifyMusicFetcher;

    public SpotifyMusicCache(SpotifyMusicFetcher spotifyMusicFetcher) {
        this.spotifyMusicFetcher = spotifyMusicFetcher;
    }

    @Override
    public Optional<Track> getTrack(String name, String artist) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(SpotifyTrack.class);
            var root = criteria.from(SpotifyTrack.class);

            Join<SpotifyTrack, SpotifyArtist> artistsJoin = root.join("artists");

            var trackNamePredicate = builder.equal(root.get("name"), name);
            var artistPredicate = builder.equal(artistsJoin.get("name"), artist);

            criteria.where(trackNamePredicate, artistPredicate);

            var tracks = session.createQuery(criteria).getResultList();

            if (!tracks.isEmpty()) {
                return Optional.of(tracks.get(0));
            }
        }

        return spotifyMusicFetcher.fetchTrack(name, artist)
                .map(SpotifyTrack.class::cast)
                .map(this::storeTrack);
    }

    @Override
    public Optional<Track> getTrackById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var trackOptional = Optional.<Track>ofNullable(session.find(SpotifyTrack.class, id));
            if (trackOptional.isPresent()) {
                return trackOptional;
            }
        }

        return spotifyMusicFetcher.fetchTrackById(id)
                .map(SpotifyTrack.class::cast)
                .map(this::storeTrack);
    }

    @Override
    public List<Track> getTracks(List<TrackNameArtist> tracks) { // TODO: Not testing yet
        // The track to look up with its destination index in the list
        var lookupTracks = new HashMap<Integer, TrackNameArtist>();
        var foundTracks = new ArrayList<Track>(CollectionUtility.createList(tracks.size(), null));

        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();


            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(SpotifyTrack.class);
            var root = criteria.from(SpotifyTrack.class);

            Join<SpotifyTrack, SpotifyArtist> artistsJoin = root.join("artists");

            var predicateMap = new HashMap<Predicate, TrackNameArtist>();

            for (var trackNameArtist : tracks) {
                var trackNamePredicate = builder.equal(root.get("name"), trackNameArtist.name());
                var artistPredicate = builder.equal(artistsJoin.get("name"), trackNameArtist.artist());
                var combinedPredicate = builder.and(trackNamePredicate, artistPredicate);
                predicateMap.put(combinedPredicate, trackNameArtist);
            }

            criteria.where(builder.or(predicateMap.keySet().toArray(Predicate[]::new)));

            var result = session.createQuery(criteria).getResultList();
            for (var spotifyTrack : result) {
                var trackNameArtists = predicateMap.values().toArray(TrackNameArtist[]::new);
                for (int i = 0; i < trackNameArtists.length; i++) {
                    var trackNameArtist = trackNameArtists[i];
                    if (trackNameArtist.matchesTrack(spotifyTrack)) {
                        foundTracks.set(i, spotifyTrack);
                    } else {
                        lookupTracks.put(i, trackNameArtist);
                    }
                }
            }
        }

        LOGGER.debug("foundTracks = {}", foundTracks);
        LOGGER.debug("lookupTracks = {}", lookupTracks);

        var fetched = storeTracks(spotifyMusicFetcher.fetchTracks(lookupTracks.values().stream().toList())).fetchedTracks();
        for (var entry : CollectionUtility.getRecordEntries(lookupTracks)) {
            var index = entry.k();
            var trackNameArtist = entry.v();
            fetched.stream().filter(trackNameArtist::matchesTrack)
                    .findFirst()
                    .ifPresent(track -> foundTracks.set(index, track));
        }
        
        LOGGER.debug("lookupTracks = {}", lookupTracks);
        
        foundTracks.removeIf(Objects::isNull);

        LOGGER.debug("lookupTracks = {}", lookupTracks);

        return foundTracks;
    }

    @Override
    public List<Track> getTracksById(List<String> trackIds) {
        // The track to look up with its destination index in the list
        var lookupTracks = new HashMap<Integer, String>();
        var foundTracks = new ArrayList<Track>(CollectionUtility.createList(trackIds.size(), null));

        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(SpotifyTrack.class);
            var root = criteria.from(SpotifyTrack.class);

            var predicateMap = new HashMap<Predicate, String>();

            for (var id : trackIds) {
                predicateMap.put(builder.equal(root.get("id"), id), id);
            }

            criteria.where(builder.or(predicateMap.keySet().toArray(Predicate[]::new)));

            var result = session.createQuery(criteria).getResultList();
            for (var spotifyTrack : result) {
                var ids = predicateMap.values().toArray(String[]::new);
                for (int i = 0; i < ids.length; i++) {
                    var id = ids[i];
                    if (spotifyTrack.getId().equals(id)) {
                        foundTracks.set(i, spotifyTrack);
                    } else {
                        lookupTracks.put(i, id);
                    }
                }
            }
        }

        LOGGER.debug("foundTracks = {}", foundTracks);
        LOGGER.debug("lookupTracks = {}", lookupTracks);

        var fetched = storeTracks(spotifyMusicFetcher.fetchTracksById(lookupTracks.values().stream().toList())).fetchedTracks();
        for (var entry : CollectionUtility.getRecordEntries(lookupTracks)) {
            var index = entry.k();
            var id = entry.v();
            fetched.stream().filter(track -> track.getId().equals(id))
                    .findFirst()
                    .ifPresent(track -> foundTracks.set(index, track));
        }

        LOGGER.debug("lookupTracks = {}", lookupTracks);

        foundTracks.removeIf(Objects::isNull);

        LOGGER.debug("lookupTracks = {}", lookupTracks);
        
        return foundTracks;
    }

    @Override
    public Optional<Playlist> getPlaylist(String name, String creator) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(SpotifyPlaylist.class);
            var root = criteria.from(SpotifyPlaylist.class);

            Join<SpotifyPlaylist, SpotifyUser> departmentJoin = root.join("creator");

            // Spotify lookups are case-insensitive, and the creator may be given as either the user's ID or name
            var playlistNamePredicate = builder.equal(builder.lower(root.get("title")), name.toLowerCase());
            var creatorPredicate = builder.or(
                    builder.equal(builder.lower(departmentJoin.get("name")), creator.toLowerCase()),
                    builder.equal(builder.lower(departmentJoin.get("id")), creator.toLowerCase()));

            criteria.where(playlistNamePredicate, creatorPredicate);

            var playlists = session.createQuery(criteria).getResultList();

            if (!playlists.isEmpty()) {
                return Optional.of(playlists.get(0));
            }
        }

        return spotifyMusicFetcher.fetchPlaylist(name, creator)
                .map(SpotifyPlaylist.class::cast)
                .map(this::storePlaylist);
    }

    @Override
    public Optional<Playlist> getPlaylistById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var playlistOptional = Optional.<Playlist>ofNullable(session.find(SpotifyPlaylist.class, id));
            if (playlistOptional.isPresent()) {
                return playlistOptional;
            }
        }

        return spotifyMusicFetcher.fetchPlaylistById(id)
                .map(SpotifyPlaylist.class::cast)
                .map(this::storePlaylist);
    }

    @Override
    public Optional<Album> getAlbum(String name, String artist) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(SpotifyAlbum.class);
            var root = criteria.from(SpotifyAlbum.class);

            Join<SpotifyAlbum, SpotifyArtist> departmentJoin = root.join("artists");

            // Spotify searches are case-insensitive, so the cache lookup should be as well
            var albumNamePredicate = builder.equal(builder.lower(root.get("name")), name.toLowerCase());
            var artistPredicate = builder.equal(builder.lower(departmentJoin.get("name")), artist.toLowerCase());

            criteria.where(albumNamePredicate, artistPredicate);

            var albums = session.createQuery(criteria).getResultList();

            if (!albums.isEmpty()) {
                return Optional.of(albums.get(0));
            }
        }

        return spotifyMusicFetcher.fetchAlbum(name, artist)
                .map(SpotifyAlbum.class::cast)
                .map(this::storeAlbum);
    }

    @Override
    public Optional<Album> getAlbumById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var albumOptional = Optional.<Album>ofNullable(session.find(SpotifyAlbum.class, id));
            if (albumOptional.isPresent()) {
                return albumOptional;
            }
        }
        
        return spotifyMusicFetcher.fetchAlbumById(id)
                .map(SpotifyAlbum.class::cast)
                .map(this::storeAlbum);
    }

    @Override
    public List<Track> getAlbumTracks(Album album) {
        var spotifyAlbum = (SpotifyAlbum) album;
        var albumTracks = spotifyAlbum.getTracks();

        if (albumTracks == null || albumTracks.isEmpty()) {
            var tracks = spotifyMusicFetcher.fetchAlbumTracks(album);
            var allTracks = storeTracks(tracks).allTracks();
            spotifyAlbum.setTracks(allTracks.stream().map(SpotifyTrack.class::cast).toList());

            try (var entityTransaction = EntityTransaction.beginTransaction()) {
                var session = entityTransaction.getSession();
                session.update(spotifyAlbum);
            }
            
            return allTracks;
        }

        return albumTracks.stream().map(Track.class::cast).toList();
    }

    @Override
    public List<Track> getPlaylistTracks(Playlist playlist) {
        var initialTracks = playlist.getTracks();
        if (initialTracks.isPresent()) {
            return initialTracks.get();
        }

        var spotifyPlaylist = IncompatibleProviderTypeException.ensureType(playlist, SpotifyPlaylist.class);
        var playlistIndex = spotifyPlaylist.getSpotifyPlaylistIndex();
        
        var expires = Instant.ofEpochMilli(playlistIndex.getLastUpdatedIndex().getTime()).plus(7, ChronoUnit.DAYS); // when it expires
        LOGGER.debug("Should update: {} .isAfter {}", Instant.now(), expires);
        if (Instant.now().isAfter(expires)) {
            var tracks = spotifyMusicFetcher.fetchPlaylistTracks(playlist);
            
            var playlistTracks = storeTracks(tracks).allTracks();

            spotifyPlaylist.setSpotifyPlaylistIndex(new SpotifyPlaylistIndex(playlistTracks.stream()
                    .map(SpotifyTrack.class::cast)
                    .toList(), new Date(System.currentTimeMillis())));

            try (var entityTransaction = EntityTransaction.beginTransaction()) {
                var session = entityTransaction.getSession();
                session.update(spotifyPlaylist);
            }
            
            return playlistTracks;
        }

        return playlistIndex.getTracks().stream().map(Track.class::cast).toList();
    }

    @Override
    public Optional<Artist> getArtistById(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var albumOptional = Optional.<Artist>ofNullable(session.find(SpotifyArtist.class, id));
            if (albumOptional.isPresent()) {
                return albumOptional;
            }
        }

        return spotifyMusicFetcher.fetchArtistById(id)
                .map(SpotifyArtist.class::cast)
                .map(this::storeArtist);
    }

    @Override
    public Optional<Artist> getArtistByName(String name) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var builder = session.getCriteriaBuilder();
            var criteria = builder.createQuery(SpotifyArtist.class);
            var root = criteria.from(SpotifyArtist.class);

            criteria.where(builder.equal(root.get("name"), name));

            var artists = session.createQuery(criteria).getResultList();

            if (!artists.isEmpty()) {
                return Optional.of(artists.get(0));
            }
        }

        return spotifyMusicFetcher.fetchArtistByName(name)
                .map(SpotifyArtist.class::cast)
                .map(this::storeArtist);
    }

    /**
     * Searches for the given ID, and returns an Optional containing either a {@link Track}, {@link Playlist}, or a
     * {@link Album}.
     *
     * @return The found object, if any
     */
    public Optional<Object> searchAnyId(String id) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var optional = Optional.<Object>ofNullable(session.find(SpotifyTrack.class, id))
                    .or(() -> Optional.<Object>ofNullable(session.find(SpotifyPlaylist.class, id)))
                    .or(() -> Optional.<Object>ofNullable(session.find(SpotifyAlbum.class, id)));
            
            if (optional.isPresent()) {
                return optional;
            }
        }
        
        var foundOptional = spotifyMusicFetcher.searchAnyId(id);
        foundOptional.ifPresent(found -> {
            switch (found) {
                case SpotifyTrack track -> storeTrack(track);
                case SpotifyPlaylist playlist -> storePlaylist(playlist);
                case SpotifyAlbum album -> storeAlbum(album);
                default -> {}
            }
        });
        
        return foundOptional;
    }

    @Override
    public String getIdFromString(String idOrUrl) {
        // Regular expression to match Spotify track URLs or an ID
        var pattern = Pattern.compile("spotify\\.com/track/(\\w{22})|^(\\w{22})$");
        var matcher = pattern.matcher(idOrUrl);

        if (matcher.find()) {
            // Check which group has a match
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i);
                }
            }
        }

        throw new InvalidURLOrIDException(String.format("Invalid URL or ID: \"%s\"", idOrUrl));
    }

    /**
     * Stores a single artist into the database.
     *
     * @param artist The artist to store in the database
     */
    private SpotifyArtist storeArtist(SpotifyArtist artist) {
        return storeArtists(List.of(artist)).values().toArray(SpotifyArtist[]::new)[0];
    }

    /**
     * Attempts to put all given artists in the database. The list is assumed to be distinct. If an artist is found in
     * the database, its ID and stored instance is added to the map. A distinct artist will be added to the map no
     * matter what.
     *
     * @param artists The artists to try and put in the database
     * @return A map of all IDs and {@link SpotifyArtist} instances, regardless of if they were added to the database
     */
    private Map<String, SpotifyArtist> storeArtists(List<SpotifyArtist> artists) {
        var allArtists = new HashMap<String, SpotifyArtist>();
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            artists.forEach(artist -> {
                if (allArtists.containsKey(artist.getId())) {
                    return;
                }
                
                var foundArtist = session.find(SpotifyArtist.class, artist.getId());
                if (foundArtist != null) {
                    LOGGER.debug("Already found artist: {}", foundArtist);
                    allArtists.put(foundArtist.getId(), foundArtist);
                    return;
                }

                LOGGER.debug("Saving artist: {}", artist);
                session.save(artist);
                allArtists.put(artist.getId(), artist);
            });
        }

        return allArtists;
    }

    /**
     * Stores a playlist to the database, replacing the creator with a reference to the one in the database, if found.
     * If not, the user is also saved.
     *
     * @param playlist The playlist to store in the database
     * @return The new {@link SpotifyPlaylist}
     */
    private SpotifyPlaylist storePlaylist(SpotifyPlaylist playlist) {
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var foundPlaylist = session.find(SpotifyPlaylist.class, playlist.getId());
            if (foundPlaylist != null) {
                LOGGER.debug("Already found playlist: {}", foundPlaylist);
                return foundPlaylist;
            }

            var user = (SpotifyUser) playlist.getCreator();
            var databaseUser = session.find(SpotifyUser.class, user.getId());
            if (databaseUser == null) {
                session.save(databaseUser = user);
            }

            var newPlaylist = new SpotifyPlaylist(playlist.getId(), playlist.getTitle(), databaseUser, playlist.getTrackCount());
            LOGGER.debug("new playlist = {}", newPlaylist);
            session.save(newPlaylist);
            return newPlaylist;
        }
    }

    /**
     * Stores an individual spotify album in the database, returning the new object to use, with updated artist
     * instances.
     *
     * @param album The album to store in the database
     * @return The new {@link SpotifyAlbum} instance
     */
    private SpotifyAlbum storeAlbum(SpotifyAlbum album) {
        var artistMap = storeArtists(album.getArtists().stream().distinct().map(SpotifyArtist.class::cast).toList());
        return storeAlbums(List.of(album), artistMap).values().toArray(SpotifyAlbum[]::new)[0];
    }

    /**
     * Stores a track into the database, returning the new reference to the track, now in the database.
     *
     * @param track The track to store in the database
     * @return The new track instance
     */
    private Track storeTrack(SpotifyTrack track) {
        return storeTracks(List.of(track)).allTracks().get(0);
    }

    /**
     * Attempts to put all given albums in the database. The list is assumed to be distinct. If an album is found in
     * the database, its ID and stored instance is added to the map. A distinct album will be added to the map no
     * matter what. The artist map should be artists already in the database, and is used to reconstruct any
     * {@link SpotifyAlbum} object to be stored with authors that are known to be in the database.
     *
     * @param albums    The albums to try and put in the database
     * @param artistMap The artists in the database already
     * @return A map of all IDs and {@link SpotifyAlbum} instances, regardless of if they were added to the database
     */
    private Map<String, SpotifyAlbum> storeAlbums(List<SpotifyAlbum> albums, Map<String, SpotifyArtist> artistMap) {
        var allAlbums = new HashMap<String, SpotifyAlbum>();
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            albums.stream().map(album -> {
                var foundAlbum = session.find(SpotifyAlbum.class, album.getId());
                if (foundAlbum != null) {
                    return foundAlbum;
                }

                // Replacing the artist list to ones that are known to be in the database
                var newArtists = album.getArtists().stream().map(Artist::getId).map(artistMap::get).toList();
                var savingAlbum = new SpotifyAlbum(album.getId(), album.getName(), newArtists);

                session.save(savingAlbum);
                return savingAlbum;
            }).forEach(album -> allAlbums.put(album.getId(), album));
        }

        return allAlbums;
    }

    /**
     * Adds all given tracks to the database (if they are not present already), using cached albums and artists for
     * everything already in the database.
     *
     * @param addingTracks The tracks to add to the database
     * @return A record containing saved tracks and all tracks (with new objects), in the same order as addedTracks
     *         (excluding already cached ones)
     */
    private StoredTracks storeTracks(List<Track> addingTracks) {
        LOGGER.debug("All tracks to add: {}", addingTracks);
        
        try (var entityTransaction = EntityTransaction.beginTransaction()) {
            var session = entityTransaction.getSession();

            var preTrackCount = addingTracks.size();
            // Remove tracks already in the database. Do this now so albums/artists are skipped for existing ones
            var newTracks = addingTracks.stream().filter(track -> session.find(SpotifyTrack.class, track.getId()) == null).toList();

            LOGGER.debug("Removed {} tracks that are already in the database. Adding {} tracks", preTrackCount - newTracks.size(), newTracks.size());
            
            LOGGER.debug("Tracks to add: {}", getNameList(newTracks));

            var distinctArtists = addingTracks.stream()
                    .flatMap(track -> Stream.concat(track.getArtists().stream(), track.getAlbum().getArtists().stream()))
                    .map(SpotifyArtist.class::cast)
                    .distinct().toList();

            // The instances of artists that should be saved with/are known to be in the database
            var artistMap = storeArtists(distinctArtists);
            
            LOGGER.debug("artist map = {}", artistMap);

            var distinctAlbums = addingTracks.stream()
                    .map(Track::getAlbum)
                    .map(SpotifyAlbum.class::cast)
                    .distinct().toList();

            // The instances of albums that should be saved with/are known to be in the database
            var albumMap = storeAlbums(distinctAlbums, artistMap);
            
            LOGGER.debug("album map = {}", albumMap);

            var fetchedTracks = new ArrayList<Track>();
            
            var allTracks = addingTracks.stream().map(track -> {
                var newTrackFound = newTracks.stream().filter(newTrack -> newTrack.getId().equals(track.getId())).findFirst();
                if (newTrackFound.isPresent()) {
                    var storingTrack = newTrackFound.get();
                    
                    var storedArtists = storingTrack.getArtists().stream().map(Artist::getId).map(artistMap::get).toList();
                    LOGGER.debug("stored artists = {}", storedArtists);
                    
                    var storedAlbum = albumMap.get(storingTrack.getAlbum().getId());

                    var newTrack = new SpotifyTrack(storingTrack.getId(), storingTrack.getName(), storedArtists, storedAlbum, storingTrack.getDuration());
                    session.save(newTrack);
                    fetchedTracks.add(newTrack);

                    return newTrack;
                } else {
                    return (Track) session.find(SpotifyTrack.class, track.getId());
                }
            }).toList();
            
            LOGGER.debug("Tracks fetched: {}", getNameList(fetchedTracks));
            LOGGER.debug("New all tracks: {}", getNameList(allTracks));
            
            return new StoredTracks(fetchedTracks, allTracks);
        }
    }
    
    private static String getNameList(List<Track> tracks) {
        return tracks.stream().map(Track::getName).collect(Collectors.joining(", "));
    }

    /**
     * Tracks stored from {@link #storeTracks(List)}.
     * 
     * @param fetchedTracks New tracks fetched and added to the cache
     * @param allTracks All tracks, just with any necessary new objects
     */
    private record StoredTracks(List<Track> fetchedTracks, List<Track> allTracks) {}
}
