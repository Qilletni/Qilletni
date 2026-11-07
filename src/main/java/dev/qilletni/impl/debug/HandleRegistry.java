package dev.qilletni.impl.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The frame ids and variable references of one stop. Ids are unique for the whole session, so an id from an earlier
 * stop never finds anything in a later one.
 */
final class HandleRegistry {

    private final AtomicInteger nextId;
    private final Map<Integer, Object> handles = new HashMap<>();
    private final Map<Object, Integer> ids = new HashMap<>();

    HandleRegistry(AtomicInteger nextId) {
        this.nextId = nextId;
    }

    /**
     * @return The id of {@code handle}, the same one each time it's registered during this stop
     */
    int register(Object handle) {
        return ids.computeIfAbsent(handle, h -> {
            var id = nextId.getAndIncrement();
            handles.put(id, h);
            return id;
        });
    }

    <T> T get(int id, Class<T> type) {
        var handle = handles.get(id);
        if (!type.isInstance(handle)) {
            throw new IllegalArgumentException("Unknown or expired reference %d".formatted(id));
        }

        return type.cast(handle);
    }
}
