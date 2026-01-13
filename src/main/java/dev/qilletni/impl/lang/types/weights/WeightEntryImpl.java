package dev.qilletni.impl.lang.types.weights;

import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.lang.types.weights.WeightTrackType;
import dev.qilletni.api.lang.types.weights.WeightUnit;
import dev.qilletni.api.lang.types.SongType;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.orchestration.CollectionState;
import dev.qilletni.api.music.supplier.DynamicProvider;
import dev.qilletni.impl.music.orchestration.CollectionStateImpl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WeightEntryImpl implements WeightEntry {
    private double weightAmount;
    private WeightUnit weightUnit;
    private boolean canRepeatTrack;
    private boolean canRepeatWeight;

    private final WeightTrackType weightTrackType;
    SongType song;
    private ListType songList;
    private CollectionState collectionState;
    private WeightsType weights;
    private Playlist playlist;
    private final MusicPopulator musicPopulator;
    private final DynamicProvider dynamicProvider;

    /**
     * Constructor for a single song, initialized later
     */
    WeightEntryImpl(int weightAmount, WeightUnit weightUnit, MusicPopulator musicPopulator, DynamicProvider dynamicProvider, boolean canRepeatTrack, boolean canRepeatWeight) {
        this.weightAmount = weightAmount;
        this.weightUnit = weightUnit;
        this.musicPopulator = musicPopulator;
        this.dynamicProvider = dynamicProvider;
        this.canRepeatTrack = canRepeatTrack;
        this.canRepeatWeight = canRepeatWeight;
        this.weightTrackType = WeightTrackType.SINGLE_TRACK;
    }

    public WeightEntryImpl(int weightAmount, WeightUnit weightUnit, MusicPopulator musicPopulator, DynamicProvider dynamicProvider, SongType song, boolean canRepeatTrack, boolean canRepeatWeight) {
        this.weightAmount = weightAmount;
        this.weightUnit = weightUnit;
        this.musicPopulator = musicPopulator;
        this.dynamicProvider = dynamicProvider;
        this.canRepeatTrack = canRepeatTrack;
        this.canRepeatWeight = canRepeatWeight;
        
        this.song = song;
        this.weightTrackType = WeightTrackType.SINGLE_TRACK;
    }

    public WeightEntryImpl(int weightAmount, WeightUnit weightUnit, MusicPopulator musicPopulator, DynamicProvider dynamicProvider, ListType songList, boolean canRepeatTrack, boolean canRepeatWeight) {
        this.weightAmount = weightAmount;
        this.weightUnit = weightUnit;
        this.musicPopulator = musicPopulator;
        this.dynamicProvider = dynamicProvider;
        this.canRepeatTrack = canRepeatTrack;
        this.canRepeatWeight = canRepeatWeight;
        
        this.songList = songList;
        this.weightTrackType = WeightTrackType.LIST;
    }

    public WeightEntryImpl(int weightAmount, WeightUnit weightUnit, MusicPopulator musicPopulator, DynamicProvider dynamicProvider, CollectionType collection, boolean canRepeatTrack, boolean canRepeatWeight) {
        this.weightAmount = weightAmount;
        this.weightUnit = weightUnit;
        this.musicPopulator = musicPopulator;
        this.dynamicProvider = dynamicProvider;
        this.canRepeatTrack = canRepeatTrack;
        this.canRepeatWeight = canRepeatWeight;

        this.collectionState = new CollectionStateImpl(collection, dynamicProvider);
        this.weightTrackType = WeightTrackType.COLLECTION;
    }

    public WeightEntryImpl(int weightAmount, WeightUnit weightUnit, MusicPopulator musicPopulator, DynamicProvider dynamicProvider, WeightsType weights, boolean canRepeatTrack, boolean canRepeatWeight) {
        this.weightAmount = weightAmount;
        this.weightUnit = weightUnit;
        this.musicPopulator = musicPopulator;
        this.dynamicProvider = dynamicProvider;
        this.canRepeatTrack = canRepeatTrack;
        this.canRepeatWeight = canRepeatWeight;

        this.weights = weights;
        this.weightTrackType = WeightTrackType.WEIGHTS;
    }

    public WeightEntryImpl(int weightAmount, WeightUnit weightUnit, MusicPopulator musicPopulator, DynamicProvider dynamicProvider, Playlist playlist, boolean canRepeatTrack, boolean canRepeatWeight) {
        this.weightAmount = weightAmount;
        this.weightUnit = weightUnit;
        this.musicPopulator = musicPopulator;
        this.dynamicProvider = dynamicProvider;
        this.canRepeatTrack = canRepeatTrack;
        this.canRepeatWeight = canRepeatWeight;
        
        this.playlist = playlist;
        this.weightTrackType = WeightTrackType.PLAYLIST;
    }

    @Override
    public double getWeightAmount() {
        return weightAmount;
    }

    @Override
    public void setWeightAmount(double weightAmount) {
        this.weightAmount = weightAmount;
    }

    @Override
    public WeightUnit getWeightUnit() {
        return weightUnit;
    }

    @Override
    public void setWeightUnit(WeightUnit weightUnit) {
        this.weightUnit = weightUnit;
    }

    @Override
    public void setCanRepeat(boolean canRepeatTrack) {
        this.canRepeatTrack = canRepeatTrack;
    }

    @Override
    public boolean getCanRepeatTrack() {
        return canRepeatTrack;
    }

    @Override
    public void setCanRepeatWeight(boolean canRepeatWeight) {
        this.canRepeatWeight = canRepeatWeight;
    }

    @Override
    public boolean getCanRepeatWeight() {
        return canRepeatWeight;
    }

    @Override
    public WeightTrackType getTrackType() {
        return weightTrackType;
    }

    @Override
    public Track getTrack() {
        final var trackOrchestrator = dynamicProvider.getTrackOrchestrator();
        final var musicCache = dynamicProvider.getMusicCache();
        
        return switch (weightTrackType) {
            case SINGLE_TRACK -> {
                musicPopulator.populateSong(song);
                yield song.getTrack();
            }
            case LIST -> {
                var songs = songList.getItems();
                yield ((SongType) songs.get(ThreadLocalRandom.current().nextInt(0, songs.size()))).getTrack();
            }
            case COLLECTION -> trackOrchestrator.getTrackFromCollection(collectionState);
            case WEIGHTS -> trackOrchestrator.getTrackFromWeight(weights);
            case PLAYLIST -> {
                var tracks = musicCache.getPlaylistTracks(playlist);
                yield tracks.get(ThreadLocalRandom.current().nextInt(0, tracks.size()));
            }
            case FUNCTION -> throw new UnsupportedOperationException("Function weight entry should use LazyWeightEntry");
        };
    }

    @Override
    public List<Track> getAllTracks() {
        final var musicCache = dynamicProvider.getMusicCache();

        return switch (weightTrackType) {
            case SINGLE_TRACK -> {
                musicPopulator.populateSong(song);
                yield List.of(song.getTrack());
            }
            case LIST -> songList.getItems().stream().map(SongType.class::cast)
                    .peek(musicPopulator::populateSong)
                    .map(SongType::getTrack).toList();
            case COLLECTION -> musicCache.getPlaylistTracks(collectionState.getCollection().getPlaylist());
            case WEIGHTS -> weights.getWeightEntries().stream().flatMap(weightEntry -> weightEntry.getAllTracks().stream()).toList();
            case PLAYLIST -> musicCache.getPlaylistTracks(playlist);
            case FUNCTION -> Collections.emptyList();
        };
    }

    @Override
    public boolean isInconsistent() {
        return false;
    }

    @Override
    public String getTrackStringValue() {
        return switch (weightTrackType) {
            case SINGLE_TRACK -> song.stringValue();
            case LIST -> songList.stringValue();
            case COLLECTION -> collectionState.stringValue();
            case WEIGHTS -> weights.stringValue();
            case PLAYLIST -> String.format("[Raw playlist of ID %s]", playlist.getId());
            case FUNCTION -> "function-call";
        };
    }

    @Override
    public String toString() {
        return "WeightEntry[" + weightAmount + weightUnit.getStringUnit() + " " + getTrackStringValue() + "]";
    }
}
