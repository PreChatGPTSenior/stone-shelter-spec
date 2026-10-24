"""Deterministic admission: actual analyzers, selected tests, monitored services and browser evidence."""
from __future__ import annotations
import json,os,signal,subprocess,sys,time,xml.etree.ElementTree as ET
from pathlib import Path
from .core import ROOT,Blocked,identity,json_read,json_write,sha,command
from .environment import probe,runtime_env
from .test_environment import handshake
sys.path.insert(0,str(ROOT/'tools'))
import static_analysis
from check_spring_ai import check as check_ai

def report(run,stage,task,candidate,artifacts,**extra):
 value={'stage':stage,'task':task['id'],'candidate':candidate,'mode':'application','status':'PASS','artifact_hashes':{str(Path(p).absolute()):sha(p) for p in artifacts},**extra}
 json_write(run/(stage+'.json'),value);return value

def verify_artifacts(reports):
 for r in reports.values():
  for p,h in r['artifact_hashes'].items():
   if sha(p)!=h:raise Blocked('Changed evidence artifact')

def retain_static_artifacts(run,paths,expected=None):
 # Analyzer caches are disposable and outside audit backups. Preserve verified
 # bytes before admitting the stage, and hash these durable copies in its report.
 expected=expected or {str(p):sha(p) for p in paths}
 folder=run/'static-evidence';folder.mkdir(mode=0o700)
 saved=[]
 for i,source in enumerate(paths):
  target=folder/(str(i)+'-'+Path(source).name)
  with target.open('xb') as stream:
   os.chmod(target,0o600);stream.write(Path(source).read_bytes());stream.flush();os.fsync(stream.fileno())
  if sha(target)!=expected[str(source)]:raise Blocked('Analyzer evidence changed while retaining audit copy')
  saved.append(target)
 descriptor=os.open(folder,os.O_RDONLY)
 try:os.fsync(descriptor)
 finally:os.close(descriptor)
 return saved

