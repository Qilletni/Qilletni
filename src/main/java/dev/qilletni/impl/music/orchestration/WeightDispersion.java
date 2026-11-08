package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.lang.types.weights.WeightUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class WeightDispersion {

    private final List<DispersedWeightEntry> scaledWeights;
    private final int scalingFactor;

    WeightDispersion(List<DispersedWeightEntry> scaledWeights, int scalingFactor) {
        this.scaledWeights = scaledWeights;
        this.scalingFactor = scalingFactor;
    }
    
    public static WeightDispersion initializeWeightDispersion(WeightsType weightsType) {
        if (weightsType == null) {
            return new EmptyWeightDispersion();
        }
        
        int scalingFactor = findScalingFactor(weightsType.getWeightEntries());
        return new WeightDispersion(scaleAndCumulateWeights(scalingFactor, weightsType.getWeightEntries()), scalingFactor);
    }

    /**
     * Selects a {@link WeightEntry} based off of dispersed weights.
     * 
     * @return A found {@link WeightEntry}, if any
     */
    public Optional<WeightEntry> selectWeight() {
        return select().map(Selection::entry);
    }

    /**
     * Rolls and selects an entry. Only percent entries are rolled; multipliers only affect a collection's queue.
     *
     * @return The selection, or empty if the roll fell past every entry
     */
    public Optional<Selection> select() {
        return select(ThreadLocalRandom.current().nextInt(100 * scalingFactor));
    }

    /**
     * @param roll A roll in {@code [0, 100 * scalingFactor)}
     */
    Optional<Selection> select(int roll) {
        int low = 0, high = scaledWeights.size() - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (scaledWeights.get(mid).cumulativeWeight() <= roll) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        // low is now the first entry whose cumulative weight is greater than the roll
        if (low >= scaledWeights.size()) {
            return Optional.empty();
        }

        var dispersed = scaledWeights.get(low);
        var from = low == 0 ? 0 : scaledWeights.get(low - 1).cumulativeWeight();
        return Optional.of(new Selection(dispersed.weightEntry(), dispersed.index(), roll, from, dispersed.cumulativeWeight(), scalingFactor));
    }

    private static int findScalingFactor(List<WeightEntry> weights) {
        int maxDecimalPlaces = 0;
        for (var weightEntry : percentEntries(weights)) {
            String weightStr = Double.toString(weightEntry.getWeightAmount());
            int decimalPlaces = weightStr.length() - weightStr.indexOf('.') - 1;
            maxDecimalPlaces = Math.max(maxDecimalPlaces, decimalPlaces);
        }
        return (int) Math.pow(10, maxDecimalPlaces);
    }

    private static List<WeightEntry> percentEntries(List<WeightEntry> weightEntries) {
        return weightEntries.stream().filter(entry -> entry.getWeightUnit() == WeightUnit.PERCENT).toList();
    }

    private static List<DispersedWeightEntry> scaleAndCumulateWeights(int scalingFactor, List<WeightEntry> weightEntries) {
        int cumulative = 0;
        var scaledWeights = new ArrayList<DispersedWeightEntry>();

        for (int i = 0; i < weightEntries.size(); i++) {
            var weightEntry = weightEntries.get(i);
            if (weightEntry.getWeightUnit() != WeightUnit.PERCENT) {
                continue;
            }

            cumulative += (int) (weightEntry.getWeightAmount() * scalingFactor);
            scaledWeights.add(new DispersedWeightEntry(weightEntry, i, cumulative));
        }

        return scaledWeights;
    }

    /**
     * @param index The entry's index in its weights, counting multiplier entries
     */
    record DispersedWeightEntry(WeightEntry weightEntry, int index, int cumulativeWeight) {}

    /**
     * A rolled selection. Rolls and bounds are in scaled units; divide by {@code scale} for percent.
     *
     * @param index The entry's index in its weights
     * @param from  The first roll that selects this entry
     * @param to    The first roll past this entry
     */
    public record Selection(WeightEntry entry, int index, int roll, int from, int to, int scale) {

        public double rollPercent() {
            return (double) roll / scale;
        }

        public double fromPercent() {
            return (double) from / scale;
        }

        public double toPercent() {
            return (double) to / scale;
        }

        /**
         * @return The chance of this entry within its own weights, in percent
         */
        public double chance() {
            return (double) (to - from) / scale;
        }
    }

    @Override
    public String toString() {
        return "WeightDispersion{scaledWeights=%s, scalingFactor=%d}".formatted(scaledWeights, scalingFactor);
    }
}
