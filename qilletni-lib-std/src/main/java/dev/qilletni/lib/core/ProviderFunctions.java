package dev.qilletni.lib.core;

import dev.qilletni.api.music.supplier.DynamicProvider;

public class ProviderFunctions {

    private final DynamicProvider dynamicProvider;

    public ProviderFunctions(DynamicProvider dynamicProvider) {
        this.dynamicProvider = dynamicProvider;
    }

    public boolean setFetchResolveStrategy(String resolveStrategy) {
        return dynamicProvider.getMusicStrategies().getSearchResolveStrategyProvider().setSearchResolveStrategy(resolveStrategy);
    }

}
