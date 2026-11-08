package dev.qilletni.impl.lang.types.weights;

import dev.qilletni.api.lang.types.weights.WeightUnit;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.supplier.DynamicProvider;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A weight entry whose track comes from a function call, made again every time the entry is picked.
 */
public class LazyWeightEntry extends WeightEntryImpl {

    private final Supplier<Track> trackSupplier;
    private final String callText;

    /**
     * @param callText The call as written in the source, e.g. {@code mySongFunc()}
     */
    public LazyWeightEntry(int weightAmount, WeightUnit weightUnit, DynamicProvider dynamicProvider, boolean canRepeatTrack, boolean canRepeatWeight, String callText, Supplier<Track> trackSupplier) {
        super(weightAmount, weightUnit, null, dynamicProvider, canRepeatTrack, canRepeatWeight);
        this.callText = callText;
        this.trackSupplier = trackSupplier;
    }

    public String getCallText() {
        return callText;
    }

    @Override
    public String getTrackStringValue() {
        return callText;
    }

    @Override
    public Track getTrack() {
        return trackSupplier.get();
    }

    @Override
    public List<Track> getAllTracks() {
        return Collections.emptyList();
    }

    @Override
    public boolean isInconsistent() {
        return true;
    }
}
