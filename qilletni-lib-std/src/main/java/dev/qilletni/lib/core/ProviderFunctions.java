package dev.qilletni.lib.core;

import dev.qilletni.api.music.supplier.DynamicProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderFunctions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderFunctions.class);

    private final DynamicProvider dynamicProvider;

    public ProviderFunctions(DynamicProvider dynamicProvider) {
        this.dynamicProvider = dynamicProvider;
    }

    public boolean setFetchResolveStrategy(String resolveStrategy) {
        var strategyOptional = dynamicProvider.getMusicStrategies().getSearchResolveStrategyProvider();
        if (strategyOptional.isEmpty()) {
            LOGGER.warn("Service provider does not implement any search resolve strategies");
            return false;
        }

        return strategyOptional.get().setSearchResolveStrategy(resolveStrategy);
    }

}
