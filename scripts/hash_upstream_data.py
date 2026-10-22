#!/usr/bin/env python3
import csv, hashlib, os, sys
root=os.environ.get('PPUM_ROOT') or (sys.argv[1] if len(sys.argv)>1 else '')
out=sys.argv[2] if len(sys.argv)>2 else 'results/upstream_data_hashes.csv'
if not root: raise SystemExit('Set PPUM_ROOT or pass root as first argument')
os.makedirs(os.path.dirname(out) or '.',exist_ok=True)
def sha(p):
 h=hashlib.sha256()
 with open(p,'rb') as f:
  for b in iter(lambda:f.read(1<<20),b''):h.update(b)
 return h.hexdigest()
rows=[]
for r in csv.DictReader(open('datasets/dataset_manifest.csv',encoding='utf-8')):
 for kind in ('item_count_rel','item_utility_rel'):
  p=os.path.join(root,r[kind]);rows.append(dict(dataset=r['dataset'],kind=kind,path=os.path.abspath(p),bytes=os.path.getsize(p) if os.path.exists(p) else '',sha256=sha(p) if os.path.exists(p) else 'MISSING'))
with open(out,'w',newline='',encoding='utf-8') as f:
 w=csv.DictWriter(f,fieldnames=['dataset','kind','path','bytes','sha256']);w.writeheader();w.writerows(rows)
print(out)
