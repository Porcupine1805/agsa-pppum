# AGSA-PPPUM

Reproducibility package for:

**Hiding Sensitive Periodic High-Utility Itemsets: Exact Entry Channels and Guarded Sanitization**

Khac Chien Nguyen and Cong Phat Tran, People's Police University.

This repository contains the Java 17 implementation, correctness suites, registered experiment matrices, and the frozen CSV files used in the tables. Benchmark transaction files (Foodmart, Retail, …) are not redistributed; they are prepared from [DSI-Lab1/PPUM](https://github.com/DSI-Lab1/PPUM) and [SPMF](https://www.philippe-fournier-viger.com/spmf/index.php?link=datasets.php) as documented below.

## Contents

| Path | Role |
|---|---|
| `src/main/java` | Miner and sanitizers (SMAU, SMSE, MU-MAP, MU-MIP, AGSA, MPH, …) |
| `data/` | Ten-transaction running example and compaction toy |
| `datasets/liquor_11/` | Bundled Liquor_11 extract used in the paper |
| `datasets/ready/manifest.csv` | Sizes and SHA-256 hashes of the eight converted datasets |
| `experiments/` | Registered smoke, paper, scaling, and follow-up matrices |
| `results/paper-results/` | Frozen CSVs behind the main and scaling tables |
| `results/q2/` | MU-MIP, Liu REF, Retail timeout, ten-draw extra seeds |
| `scripts/` | Build, dataset preparation, correctness tests, runners |
| `docs/EXPERIMENT_GUIDE_VI.md` | Full build-to-tables procedure |

## Build (JDK 17)

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
java -jar build/agsa-pppum.jar selftest
```

Linux / macOS:

```bash
./scripts/build.sh
java -jar build/agsa-pppum.jar selftest
```

## Reproduce

Correctness suites (2,200 small databases):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_adversarial_suite.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_adversarial_2000.ps1
```

Prepare the eight paper datasets, then run a smoke profile:

```powershell
git clone https://github.com/DSI-Lab1/PPUM.git external/DSI-Lab1-PPUM
python scripts/prepare_all_datasets.py --ppum-root external/DSI-Lab1-PPUM
python scripts/generate_ready_experiments.py --profile all
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_ready_pipeline.ps1 -Profile smoke
```

Toy theorems (no PPUM clone required):

```bash
java -jar build/agsa-pppum.jar directions --out results/three_directions
java -jar build/agsa-pppum.jar compaction --out results/compaction
```

See `docs/EXPERIMENT_GUIDE_VI.md` for the paper and scaling profiles.

## License

MIT (`LICENSE`). Benchmark files from DSI-Lab1/PPUM and SPMF remain under those projects' terms.
