package dev.qilletni.api.lang.types.weights;

import dev.qilletni.api.exceptions.InvalidWeightException;
import dev.qilletni.api.lang.types.WeightsType;

/**
 * Utilities to assist in weight selection.
 */
public class WeightUtils {

    /**
     * Checks if the weights are valid, meaning they don't exceed a total of 100%
     *
     * @param weights The weights to validate
     * @return The total weight percent
     */
    public static double validateWeights(WeightsType weights) {
        if (weights == null) {
            return 0;
        }

        double totalPercent = weights.getWeightEntries().stream()
                .filter(entry -> entry.getWeightUnit() == WeightUnit.PERCENT)
                .map(WeightEntry::getWeightAmount)
                .reduce(Double::sum)
                .orElse(0D);

        if (totalPercent > 100) {
            throw new InvalidWeightException("Total weight percentage cannot go over 100%! Current total is " + totalPercent + "%");
        }
        
        return totalPercent;
    }

    /**
     * Checks if the provided weights are constrained. Weights are constrained if all entries add up to 100%, and
     * contain no other weight entries.
     *
     * @param weights The weights to check
     * @return Whether the weights are constrained
     */
    public static boolean areWeightsConstrained(WeightsType weights) {
        if (weights.getWeightEntries().stream()
                .anyMatch(entry -> entry.getWeightUnit() != WeightUnit.PERCENT)) {
            return false;
        }

        return weights.getWeightEntries().stream().mapToDouble(WeightEntry::getWeightAmount).sum() == 100;
    }
    
}
