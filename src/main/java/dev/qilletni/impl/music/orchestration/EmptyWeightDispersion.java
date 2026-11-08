package dev.qilletni.impl.music.orchestration;

import java.util.Optional;

public class EmptyWeightDispersion extends WeightDispersion {

    EmptyWeightDispersion() {
        super(null, 0);
    }

    @Override
    public Optional<Selection> select() {
        return Optional.empty();
    }
}
