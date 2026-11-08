package dev.qilletni.impl.debug;

import java.util.List;

/**
 * A weighted pick, sent to the IDE as it happens. Plain text and numbers only, so it can leave the interpreter thread.
 *
 * @param weightsId       Identifies the outermost weights for this session
 * @param label           Names the weights for the user, e.g. {@code weights(3 entries) @ main.ql:14}
 * @param kind            {@code pick}, or {@code miss} when no entry was rolled and the collection's own queue was used
 * @param path            The entry chosen at each level, outermost first. Empty for a miss
 * @param rejectedOmitted Rejections left out past the first {@value DebugController#MAX_REJECTED}
 * @param breakdown       The weights' chances, or {@code null} if unchanged since the last pick sent for these weights
 */
public record WeightPick(int weightsId, String label, String kind, List<Level> path, String song, String trackId,
                         List<Rejected> rejected, int rejectedOmitted, Breakdown breakdown) {

    /**
     * Rolls and bounds are in percent, with the range being {@code [from, to)}.
     *
     * @param chance The entry's chance within its own weights, in percent
     */
    public record Level(int index, String name, double roll, double from, double to, double chance) {}

    /**
     * @param indices The rejected attempt's entry indices, outermost first, starting from the outermost weights
     * @param path    The names of those entries
     * @param reason  {@code same track} or {@code same entry}
     */
    public record Rejected(List<Integer> indices, List<String> path, String song, String reason) {}

    /**
     * @param miss          The chance of no entry being picked, in percent
     * @param omitted       Leaves left out of {@code roots}
     * @param omittedChance Their combined chance, in percent
     */
    public record Breakdown(List<Node> roots, double miss, int omitted, double omittedChance) {}

    /**
     * @param kind     {@code song}, {@code list}, {@code collection}, {@code album}, {@code weights}, {@code function}
     *                 or {@code multiplier}
     * @param chance   The chance within its own weights, in percent
     * @param combined The chance of the path to this node, in percent
     * @param source   Display text of the value the entry picks from
     * @param listSize  The number of songs for a {@code list} entry, otherwise 0
     * @param songCount The number of songs a list, collection or album entry picks from, otherwise 0
     */
    public record Node(String name, int index, String kind, double chance, double combined, String source, int listSize,
                       int songCount, List<Node> children) {}
}
