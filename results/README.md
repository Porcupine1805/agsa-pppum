# Frozen experiment data for the JDSA manuscript

Toy example databases that the Java CLI reads live in `../data/`. Converted eight-dataset files are prepared into `../datasets/ready/` (see `../scripts/prepare_all_datasets.py`); they are not stored in this folder.

| Path | Contents |
|---|---|
| `paper-results/` | Frozen CSVs behind the main and scaling tables |
| `q2/` | Follow-up runs: MU-MIP, Liu REF, Retail timeout, ten-draw extra seeds |
| `three_directions/` | Outputs of the toy theorem / compaction studies |
| `datasets/manifest.csv` | Dataset sizes, semantics, and SHA-256 hashes |
| `SHA256.json` | Hashes of the reproducibility archive |
| `results_validation.json` | Rechecked table values, Friedman and Wilcoxon statistics |
