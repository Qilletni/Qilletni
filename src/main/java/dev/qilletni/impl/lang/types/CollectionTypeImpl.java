package dev.qilletni.impl.lang.types;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.collection.CollectionDefinition;
import dev.qilletni.api.lang.types.collection.CollectionOrder;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.SpotifyDataUtility;
import dev.qilletni.impl.lang.exceptions.UnsupportedOperatorException;
import dev.qilletni.impl.music.DummyPlaylist;

import java.util.List;

public final class CollectionTypeImpl implements CollectionType {
    private MusicPopulator musicPopulator;

    private CollectionDefinition collectionDefinition;
    private String url;
    private String name;
    private String creator;
    private CollectionOrder order = CollectionOrder.SHUFFLE;
    private WeightsType weights;
    private final DynamicMusicType<Playlist> dynamicPlaylist;
    private EntityType creatorType;
    
    public CollectionTypeImpl(DynamicProvider dynamicProvider, MusicPopulator musicPopulator, Playlist playlist) {
        this.musicPopulator = musicPopulator;
        this.collectionDefinition = CollectionDefinition.PREPOPULATED;
        this.dynamicPlaylist = new DynamicMusicType<>(Playlist.class, dynamicProvider, playlist);
    }

    public CollectionTypeImpl(DynamicProvider dynamicProvider, MusicPopulator musicPopulator, String url) {
        this.musicPopulator = musicPopulator;
        this.collectionDefinition = CollectionDefinition.URL;
        this.url = url;
        this.dynamicPlaylist = new DynamicMusicType<>(Playlist.class, dynamicProvider);
    }

    public CollectionTypeImpl(DynamicProvider dynamicProvider, MusicPopulator musicPopulator, String name, String creator) {
        this.musicPopulator = musicPopulator;
        this.collectionDefinition = CollectionDefinition.NAME_CREATOR;
        this.name = name;
        this.creator = creator;
        this.dynamicPlaylist = new DynamicMusicType<>(Playlist.class, dynamicProvider);
    }
    
    public CollectionTypeImpl(DynamicProvider dynamicProvider, MusicPopulator musicPopulator, List<Track> tracks) {
        this.musicPopulator = musicPopulator;
        this.collectionDefinition = CollectionDefinition.SONG_LIST;
        this.dynamicPlaylist = new DynamicMusicType<>(Playlist.class, dynamicProvider, new DummyPlaylist(tracks));
    }

    @Override
    public CollectionDefinition getCollectionDefinition() {
        return collectionDefinition;
    }

    @Override
    public void setCollectionDefinition(CollectionDefinition collectionDefinition) {
        this.collectionDefinition = collectionDefinition;
    }

    @Override
    public String getSuppliedUrl() {
        return url;
    }

    @Override
    public String getSuppliedName() {
        return name;
    }

    @Override
    public String getSuppliedCreator() {
        return creator;
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

    @Override
    public EntityType getCreator(EntityDefinitionManager entityDefinitionManager) {
        var playlist = dynamicPlaylist.get();
        SpotifyDataUtility.requireNonNull(playlist, "Internal Playlist is null, #populateSpotifyData must be invoked prior to getting API data");

        if (creatorType != null) {
            return creatorType;
        }

        var creator = playlist.getCreator();
        var artistEntity = entityDefinitionManager.lookup("User");
        return creatorType = artistEntity.createInstance(new StringTypeImpl(creator.getId()), new StringTypeImpl(creator.getName()));
    }

    @Override
    public Playlist getPlaylist() {
        var playlist = dynamicPlaylist.get();
        return SpotifyDataUtility.requireNonNull(playlist, "Internal Playlist is null, #populateSpotifyData must be invoked prior to getting API data");
    }

    @Override
    public boolean isSpotifyDataPopulated() {
        return dynamicPlaylist.isPopulated();
    }

    @Override
    public void populateSpotifyData(Playlist playlist) {
        dynamicPlaylist.put(playlist);
    }

    @Override
    public String stringValue() {
        if (collectionDefinition == CollectionDefinition.URL) {
            return String.format("collection(%s)", url);
        }

        if (collectionDefinition == CollectionDefinition.SONG_LIST) {
            return "collection(list-expression)";
        }

        return String.format("collection(\"%s\" by \"%s\")", name, creator);
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
    public QilletniTypeClass<CollectionType> getTypeClass() {
        return QilletniTypeClass.COLLECTION;
    }

    @Override
    public String toString() {
        if (collectionDefinition == CollectionDefinition.URL) {
            return "CollectionType{url='%s'}".formatted(url);
        }
        
        if (collectionDefinition == CollectionDefinition.SONG_LIST) {
            return "CollectionType{list-expression}";
        }

        return "CollectionType{title='%s', artist='%s'}".formatted(name, creator);
    }
}
