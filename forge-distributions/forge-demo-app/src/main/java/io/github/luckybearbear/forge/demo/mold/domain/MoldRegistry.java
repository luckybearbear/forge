package io.github.luckybearbear.forge.demo.mold.domain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MoldRegistry {

    private final Map<String, MoldEntity> store = new ConcurrentHashMap<>();

    public void save(MoldEntity entity) {
        store.put(entity.id(), entity);
    }

    public Optional<MoldEntity> find(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
