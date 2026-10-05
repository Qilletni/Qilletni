package dev.qilletni.api.lang.types.collection;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * The unit for a collection limit from {@link CollectionLimit}.
 */
public enum CollectionLimitUnit {
    /**
     * A set number of songs are played from the collection.
     */
    COUNT("", null),

    /**
     * The collection is played until a set number of seconds passes.
     */
    SECOND("s", TimeUnit.SECONDS),

    /**
     * The collection is played until a set number of minutes passes.
     */
    MINUTE("m", TimeUnit.MINUTES),

    /**
     * The collection is played until a set number of hours passes.
     */
    HOUR("h", TimeUnit.HOURS);
    
    private final String unitText;
    private final TimeUnit timeUnit;

    CollectionLimitUnit(String unitText, TimeUnit timeUnit) {
        this.unitText = unitText;
        this.timeUnit = timeUnit;
    }

    /**
     * Returns the unit text representation of the collection limit unit. This value serves as a textual suffix
     * or identifier (e.g., "s" for seconds, "m" for minutes) used in formatting and representations.
     *
     * @return the unit text associated with the collection limit unit
     */
    public String getUnitText() {
        return unitText;
    }

    /**
     * Gets the Java {@link TimeUnit} the limit unit represents, or null if it's not applicable for the unit (is a
     * {@link #COUNT}). 
     * 
     * @return The {@link TimeUnit} of the unit, if applicable
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Returns true if the unit is time-based, false otherwise.
     * 
     * @return True if the unit is time-based, false otherwise
     */
    public boolean isTimeBased() {
        return timeUnit != null;
    }

    /**
     * Gets the {@link CollectionLimitUnit} from the given prefix.
     * 
     * @param text The prefix of the unit
     * @return The found {@link CollectionLimitUnit}
     * @throws IllegalStateException If the specified string does not match any defined prefix
     */
    public static CollectionLimitUnit fromText(String text) {
        return Arrays.stream(values())
                .filter(unit -> unit.unitText.equals(text)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unexpected value: " + text));
    }
}
