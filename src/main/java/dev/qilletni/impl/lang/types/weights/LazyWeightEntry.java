package dev.qilletni.impl.lang.types.weights;

import dev.qilletni.api.lang.types.weights.WeightUnit;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.supplier.DynamicProvider;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Deprecated
public class LazyWeightEntry extends WeightEntryImpl {

    private final Supplier<Track> trackSupplier;

    public LazyWeightEntry(int weightAmount, WeightUnit weightUnit, DynamicProvider dynamicProvider, boolean canRepeatTrack, boolean canRepeatWeight, Supplier<Track> trackSupplier) {
        super(weightAmount, weightUnit, null, dynamicProvider, canRepeatTrack, canRepeatWeight);
        this.trackSupplier = trackSupplier;
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
