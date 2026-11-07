package dev.qilletni.impl.lang.types;

import dev.qilletni.impl.SpotifyDataUtility;
import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.SongType;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.song.SongDefinition;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.lang.exceptions.UnsupportedOperatorException;

import java.util.Optional;

public final class SongTypeImpl implements SongType {

    private final DynamicProvider dynamicProvider;
    private SongDefinition songDefinition;
    private String url;
    private String title;
    private String artist;

    private EntityType artistType;
    private ListType artistsType;
    private AlbumType albumType;
    private final DynamicMusicType<Track> dynamicTrack;
    
    public SongTypeImpl(DynamicProvider dynamicProvider, Track track) {
        this.dynamicProvider = dynamicProvider;
        this.songDefinition = SongDefinition.PREPOPULATED;
        this.dynamicTrack = new DynamicMusicType<>(Track.class, dynamicProvider, track);
    }
    
    public SongTypeImpl(DynamicProvider dynamicProvider, String url) {
        this.dynamicProvider = dynamicProvider;
        this.songDefinition = SongDefinition.URL;
        this.url = url;
        this.dynamicTrack = new DynamicMusicType<>(Track.class, dynamicProvider);
    }
    
    public SongTypeImpl(DynamicProvider dynamicProvider, String title, String artist) {
        this.dynamicProvider = dynamicProvider;
        this.songDefinition = SongDefinition.TITLE_ARTIST;
        this.title = title;
        this.artist = artist;
        this.dynamicTrack = new DynamicMusicType<>(Track.class, dynamicProvider);
    }

    @Override
    public SongDefinition getSongDefinition() {
        return songDefinition;
    }

    @Override
    public void setSongDefinition(SongDefinition songDefinition) {
        this.songDefinition = songDefinition;
    }

    @Override
    public String getSuppliedUrl() {
        return url;
    }

    @Override
    public String getSuppliedTitle() {
        return title;
    }

    @Override
    public String getSuppliedArtist() {
        return artist;
    }

    @Override
    public AlbumType getAlbum() {
        var track = dynamicTrack.get();
        SpotifyDataUtility.requireNonNull(track, "Internal Track is null, #populateSpotifyData must be invoked for the current provider (%s) prior to getting API data".formatted(dynamicProvider.getCurrentProvider().getName()));

        if (albumType != null) {
            return albumType;
        }
        
        return albumType = new AlbumTypeImpl(dynamicProvider, track.getAlbum());
    }
    
    @Override
    public EntityType getArtist(EntityDefinitionManager entityDefinitionManager) {
        var track = dynamicTrack.get();
        SpotifyDataUtility.requireNonNull(track, "Internal Track is null, #populateSpotifyData must be invoked for the current provider (%s) prior to getting API data".formatted(dynamicProvider.getCurrentProvider().getName()));
        
        if (artistType != null) {
            return artistType;
        }

        var trackArtist = track.getArtist();
        var artistEntity = entityDefinitionManager.lookup("Artist");
        return artistType = artistEntity.createInstance(new StringTypeImpl(trackArtist.getId()), new StringTypeImpl(trackArtist.getName()));
    }
    
    @Override
    public ListType getArtists(EntityDefinitionManager entityDefinitionManager) {
        var track = dynamicTrack.get();
        SpotifyDataUtility.requireNonNull(track, "Internal Track is null, #populateSpotifyData must be invoked for the current provider (%s) prior to getting API data".formatted(dynamicProvider.getCurrentProvider().getName()));

        if (artistsType != null) {
            return artistsType;
        }
        
        var trackArtists = track.getArtists();
        var artistEntity = entityDefinitionManager.lookup("Artist");
        var artistEntities = trackArtists.stream()
                .map(artist -> (QilletniType) artistEntity.createInstance(new StringTypeImpl(artist.getId()), new StringTypeImpl(artist.getName())))
                .toList();

        return artistsType = new ListTypeImpl(QilletniTypeClass.createListOfType(artistEntity.getQilletniTypeClass()), artistEntities);
    }

    @Override
    public Track getTrack() {
        var track = dynamicTrack.get();
        return SpotifyDataUtility.requireNonNull(track, "Internal Track is null, #populateSpotifyData must be invoked for the current provider (%s) prior to getting API data".formatted(dynamicProvider.getCurrentProvider().getName()));
    }
    
    /**
     * @return The track for the current service provider, if already loaded. Never fetches
     */
    public Optional<Track> peekTrack() {
        return dynamicTrack.peek();
    }

    @Override
    public boolean isSpotifyDataPopulated() {
        return dynamicTrack.isPopulated();
    }

    @Override
    public void populateSpotifyData(Track track) {
        dynamicTrack.put(track);
    }

    @Override
    public String stringValue() {
        if (!isSpotifyDataPopulated()) {
            if (songDefinition == SongDefinition.URL) {
                return String.format("song(%s)", url);
            }
            
            if (songDefinition == SongDefinition.TITLE_ARTIST) {
                return String.format("song(\"%s\" by \"%s\")", title, artist);
            }
        }

        var track = dynamicTrack.get();

        return String.format("song(\"%s\" by \"%s\")", track.getName(), track.getArtist().getName());
    }

    @Override
    public QilletniType plusOperator(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    @Override
    public void plusOperatorInPlace(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    @Override
    public QilletniType minusOperator(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "-");
    }

    @Override
    public void minusOperatorInPlace(QilletniType qilletniType) {
        throw new UnsupportedOperatorException(this, qilletniType, "-");
    }

    @Override
    public QilletniTypeClass<SongType> getTypeClass() {
        return QilletniTypeClass.SONG;
    }

    @Override
    public String toString() {
        var track = dynamicTrack.get();

        if (songDefinition == SongDefinition.URL) {
            return "SongType{url='%s'}".formatted(url);
        }

        if (songDefinition == SongDefinition.TITLE_ARTIST) {
            return "SongType{title='%s', artist='%s'}".formatted(title, artist);
        }
        
        return "SongType{title='%s', artist='%s'}".formatted(track.getName(), track.getArtist().getName());
    }
}
