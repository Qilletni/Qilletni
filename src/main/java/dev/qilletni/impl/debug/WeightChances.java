package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.types.ListType;
import dev.qilletni.api.lang.types.OrderableTracksType;
import dev.qilletni.api.lang.types.collection.CollectionOrder;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.lang.types.weights.WeightUnit;
import dev.qilletni.impl.lang.types.weights.LazyWeightEntry;
import dev.qilletni.impl.lang.types.weights.WeightEntryImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The chance of each entry in a weights value being picked. Never loads music: collections, albums and lists are
 * leaves, and only a list's size is read.
 * <p>
 * Top-level weights that don't add up to 100% miss, falling back to a collection's own tracks. Nested weights reroll
 * on a miss, so their chances are relative to their own total. Multiplier entries are never rolled.
 */
final class WeightChances {

    static final int MAX_LEAVES = 500;
    private static final int MAX_DEPTH = 16;

    private WeightChances() {}

    /**
     * @param index    The entry's index in its weights
     * @param kind     {@code song}, {@code list}, {@code collection}, {@code album}, {@code weights}, {@code function}
     *                 or {@code multiplier}
     * @param chance   The chance within its own weights, in percent
     * @param combined The chance of the whole path to this node, in percent
     * @param source     The value the entry picks from, or {@code null} for a function entry
     * @param sourceText Display text of what the entry picks from, e.g. the call of a function entry
     * @param songCount  The number of songs a list, collection or album picks from, otherwise 0
     * @param amount     The entry's amount as written, a percent or a multiplier
     * @param sequential If the entry is a collection or album played in order
     * @param span       Where the entry is written, or {@code null} if unknown
     * @param children   The nested entries of a {@code weights} node
     */
    record Node(String name, int index, String kind, double chance, double combined, QilletniType source, String sourceText,
                int songCount, double amount, boolean sequential, SourceSpan span, List<Node> children) {

        int listSize() {
            return source instanceof ListType list ? list.getItems().size() : 0;
        }
    }

    /**
     * @param miss    The chance that no entry is picked, in percent
     * @param omitted Leaves left out past {@link #MAX_LEAVES}
     * @param omittedChance The combined chance of the omitted leaves, in percent
     */
    record Tree(List<Node> roots, double miss, int leafCount, int omitted, double omittedChance) {}

    static Tree breakdown(WeightsType weights) {
        var counter = new Counter();
        var roots = nodes(weights, false, 100, Collections.newSetFromMap(new IdentityHashMap<>()), 0, counter);
        return new Tree(roots, Math.max(0, 100 - percentTotal(weights)), counter.leaves, counter.omitted, counter.omittedChance);
    }

    /**
     * @param nested If the weights were picked by an outer weights, so a miss rerolls
     * @return The entry's chance within its weights, in percent
     */
    static double levelChance(WeightsType weights, WeightEntry entry, boolean nested) {
        if (entry.getWeightUnit() != WeightUnit.PERCENT) {
            return 0;
        }

        if (!nested) {
            return entry.getWeightAmount();
        }

        var total = percentTotal(weights);
        return total == 0 ? 0 : entry.getWeightAmount() / total * 100;
    }

    static double percentTotal(WeightsType weights) {
        return weights.getWeightEntries().stream()
                .filter(entry -> entry.getWeightUnit() == WeightUnit.PERCENT)
                .mapToDouble(WeightEntry::getWeightAmount)
                .sum();
    }

    /**
     * @return The leaves of the tree with the path to each, outermost first
     */
    static List<List<Node>> leafPaths(Tree tree) {
        var paths = new ArrayList<List<Node>>();
        collectLeaves(tree.roots(), new ArrayList<>(), paths);
        return paths;
    }

    /**
     * @return e.g. {@code 15%}, {@code 33.33%} or {@code 0.0033%}
     */
    static String percent(double value) {
        return number(value) + "%";
    }

    /**
     * @return Up to 2 decimals, or 2 significant digits below 1, with trailing zeros dropped
     */
    static String number(double value) {
        if (value == 0 || !Double.isFinite(value)) {
            return value == 0 ? "0" : Double.toString(value);
        }

        var decimals = Math.abs(value) >= 1 ? 2 : Math.max(2, 1 - (int) Math.floor(Math.log10(Math.abs(value))));
        return new BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * @return e.g. {@code 3,000}
     */
    static String count(int count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    private static List<Node> nodes(WeightsType weights, boolean nested, double parentCombined, Set<WeightsType> visiting, int depth, Counter counter) {
        var entries = weights.getWeightEntries();
        var nodes = new ArrayList<Node>(entries.size());
        visiting.add(weights);

        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var name = MusicValues.entryName(i, entry);
            var source = entry instanceof LazyWeightEntry ? null : MusicValues.entrySource(entry);
            var sourceText = MusicValues.entrySourceText(entry);
            var songCount = entry instanceof WeightEntryImpl impl ? impl.getKnownTrackCount() : 0;
            var amount = entry.getWeightAmount();
            var sequential = source instanceof OrderableTracksType orderable && orderable.getOrder() == CollectionOrder.SEQUENTIAL;
            var span = entry instanceof WeightEntryImpl impl ? impl.getSourceSpan() : null;

            if (entry.getWeightUnit() != WeightUnit.PERCENT) {
                nodes.add(new Node(name, i, "multiplier", 0, 0, source, sourceText, 0, amount, false, span, List.of()));
                continue;
            }

            var chance = levelChance(weights, entry, nested);
            var combined = parentCombined * chance / 100;
            var kind = kind(entry);

            if (source instanceof WeightsType inner && depth < MAX_DEPTH && !visiting.contains(inner)) {
                nodes.add(new Node(name, i, kind, chance, combined, source, sourceText, 0, amount, false, span, nodes(inner, true, combined, visiting, depth + 1, counter)));
            } else if (counter.leaves < MAX_LEAVES) {
                counter.leaves++;
                nodes.add(new Node(name, i, kind, chance, combined, source, sourceText, songCount, amount, sequential, span, List.of()));
            } else {
                counter.omitted++;
                counter.omittedChance += combined;
            }
        }

        visiting.remove(weights);
        return nodes;
    }

    private static String kind(WeightEntry entry) {
        return switch (entry.getTrackType()) {
            case SINGLE_TRACK -> "song";
            case LIST -> "list";
            case COLLECTION, PLAYLIST -> "collection";
            case ALBUM -> "album";
            case WEIGHTS -> "weights";
            case FUNCTION -> "function";
        };
    }

    private static void collectLeaves(List<Node> nodes, List<Node> path, List<List<Node>> paths) {
        for (var node : nodes) {
            path.add(node);
            if (node.children().isEmpty()) {
                paths.add(List.copyOf(path));
            } else {
                collectLeaves(node.children(), path, paths);
            }
            path.removeLast();
        }
    }

    private static final class Counter {
        int leaves;
        int omitted;
        double omittedChance;
    }
}
