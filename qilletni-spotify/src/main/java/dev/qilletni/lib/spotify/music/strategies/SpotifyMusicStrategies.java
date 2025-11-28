package dev.qilletni.lib.spotify.music.strategies;

import dev.qilletni.api.music.strategies.MusicStrategies;

import java.util.Optional;

public class SpotifyMusicStrategies implements MusicStrategies<Object, Object> {

    @Override
    public Optional<SearchResolveStrategyProvider<Object, Object>> getSearchResolveStrategyProvider() {
        return Optional.empty();
    }
}
