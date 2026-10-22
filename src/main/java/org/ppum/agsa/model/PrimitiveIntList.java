package org.ppum.agsa.model;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

/** Immutable primitive-backed integer list used for transaction-id sets. */
public final class PrimitiveIntList extends AbstractList<Integer> implements RandomAccess {
    private final int[] values;

    public PrimitiveIntList(int[] values) {
        this.values = values.clone();
    }

    public static PrimitiveIntList copyOf(int[] values, int size) {
        return new PrimitiveIntList(Arrays.copyOf(values, size));
    }

    @Override public Integer get(int index) { return values[index]; }
    public int getInt(int index) { return values[index]; }
    @Override public int size() { return values.length; }
    @Override public boolean contains(Object o) {
        if (!(o instanceof Integer x)) return false;
        return Arrays.binarySearch(values, x) >= 0;
    }
    public int[] toIntArray() { return values.clone(); }
}
