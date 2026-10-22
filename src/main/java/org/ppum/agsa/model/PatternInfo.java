package org.ppum.agsa.model;

import java.util.*;

public record PatternInfo(ItemsetKey key, long utility, PeriodicityStats periodicity,
                          boolean highUtility, boolean periodicHighUtility, List<Integer> tids) {
    public long utilityMargin(PeriodicityConstraints c) { return utility - c.minUtil(); }
}
