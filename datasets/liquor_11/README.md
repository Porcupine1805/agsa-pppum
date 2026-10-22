# Liquor_11

Public source: [SPMF liquor_11.txt](https://www.philippe-fournier-viger.com/spmf/publicdatasets/liquor_11.txt).

Conversion used in the paper:

1. Keep a transaction if its **original item count** (before dropping non-positive quantities) is at most 5.
2. Write `item:qty` lines and unit profits in cents.

Result: 9283 non-empty transactions, 2626 items, average length 2.7021.

This matches Zhou et al.'s Liquor_11 table (9,284 / 2,626 / 2.70) to rounding.
It is not the PPUM copy `liquor.txt` (52,131 transactions).

SHA-256:

- `item_count.txt` `7fb9ab97aa36cadf4c64d98f9f0790ee71ccac4064897c6b26a88b36d12563fa`
- `item_utility.txt` `e8ffc0f68f660376b3c1903a1d70b51d17c2c157dff932da47508123f9b10943`
