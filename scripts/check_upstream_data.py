#!/usr/bin/env python3
import csv, os, sys
root=os.environ.get('PPUM_ROOT') or (sys.argv[1] if len(sys.argv)>1 else '')
if not root: raise SystemExit('Set PPUM_ROOT or pass repository root as argument')
ok=True
for r in csv.DictReader(open('datasets/dataset_manifest.csv',encoding='utf-8')):
    for k in ('item_count_rel','item_utility_rel'):
        p=os.path.join(root,r[k]); exists=os.path.isfile(p); print(('OK  ' if exists else 'MISS'),r['dataset'],k,p); ok &= exists
if not ok: raise SystemExit(2)