def monitored(argv,cwd,env,stack,task,run,name,timeout=1800):
 before=probe(stack,task,env);json_write(run/(name+'-environment-before.json'),before)
 log=run/(name+'.log');deadline=time.monotonic()+timeout;observations=[];test_checks=None
 manifest=env.get('STONE_TEST_ENVIRONMENT_MANIFEST')
 with log.open('w') as stream:
  process=subprocess.Popen(argv,cwd=cwd,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
  try:
   while process.poll() is None:
    if time.monotonic()>deadline:raise Blocked('Test timeout: '+name)
    if manifest and not Path(manifest+'.completed').is_file():
     observed=handshake(manifest,task['id'],env['STONE_TEST_CANDIDATE'],env['STONE_TEST_RUN_ID'])
     if observed:test_checks=observed
    observations.append({'at':time.time(),'checks':probe(stack,task,env),'actual_test_resources':test_checks})
    # Bound polling; never hide a failure behind retries.
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:pass
   code=process.returncode
   after=probe(stack,task,env)
   if manifest and not test_checks:raise Blocked('The test JVM did not complete the required readiness handshake')
   if manifest:
    # The test extension keeps probes alive until the final acknowledgement; a separate after report is required.
    final=json_read(manifest+'.completed')
    if final.get('run_id')!=env['STONE_TEST_RUN_ID'] or final.get('candidate')!=env['STONE_TEST_CANDIDATE'] or final.get('status')!='READY' or final.get('generation')!=test_checks['generation'] or not final.get('checks') or any(c.get('status')!='READY' for c in final['checks']):raise Blocked('Missing/failed final actual test-resource check')
  except BaseException:
   if process.poll() is None:
    os.killpg(process.pid,signal.SIGTERM)
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
   json_write(run/(name+'-environment-failure.json'),{'status':'BLOCKED_ENVIRONMENT','observations':observations,'observed_process_exit':process.returncode})
   raise
 json_write(run/(name+'-environment-after.json'),{'checks':after,'observations':observations,'exit':code})
 if code:raise Blocked('Selected verification failed: '+name+'; inspect private local log')
 return log

def junit(path):
 root=ET.parse(path).getroot();suites=[root] if root.tag=='testsuite' else list(root.iter('testsuite'))
 # Count leaf suites only, avoiding duplicate aggregate counters.
 suites=[s for s in suites if not list(s.findall('testsuite'))]
 counts={k:sum(int(s.attrib.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
 if counts['tests']<1 or any(counts[k] for k in ['failures','errors','skipped']):raise Blocked('Empty, failed or skipped test result')
 return counts['tests']

def static_gate(task,tree,run,env,candidate,changed):
 release=any(c['adapter']=='release' for c in task['checks'])
 java=release or any(n.startswith('stone-shelter-api/') and n.endswith('.java') for n in changed)
 react=release or any(n.startswith('stone-shelter-ui/') and n.endswith(('.ts','.tsx','.js','.jsx')) for n in changed)
 raw=[]
 feature=int(task['id'][1:5]);number=int(task['id'][-3:])
 ai_stage='rag' if feature>=7 or (feature==6 and number>=5) else 'chat' if feature==6 and number>=2 else 'embedding' if feature>=5 and task['kind']!='readiness' else None
 if ai_stage:
  evidence=run/'spring-ai-policy.json';json_write(evidence,check_ai(tree,ai_stage));raw.append(evidence)
 if java or react:
  packet=run/'static-packet.json';json_write(packet,{'schema':1,'mode':'application','task_ids':[task['id']],'source_root':str(tree),'java_roots':['stone-shelter-api/src/main/java','stone-shelter-api/src/test/java'] if java else [],'react_roots':['stone-shelter-ui/src'] if react else []})
  result,path=static_analysis.run_gate(packet,env.get('JAVA_HOME'))
  if result['status']!='PASS':raise Blocked('Checkstyle/PMD/ESLint admission failed; report '+str(path))
  sources=[path]+[Path(c['report']) for c in result['checks'].values()]
  expected={str(p):sha(p) for p in sources}
  static_analysis.verify_handoff(packet,path,env.get('JAVA_HOME'));raw.append(packet)
  raw += retain_static_artifacts(run,sources,expected)
 if react:
  for label,argv in [('typecheck',['npm','exec','--','tsc','--noEmit']),('build',['npm','run','build'])]:
   r=subprocess.run(argv,cwd=tree/'stone-shelter-ui',env=env,capture_output=True,text=True,timeout=300)
   log=run/(label+'.log');log.write_text(r.stdout+r.stderr);raw.append(log)
   if r.returncode:raise Blocked('Frontend '+label+' failed')
 if not raw:
  p=run/'static-scope.json';json_write(p,{'status':'PASS','reason':'No Java/React source changed; policy, package and scope validation remain required','changed':changed});raw=[p]
 if identity(tree)!=candidate:raise Blocked('Source changed during static checks')
 return report(run,'static',task,candidate,raw)

def selected_tests(task,tree,run,env,stack,candidate):
 raw=[];total=0
 for i,check in enumerate(task['checks']):
  adapter=check['adapter'];selector=check['selector'];name='test-'+str(i)
  if adapter=='package':
   log=monitored([sys.executable,str(ROOT/'tools/validate_execution.py')],ROOT,env,stack,task,run,name)
   raw.append(log);total+=1
  elif adapter=='maven':
   paths=list((tree/'stone-shelter-api/src/test/java').rglob(selector+'.java'))
   if not paths:raise Blocked('Required test class not implemented: '+selector)
   reports=tree/'stone-shelter-api/target/surefire-reports'
   old={p:sha(p) for p in reports.glob('TEST-*.xml')} if reports.exists() else {}
   started=time.time()
   test_env=dict(env)
   if selector!='ArchitectureTest':
    test_env.update(STONE_TEST_ENVIRONMENT_MANIFEST=str(run/(name+'-actual-environment.json')),STONE_TEST_RUN_ID=run.name+'-'+name,STONE_TEST_TASK=task['id'],STONE_TEST_CANDIDATE=candidate)
   raw.append(monitored(['./mvnw','-B','-Dtest='+selector,'-Dsurefire.failIfNoSpecifiedTests=true','test'],tree/'stone-shelter-api',test_env,stack,task,run,name))
   selected=[p for p in reports.glob('TEST-*'+selector+'*.xml') if p.stat().st_mtime>=started-1]
   if not selected:raise Blocked('No fresh selected JUnit result')
   for p in selected:
    total+=junit(p);saved=run/(name+'-'+p.name);saved.write_bytes(p.read_bytes());raw.append(saved)
  elif adapter=='vitest':
   if not (tree/'stone-shelter-ui'/selector).is_file():raise Blocked('Required UI test not implemented: '+selector)
   output=run/(name+'-vitest.xml')
   raw.append(monitored(['npm','exec','--','vitest','run',selector,'--reporter=junit','--outputFile='+str(output)],tree/'stone-shelter-ui',env,stack,task,run,name))
   total+=junit(output);raw.append(output)
  elif adapter=='browser':
   # Actual browser verification is run after independent test review; this checks charter structure.
   charter=json_read(tree/task['browser_charter'])
   if not charter.get('scenarios'):raise Blocked('Missing browser charter')
   p=run/(name+'-charter.json');json_write(p,charter);raw.append(p);total+=1
  elif adapter=='release':
   # Release tasks explicitly authorize the full suites. No other task uses these selectors.
   release_started=time.time()
   for label,argv,folder in [('backend',['./mvnw','-B','verify'],'stone-shelter-api'),('frontend',['npm','exec','--','vitest','run','--reporter=junit','--outputFile='+str(run/'release-ui.xml')],'stone-shelter-ui')]:
    release_env=dict(env)
    if label=='backend':release_env.update(STONE_TEST_ENVIRONMENT_MANIFEST=str(run/'release-actual-environment.json'),STONE_TEST_RUN_ID=run.name+'-release',STONE_TEST_TASK=task['id'],STONE_TEST_CANDIDATE=candidate)
    raw.append(monitored(argv,tree/folder,release_env,stack,task,run,'release-'+label,timeout=3600))
   xml=[p for p in (tree/'stone-shelter-api/target/surefire-reports').glob('TEST-*.xml') if p.stat().st_mtime>=release_started-1]
   if not xml:raise Blocked('No fresh full backend regression reports')
   xml+=[run/'release-ui.xml']
   for p in xml:total+=junit(p);copy=run/('release-'+p.name);copy.write_bytes(p.read_bytes());raw.append(copy)
   # The Sonar adapter must obtain a current server-side gate for this candidate.
   sonar=ROOT/'tools/sonar_application.py'
   raw.append(monitored([sys.executable,str(sonar),'--source',str(tree),'--candidate',candidate,'--output',str(run/'sonar.json')],ROOT,env,stack,task,run,'sonar',timeout=1800))
   result=json_read(run/'sonar.json')
   if result.get('candidate')!=candidate or result.get('quality_gate')!='OK':raise Blocked('Sonar gate not OK')
   raw.append(run/'sonar.json')
  else:raise Blocked('Unknown test adapter')
 if not raw:raise Blocked('No selected verification')
 if identity(tree)!=candidate:raise Blocked('Source changed during tests')
 return report(run,'tests',task,candidate,raw,tests=total)

def browser(task,tree,run,env,stack,candidate):
 if not task['browser_required']:
  p=run/'technical-charter.json';json_write(p,{'task':task['id'],'type':'technical-only','reason':task['browser_policy'],'deliverables':task['deliverables']})
  return report(run,'browser',task,candidate,[p],assertions=0,not_applicable_reason=task['browser_policy'])
 suite=tree/'quality/e2e'/task['id']
 if not suite.is_dir() or not list(suite.glob('*.spec.ts')):raise Blocked('Task Playwright suite is not implemented: '+task['id'])
 browser_env=dict(env,TASK_TEST_DIR=str(suite),UI_URL=stack['ui'],API_URL=stack['api'],PLAYWRIGHT_JUNIT_OUTPUT_FILE=str(run/'browser.xml'),TASK_EVIDENCE_DIR=str(run/'browser-artifacts'),STONE_TEST_STACK_PROJECT=stack['project'],STONE_TEST_STACK_ENV_FILE=stack['env_file'])
 log=monitored(['node',str(ROOT/'quality/browser/node_modules/@playwright/test/cli.js'),'test',str(suite),'--config',str(ROOT/'quality/playwright.config.mjs'),'--reporter=junit'],tree,browser_env,stack,task,run,'browser')
 count=junit(run/'browser.xml')
 artifacts=[log,run/'browser.xml']+list((run/'browser-artifacts').rglob('*.zip'))
 if not any(p.suffix=='.zip' for p in artifacts):raise Blocked('Missing Playwright trace')
 if identity(tree)!=candidate:raise Blocked('Source changed during browser verification')
 return report(run,'browser',task,candidate,artifacts,assertions=count)
