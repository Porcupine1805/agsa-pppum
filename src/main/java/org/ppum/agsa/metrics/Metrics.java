package org.ppum.agsa.metrics;

public record Metrics(String algorithm,
                      int originalHUIs, int sanitizedHUIs,
                      int originalPHUIs, int sanitizedPHUIs,
                      int sensitiveCount, int hidingFailure, int missingCost, int artificialCost,
                      double hfRatePct, double mcRatePct, double acRatePct,
                      double itemsetUtilitySimilarityPct, double databaseUtilitySimilarityPct,
                      double databaseStructureSimilarityPct, double transactionPatternL2, double modifiedTransactionPct,
                      int editedCells, int editOperations,
                      long utilityLossAbs, double utilityLossPct,
                      long candidateEvaluations, long guardRejections, double guardRejectPct,
                      long witnessChecks, int witnessUniverseSize,
                      long runtimeMillis, boolean feasible) {}
