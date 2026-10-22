package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Inverted index from an edited item to original HUI witnesses affected by that edit. */
final class ImpactIndex {
    private final Map<String, List<ItemsetKey>> byItem = new HashMap<>();
    ImpactIndex(Collection<ItemsetKey> keys) {
        for (ItemsetKey k : keys) for (String item : k.items())
            byItem.computeIfAbsent(item, x -> new ArrayList<>()).add(k);
    }
    List<ItemsetKey> affected(String item) { return byItem.getOrDefault(item, List.of()); }
}
