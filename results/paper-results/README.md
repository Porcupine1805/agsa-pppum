# Frozen rows behind the TDP tables

These CSVs are the measured output of the registered paper and scaling matrices.

| File | Rows | Configurations |
|---|---:|---|
| `ready_paper_runs.csv` | 355 | 71 completed paper configs × 5 algorithms |
| `ready_scaling_runs.csv` | 60 | 12/12 scaling configs × 5 algorithms |

Status counts in the paper CSV: 325 `OK`, 30 `NO_PHUI` (Mushroom `minPer` ∈ {2,3}).

The missing paper configuration is `paper-retail-n88162-p1-r1` (Retail, `minPer=1`, seed `202601`). AGSA's full-universe candidate oracle did not return on that high-overlap sensitive draw. The manuscript reports it as TO and averages Retail `minPer=1` over the two completed seeds. That cell is not imputed here.

Do not mix these files with older `tdp_paper_runs.csv` snapshots.

Manuscript Fig1–Fig3 and the ESM result figures are regenerated from these CSVs by `../../scripts/make_manuscript_figures.py`.
