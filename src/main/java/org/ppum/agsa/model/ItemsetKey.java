package org.ppum.agsa.model;

import java.util.*;
import java.util.stream.Collectors;

public final class ItemsetKey implements Comparable<ItemsetKey> {
    private final List<String> items;

    public ItemsetKey(Collection<String> items) {
        TreeSet<String> sorted = new TreeSet<>(items);
        if (sorted.isEmpty()) throw new IllegalArgumentException("Itemset cannot be empty");
        this.items = List.copyOf(sorted);
    }

    public static ItemsetKey parse(String text) {
        String normalized = text.trim().replace("{", "").replace("}", "");
        String[] parts = normalized.split("[,\\s]+");
        return new ItemsetKey(Arrays.stream(parts).filter(s -> !s.isBlank()).toList());
    }

    public List<String> items() { return items; }
    public int size() { return items.size(); }
    public boolean contains(String item) { return items.contains(item); }

    @Override public int compareTo(ItemsetKey other) {
        int n = Math.min(items.size(), other.items.size());
        for (int i = 0; i < n; i++) {
            int c = items.get(i).compareTo(other.items.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(items.size(), other.items.size());
    }

    @Override public boolean equals(Object o) {
        return o instanceof ItemsetKey k && items.equals(k.items);
    }
    @Override public int hashCode() { return items.hashCode(); }
    @Override public String toString() { return items.stream().collect(Collectors.joining(",")); }
}
