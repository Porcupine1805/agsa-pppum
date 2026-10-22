package org.ppum.agsa.sanitize;

/** Heuristic weights; they affect ranking only, never the hard AC guarantee. */
public record AgsaWeights(double hidden,double progress,double missing,double utility,double edit) {
    public static AgsaWeights defaults(){return new AgsaWeights(1000.0,60.0,30.0,8.0,2.0);}
}
