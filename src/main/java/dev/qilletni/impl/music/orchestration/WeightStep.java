package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.weights.WeightEntry;

import java.util.List;

/**
 * One level of a weighted selection: the weights picked from, and the roll that chose an entry.
 *
 * @param rejected Picks at this level that were rejected by {@code |!} or {@code |~} before this one
 */
public record WeightStep(WeightsType weights, WeightDispersion.Selection selection, List<WeightRejection> rejected) {

    public WeightEntry entry() {
        return selection.entry();
    }

    public int entryIndex() {
        return selection.index();
    }

    WeightStep withRejected(List<WeightRejection> rejected) {
        return new WeightStep(weights, selection, rejected);
    }
}
