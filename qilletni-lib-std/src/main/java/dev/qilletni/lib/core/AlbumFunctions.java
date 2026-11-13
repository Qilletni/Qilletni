package dev.qilletni.lib.core;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.EntityType;
import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lib.annotations.BeforeAnyInvocation;
import dev.qilletni.api.lib.annotations.NativeOn;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.factories.CollectionTypeFactory;
import dev.qilletni.api.music.supplier.DynamicProvider;

import java.util.Objects;

@NativeOn("album")
public class AlbumFunctions {
    
    private final MusicPopulator musicPopulator;
    private final EntityDefinitionManager entityDefinitionManager;
    private final CollectionTypeFactory collectionTypeFactory;
    private final DynamicProvider dynamicProvider;

    public AlbumFunctions(MusicPopulator musicPopulator, EntityDefinitionManager entityDefinitionManager, CollectionTypeFactory collectionTypeFactory, DynamicProvider dynamicProvider) {
        this.musicPopulator = musicPopulator;
        this.entityDefinitionManager = entityDefinitionManager;
        this.collectionTypeFactory = collectionTypeFactory;
        this.dynamicProvider = dynamicProvider;
    }

    @BeforeAnyInvocation
    public void setupAlbum(AlbumType albumType) {
        musicPopulator.populateAlbum(albumType);
    }

    public static String getId(AlbumType albumType) {
        return albumType.getAlbum().getId();
    }
    
    public static String getUrl(AlbumType albumType) {
        return Objects.requireNonNullElse(albumType.getSuppliedUrl(), ""); 
    }
    
    public static String getName(AlbumType albumType) {
        return albumType.getAlbum().getName();
    }

    public EntityType getArtist(AlbumType albumType) {
        return albumType.getArtist(entityDefinitionManager);
    }

    public ListType getAllArtists(AlbumType albumType) {
        return albumType.getArtists(entityDefinitionManager);
    }

    public CollectionType toCollection(AlbumType albumType) {
        var musicCache = dynamicProvider.getMusicCache();
        return collectionTypeFactory.createCollectionFromTracks(musicCache.getAlbumTracks(albumType.getAlbum()));
    }
}
