package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.PeriodicityStats;

/** Lightweight pattern state used in hot candidate-evaluation loops. */
record PatternSnapshot(long utility, PeriodicityStats periodicity, boolean highUtility, boolean periodicHighUtility) {}
