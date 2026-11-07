package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.weights.WeightEntry;

/**
 * One level of a weighted selection: the weights picked from, and the entry chosen.
 *
 * @param entryIndex The index of {@code entry} in {@code weights}
 */
public record WeightStep(WeightsType weights, WeightEntry entry, int entryIndex) {
}
