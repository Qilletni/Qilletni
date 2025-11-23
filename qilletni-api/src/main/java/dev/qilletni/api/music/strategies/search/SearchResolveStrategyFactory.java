package dev.qilletni.api.music.strategies.search;

import java.util.Optional;

public interface SearchResolveStrategyFactory<I, O> {

    /**
     * Registers a search resolving strategy. This is mapped with its internal name.
     *
     * @param strategy The strategy to register
     */
    void registerStrategy(SearchResolveStrategy<I, O> strategy);

    /**
     * Gets a strategy by its name. This is case insensitive.
     *
     * @param name The name of the strategy
     * @return The found strategy, if present
     */
    Optional<SearchResolveStrategy<I, O>> getStrategy(String name);

}
