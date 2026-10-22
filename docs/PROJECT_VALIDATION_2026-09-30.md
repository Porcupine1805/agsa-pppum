# Project validation report — 2026-09-30

## Executed checks

- Build and built-in self-test: PASS.
- Original targeted suite: 200 databases, 1,170 sanitizer runs, zero failures.
- Independent extension: 2,000 databases, 11,550 sanitizer runs, zero failures.
- Combined: 64,928 brute-force itemset comparisons; 4,455,376 period-update
  comparisons; 190,403,048 channel-formula checks; 12,720 sanitizer runs.
- Strict canonical layout/hash validation: eight large datasets, zero errors.
- Eight-dataset smoke pipeline: 40 result rows, zero validation errors.
- Resume/checkpoint test: all eight completed smoke configurations reused and
  merged without recomputation.

## Defects found and corrected

1. The one-cell action-set formula omitted the unit decrement. The proved set
   is now `{1} union {utility breakpoints} union {deletion}` under its explicit
   label-based, loss-tie-break assumptions.
2. AGSA did not generate non-sensitive PHUI breakpoints that can change its MC
   score. Candidate generation now includes every affected currently published
   PHUI while the exact original-HUI hard guard remains unchanged.
3. Using a non-event unit decrement as MPH progress caused loops proportional
   to raw quantities. It is now considered only when it directly hides; event
   breakpoints/deletion provide non-hiding progress.
4. Threshold and objective-weight domains were insufficiently validated.
   Invalid, inverted, non-finite, or negative configurations are now rejected.
5. Duplicate profit keys were silently overwritten. They are now rejected.
6. Strict data validation found 80 Liquor_11 transactions with repeated item
   tokens. Preparation now sums repeated quantities into one canonical cell.
7. Long experiment runs had no reliable resume behavior. Completed per-config
   result files are now validated and reused as checkpoints.

## Dataset representation

Every experiment dataset has:

- `item_count.txt`: one fixed-TID transaction per line, `item:quantity` cells;
- `item_utility.txt`: one `item:profit` per line.

Foodmart, Retail, Mushroom, and Liquor_11 use an upstream/prepared quantitative
factorization. For ECommerce, Fruithut, Yoochoose-buy, and Chainstore, SPMF
publishes internal utility per occurrence rather than a unique physical
quantity/profit decomposition. The prepared representation uses
`quantity=positive internal utility` and `profit=1`. It is exactly
utility-preserving for HUIM/PHUIM but must not be interpreted as physical units.

## Publication status

The correctness and reproducibility evidence is complete for the declared
small positive-utility fixed-timeline domain, and the large-data pipeline is
operational. The full `ready_paper.csv` and `ready_scaling.csv` matrices have
not yet been completed; therefore the smoke results must not be presented as a
final comparative ranking. Run the registered matrices to completion, retain
all checkpoints/logs, and use the generated summary CSV, LaTeX table, and PDF
figures for the final empirical section.
