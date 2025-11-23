package dev.qilletni.api.music.strategies;

import dev.qilletni.api.music.strategies.search.SearchResolveStrategy;
import dev.qilletni.api.music.strategies.search.SearchResolveStrategyFactory;

/**
 * Implemented by libraries to store strategies.
 */
public interface MusicStrategies<SIn, SOut> {

    SearchResolveStrategyProvider<SIn, SOut> getSearchResolveStrategyProvider();


    interface SearchResolveStrategyProvider<I, O> {
        /**
         * Gets the search resolve strategy factory for the service provider. With this, new strategies can be registered.
         *
         * @return The service provider's {@link SearchResolveStrategyFactory}
         */
        SearchResolveStrategyFactory<I, O> getSearchResolveStrategyFactory();

        /**
         * Sets the {@link SearchResolveStrategy} by a given name for the current
         * service.
         *
         * @param name The name of the strategy
         * @return If the method was successful
         */
        boolean setSearchResolveStrategy(String name);

        /**
         * Gets the current {@link SearchResolveStrategy} used by the service provider.
         *
         * @return The current {@link SearchResolveStrategy}
         */
        SearchResolveStrategy<I, O> getCurrentSearchResolveStrategy();
    }
}
