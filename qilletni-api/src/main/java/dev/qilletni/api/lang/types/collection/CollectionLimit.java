package dev.qilletni.api.lang.types.collection;

/**
 * A backing type for the <code>limit</code> type on a collection, e.g. <code>limit[5]</code> or <code>limit[3h]</code>.
 * This defines how much of a collection should be played.
 * 
 * @param limitCount The numerical part of the limit
 * @param limitUnit  The unit of the limit
 */
public record CollectionLimit(int limitCount, CollectionLimitUnit limitUnit) {

    /**
     * Gets the limit in milliseconds.
     * 
     * @return The limit in milliseconds
     */
    public long calculateLimitMilliseconds() {
        if (limitUnit == CollectionLimitUnit.COUNT) {
            throw new IllegalStateException("Attempted to calculate limit in milliseconds for a count-based limit");
        }
        
        return limitUnit.getTimeUnit().toMillis(limitCount);
    }

    @Override
    public String toString() {
        return String.format("CollectionLimit{%d%s}", limitCount, limitUnit.getUnitText());
    }
}
