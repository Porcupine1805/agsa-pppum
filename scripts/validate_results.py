#!/usr/bin/env python3
import csv,sys
from collections import defaultdict

args=sys.argv[1:]
allow='--allow-infeasible' in args
allow_hf='--allow-hf' in args
args=[a for a in args if a not in ('--allow-infeasible','--allow-hf')]
inp=args[0] if args else 'results/real_runs.csv'
rows=list(csv.DictReader(open(inp,encoding='utf-8')))
if not rows:raise SystemExit('No result rows')
key=lambda r:(r['config_id'],r['dataset'],r['replicate'],r['seed'],r['timeline'])
g=defaultdict(list)
for r in rows:g[key(r)].append(r)
errors=[];infeasible=0
for k,rs in g.items():
 hashes={r['sensitive_hash'] for r in rs};
 if len(hashes)!=1:errors.append(f'{k}: algorithms did not use the same sensitive set: {hashes}')
 for r in rs:
  if r['status'] in ('INFEASIBLE','NO_PHUI') and allow:
   infeasible+=1;continue
  if r['status']!='OK':errors.append(f'{k}/{r["algorithm"]}: status={r["status"]}')
  if r['status']=='OK' and int(r['HF'])!=0 and not allow_hf:errors.append(f'{k}/{r["algorithm"]}: HF={r["HF"]}')
  if (r['status']=='OK' and r['timeline']=='fixed' and r['algorithm'].startswith('AGSA')
      and 'NOGUARD' not in r['algorithm'] and int(r['AC'])!=0):
   errors.append(f'{k}/{r["algorithm"]}: hard-guard invariant violated, AC={r["AC"]}')
  if int(r['tx_count'])<=0:errors.append(f'{k}: tx_count invalid')
print(f'groups={len(g)} rows={len(rows)} infeasible={infeasible} errors={len(errors)}')
for e in errors[:100]:print('ERROR',e)
if errors:raise SystemExit(2)
