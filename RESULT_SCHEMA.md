# Result schema

`benchmark` and `batch` write one CSV row per algorithm/configuration. Important fields:

* provenance/pairing: `config_id`, `dataset`, `timeline`, `replicate`, `seed`, `sep_pct`, `sensitive_hash`;
* data/thresholds: `tx_count`, `nonempty_tx`, `total_utility`, `min_util`, `min_per`, `max_per`, `min_avg`, `max_avg`, `max_len`, `top_k`;
* pattern counts: `original_HUIs`, `sanitized_HUIs`, `original_PHUIs`, `sanitized_PHUIs`, `sensitive_count`;
* quality: `HF`, `MC`, `AC`, percentages, `IUS_pct`, `DUS_pct`, `DSS_pct`, `structureL2`;
* sanitization footprint: `modifiedTx_pct`, `editedCells`, `editOps`, `utilityLossAbs`, `utilityLoss_pct`;
* AGSA diagnostics: `candidateEvaluations`, `guardRejections`, `guardReject_pct`, `witnessChecks`, `witnessUniverseSize`;
* performance: `originalMining_ms`, `originalCacheHit`, `postMining_ms`, `sanitizer_ms`, `algorithmWall_ms`, `originalMiningPeak_MiB`, `algorithmPeak_MiB`;
* status: `feasible`, `status`, `message`.

For optimized fixed-timeline runs, `postMining_ms=0` because final HUI/PHUI states are obtained exactly from the original-HUI witness tracker under decrease-only positive-utility semantics. Compressed-timeline audit rows still remine because TIDs are reindexed.
