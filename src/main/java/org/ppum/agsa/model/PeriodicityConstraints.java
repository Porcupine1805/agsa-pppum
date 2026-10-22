package org.ppum.agsa.model;

public record PeriodicityConstraints(long minUtil, int minPer, int maxPer, double minAvg, double maxAvg) {
    public PeriodicityConstraints {
        if(minUtil<=0)throw new IllegalArgumentException("minUtil must be positive");
        if(minPer<1)throw new IllegalArgumentException("minPer must be at least 1 under distinct positive TIDs");
        if(maxPer<minPer)throw new IllegalArgumentException("maxPer must be >= minPer");
        if(!Double.isFinite(minAvg)||!Double.isFinite(maxAvg)||minAvg<0||maxAvg<minAvg)
            throw new IllegalArgumentException("Average-period bounds must be finite and satisfy 0 <= minAvg <= maxAvg");
    }
    public boolean accepts(PeriodicityStats s) {
        if (s.support() <= 0) return false;
        return s.minPeriod() >= minPer && s.maxPeriod() <= maxPer
                && s.avgPeriod() >= minAvg && s.avgPeriod() <= maxAvg;
    }
}
