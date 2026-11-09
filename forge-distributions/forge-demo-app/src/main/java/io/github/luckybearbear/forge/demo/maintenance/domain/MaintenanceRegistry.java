package io.github.luckybearbear.forge.demo.maintenance.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MaintenanceRegistry {

    private final Map<String, MaintenanceOrder> store = new ConcurrentHashMap<>();
    private final List<String> moldOrder = new CopyOnWriteArrayList<>();

    public void save(MaintenanceOrder order) {
        store.put(order.id(), order);
        moldOrder.add(order.moldId() + ":" + order.id());
    }

    public Optional<MaintenanceOrder> find(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<MaintenanceOrder> findByMoldId(String moldId) {
        return moldOrder.stream()
                .filter(entry -> entry.startsWith(moldId + ":"))
                .map(entry -> store.get(entry.substring(entry.indexOf(':') + 1)))
                .toList();
    }
}
