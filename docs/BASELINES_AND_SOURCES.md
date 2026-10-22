# Algorithms in this repository

No file from [DSI-Lab1/PPUM](https://github.com/DSI-Lab1/PPUM) is redistributed. Clone that repository separately for Foodmart, Retail, and Mushroom.

## TDP paper table (five algorithms)

| Label | Role | Source |
|---|---|---|
| `SMAU-REF` | Liu-style hide-by-utility, max-utility victim | Liu, Wen & Zuo, Applied Intelligence 50:169–191, 2020 |
| `SMSE-REF` | Liu-style hide-by-utility, min side-effect victim | same |
| `MU-MAP` | Zhou victim: max sensitive-utility transaction, max singleton maxPer item | Zhou, Gan, Qi, Yu, IEEE IoT Journal 13(2):2067–2084, 2026 |
| `AGSA` | local search over utility breakpoints and deletion, original-HUI witness guard | this work |
| `SMAU-PHUI`, `SMSE-PHUI` | same victim rules as the corresponding reference family, but matched PHUI stopping | this artifact (fairness variants) |
| `MPH` | min (hide, MC, loss) one-cell edits on \(S\cup N\), stop when the target is not a PHUI | this work |

The `-REF` suffix means a clean-room implementation of the published decision rule. Byte-for-byte equality with DSI-Lab1 source is not claimed.

Paper AC is a **re-mine** count (`forceRemine=true` in `experiments/tdp_paper.csv`). Do not mix tracker AC from other matrices.

## Implemented, not in the TDP tables

`MU-MIP`, `SMIU-REF`, `FAGSA`, `AGSA-NOGUARD`, and related ablations remain in `src/` and can be passed to `--algorithms`.

## Mining

`VerticalHuiMiner` is a self-contained positive-utility HUI-Miner-style miner. Period gaps follow PHM (Fournier-Viger et al., LNCS 9728, 2016): internal gaps plus the two boundary gaps from TID 0 and to \(n\).
