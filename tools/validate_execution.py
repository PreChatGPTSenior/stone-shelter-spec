#!/usr/bin/env python3
"""Validate task DAG, traceability, paths, selectors, templates and package readiness."""
import json,re,sys
from pathlib import Path
from execution.core import ROOT,Blocked,safe,tasks
from execution.model_policy import load as load_models
from execution.budgets import load as load_budgets

def validate(all_tasks=None):
 ts=all_tasks if all_tasks is not None else tasks();by={t['id']:t for t in ts}
 if len(ts)!=len(by):raise Blocked('Duplicate task ID')
 baseline=json.loads((ROOT/'product-baseline/specification.json').read_text())
 requirements={r['id'] for s in baseline['sections'] for r in s['requirements']};acceptance={a['id'] for a in baseline['acceptance']};requirements.add('AI001')
 covered=set();scenarios=set();seen=set();remaining=set(by)
 for t in ts:
  if not re.fullmatch(r'T\d{4}-\d{3}',t['id']):raise Blocked('Invalid task ID')
  if not set(t['depends_on'])<=set(by) or t['id'] in t['depends_on']:raise Blocked('Invalid dependency')
  if not set(t['requirements'])<=requirements or not set(t['acceptance'])<=acceptance:raise Blocked('Unknown traceability ID')
  covered.update(t['requirements']);scenarios.update(t['acceptance'])
  if t['kind']!='readiness' and (not t['write_paths'] or not t['checks'] or not t['deliverables']):raise Blocked('Incomplete implementation packet')
  for pattern in t['write_paths']:
   safe(ROOT,pattern)
   if pattern.startswith(('product-baseline/','reference/','provenance/','.git/','.execution/')):raise Blocked('Immutable/control path is writable')
  for c in t['checks']:
   if c['adapter']=='maven' and not re.fullmatch(r'[A-Za-z][A-Za-z0-9]*Test',c['selector']):raise Blocked('Unsafe Maven selector')
   if c['adapter']=='vitest' and not re.fullmatch(r'src/[A-Za-z0-9_./-]+\.test\.tsx?',c['selector']):raise Blocked('Unsafe Vitest selector')
  for name in t['read_first']:
   if not safe(ROOT,name).is_file():raise Blocked('Missing instruction: '+name)
  if t['browser_required'] and not safe(ROOT,t['browser_charter']).is_file():raise Blocked('Missing browser charter')
 for change in {t['change'] for t in ts}:
  group=[t for t in ts if t['change']==change]
  if group[0]['kind']!='readiness':raise Blocked('Readiness ticket must be first')
 while remaining:
  ready={tid for tid in remaining if set(by[tid]['depends_on'])<=seen}
  if not ready:raise Blocked('Dependency cycle')
  seen|=ready;remaining-=ready
 if covered!=requirements:raise Blocked('Unowned requirements: '+','.join(sorted(requirements-covered)))
 if scenarios!=acceptance:raise Blocked('Unowned acceptance scenarios')
 for stage in ['developer','code_review','test_engineer','test_review','browser_planner','browser_review']:
  if not (ROOT/'execution/prompts'/f'{stage}.md').is_file():raise Blocked('Missing role prompt')
 load_models(by)
 load_budgets()
 return {'status':'PASS','tasks':len(ts),'changes':len({t['change'] for t in ts}),'requirements':len(requirements),'acceptance_scenarios':len(acceptance),'meaning':'Execution package structure verified; application implementation and acceptance NOT_RUN'}
if __name__=='__main__':
 try:print(json.dumps(validate(),indent=2))
 except (Blocked,OSError,KeyError,ValueError) as e:print(json.dumps({'status':'FAIL','reason':str(e)}));sys.exit(1)
