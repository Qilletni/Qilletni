package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.album.AlbumDefinition;
import dev.qilletni.api.lang.types.collection.CollectionOrder;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.SpotifyDataUtility;
import dev.qilletni.impl.lang.exceptions.UnsupportedOperatorException;

public final class AlbumTypeImpl implements AlbumType {

    private AlbumDefinition albumDefinition;
    private String url;
    private String title;
    private String artist;
    private CollectionOrder order = CollectionOrder.SHUFFLE;
    private WeightsType weights;
    
    private EntityType artistType;
    private ListType artistsType;
    private final DynamicMusicType<Album> dynamicAlbum;

    public AlbumTypeImpl(DynamicProvider dynamicProvider, String url) {
        this.albumDefinition = AlbumDefinition.URL;
        this.url = url;
        this.dynamicAlbum = new DynamicMusicType<>(Album.class, dynamicProvider);
    }

    public AlbumTypeImpl(DynamicProvider dynamicProvider, String title, String artist) {
        this.albumDefinition = AlbumDefinition.TITLE_ARTIST;
        this.title = title;
        this.artist = artist;
        this.dynamicAlbum = new DynamicMusicType<>(Album.class, dynamicProvider);
    }
    
    public AlbumTypeImpl(DynamicProvider dynamicProvider, Album album) {
        this.albumDefinition = AlbumDefinition.PREPOPULATED;
        this.dynamicAlbum = new DynamicMusicType<>(Album.class, dynamicProvider, album);
    }

    @Override
    public AlbumDefinition getAlbumDefinition() {
        return albumDefinition;
    }

    @Override
    public void setAlbumDefinition(AlbumDefinition albumDefinition) {
        this.albumDefinition = albumDefinition;
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
    public EntityType getArtist(EntityDefinitionManager entityDefinitionManager) {
        var album = dynamicAlbum.get();
        SpotifyDataUtility.requireNonNull(album, "Internal Album is null, #populateSpotifyData must be invoked prior to getting API data");

        if (artistType != null) {
            return artistType;
        }

        var trackArtist = album.getArtist();
        var artistEntity = entityDefinitionManager.lookup("Artist");
        return artistType = artistEntity.createInstance(new StringTypeImpl(trackArtist.getId()), new StringTypeImpl(trackArtist.getName()));
    }

    @Override
    public ListType getArtists(EntityDefinitionManager entityDefinitionManager) {
        var album = dynamicAlbum.get();
        SpotifyDataUtility.requireNonNull(album, "Internal Album is null, #populateSpotifyData must be invoked prior to getting API data");

        if (artistsType != null) {
            return artistsType;
        }

        var trackArtists = album.getArtists();
        var artistEntity = entityDefinitionManager.lookup("Artist");
        var artistEntities = trackArtists.stream()
                .map(artist -> (QilletniType) artistEntity.createInstance(new StringTypeImpl(artist.getId()), new StringTypeImpl(artist.getName())))
                .toList();

        return artistsType = new ListTypeImpl(QilletniTypeClass.createListOfType(artistEntity.getQilletniTypeClass()), artistEntities);
    }

    @Override
    public Album getAlbum() {
        var album = dynamicAlbum.get();
        return SpotifyDataUtility.requireNonNull(album, "Internal Album is null, #populateSpotifyData must be invoked prior to getting API data");
    }

    @Override
    public boolean isSpotifyDataPopulated() {
        return dynamicAlbum.isPopulated();
    }

    @Override
    public void populateSpotifyData(Album album) {
        dynamicAlbum.put(album);
    }

    @Override
    public String stringValue() {
        var album = dynamicAlbum.get();
        
        if (!isSpotifyDataPopulated()) {
            if (albumDefinition == AlbumDefinition.URL) {
                return String.format("album(%s)", url);
            }

            if (albumDefinition == AlbumDefinition.TITLE_ARTIST) {
                return String.format("album(\"%s\" by \"%s\")", title, artist);
            }
        }

        return String.format("album(\"%s\" by \"%s\")", album.getName(), album.getArtist().getName());
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
        throw new UnsupportedOperatorException(this, qilletniType, "+");
    }

    @Override
    public QilletniTypeClass<?> getTypeClass() {
        return QilletniTypeClass.ALBUM;
    }

    @Override
    public String toString() {
        var album = dynamicAlbum.get();

        if (albumDefinition == AlbumDefinition.URL) {
            return "AlbumType{url='%s'}".formatted(url);
        }

        if (albumDefinition == AlbumDefinition.TITLE_ARTIST) {
            return "AlbumType{title='%s', artist='%s'}".formatted(title, artist);
        }

        return "AlbumType{title='%s', artist='%s'}".formatted(album.getName(), album.getArtist().getName());
    }

    @Override
    public CollectionOrder getOrder() {
        return order;
    }

    @Override
    public void setOrder(CollectionOrder order) {
        this.order = order;
    }

    @Override
    public WeightsType getWeights() {
        return weights;
    }

    @Override
    public void setWeights(WeightsType weights) {
        this.weights = weights;
    }
}
