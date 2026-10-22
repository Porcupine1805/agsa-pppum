# Upstream source/data policy

This artifact was designed to reproduce comparisons against DSI-Lab1/PPUM without embedding third-party repository contents.

- The project contains only our Java implementation, experiment harness, adapter source, configuration files and analysis scripts.
- `scripts/setup_dsi_ppum.*` clones the public upstream repository into `external/DSI-Lab1-PPUM` on the researcher's machine.
- `scripts/build_upstream_pppum.*` compiles the upstream PPPUM classes in place with the thin adapter; it does not rewrite them.
- Real datasets are read from `PPUM_ROOT`. They are not copied into the release ZIP.
- `hash_upstream_data.py` records SHA-256 values for provenance.
- `upstream_commit.txt` records the Git commit used for exact source runs.

This is a conservative reproducibility policy. It is not a legal conclusion about the ownership or permitted uses of the upstream repository. Researchers should comply with the repository authors' terms and the provenance requirements of the datasets they use.
