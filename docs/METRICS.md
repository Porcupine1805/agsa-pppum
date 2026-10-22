# Metrics written to the result CSV

Primary source-compatible quality metrics:

* `HF`, `HF_pct`: sensitive PHUIs still discoverable; lower, target 0.
* `MC`, `MC_pct`: original non-sensitive PHUIs lost; lower.
* `AC`, `AC_pct`: PHUIs appearing after sanitization that were not original PHUIs. This is a no-spurious-PHUI release-integrity constraint (and a privacy constraint only when the owner explicitly prohibits newly periodic profitable disclosures); AGSA hard-guard target 0.
* `IUS_pct`: post/pre PHUI-utility sum ratio under the source-paper definition; higher, interpreted jointly with MC because sensitive PHUIs are intentionally removed.
* `DUS_pct`: total sanitized database utility / original total database utility; higher.
* `DSS_pct`: cosine similarity between transaction item-presence-pattern frequency vectors; higher.

Supplemental footprint/efficiency metrics:

* `utilityLossAbs`, `utilityLoss_pct`.
* `modifiedTx_pct`, `editedCells`, `editOps`.
* `structureL2`: raw L2 distance between transaction-pattern frequency vectors; lower.
* `sanitizer_ms`, `algorithmWall_ms`, `originalMining_ms`, `postMining_ms`.
* `originalMiningPeak_MiB`, `algorithmPeak_MiB`.
* `candidateEvaluations`, `witnessChecks`, `guardRejections`, `guardReject_pct`, `witnessUniverseSize`.
* `originalCacheHit`: whether a batch row reused the already mined original HUI universe.
* `sensitive_hash`: shortened SHA-256 fingerprint; paired comparisons are valid only when this matches.

The analysis scripts compute mean, SD, median/IQR, paired Wilcoxon tests, Holm correction, and rank-biserial effect size. A multi-algorithm Friedman/post-hoc analysis should be added to the final paper if a single ranking across all datasets/configurations is reported.
