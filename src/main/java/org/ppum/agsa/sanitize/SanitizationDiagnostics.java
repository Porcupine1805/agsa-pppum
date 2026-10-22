package org.ppum.agsa.sanitize;

/** Algorithm-internal counters recorded for ablation and scalability analysis. */
public record SanitizationDiagnostics(long candidateEvaluations,
                                      long guardRejections,
                                      long witnessChecks,
                                      int witnessUniverseSize) {
    public static SanitizationDiagnostics none(int witnessUniverseSize) {
        return new SanitizationDiagnostics(0L, 0L, 0L, witnessUniverseSize);
    }
}
