"""Trusted lifecycle obligations and candidate-bound, relocatable evidence manifests."""
from pathlib import Path
from .core import Blocked, json_read, json_write, sha
from .audit_paths import evidence_path


def context(run, task, candidate):
    return {'task': task, 'candidate': candidate, 'attempt': Path(run).parent.name,
            'run_id': Path(run).name}


def monitor_artifacts(name, jvm=False):
    names=[name+'.log']+[name+'-environment-'+phase+'.json' for phase in ('before','during','after')]
    if jvm:
        names += [name+'-actual-environment.json'+suffix for suffix in ('','.approved','.completed')]
    return names


def monitor_policy(task, stage):
    if stage=='browser': return [('browser',False)] if task.get('browser_required') else []
    if stage!='tests': return []
    names=[]
    for i,check in enumerate(task.get('checks',[])):
        adapter=check['adapter']
        if adapter in ('package','maven','vitest'):
            names.append(('test-'+str(i),adapter=='maven' and check['selector']!='ArchitectureTest'))
        elif adapter=='release':names.extend([('release-backend',True),('release-frontend',False),('sonar',False)])
    return names


def load_artifact(run,name):
    try:return json_read(evidence_path(run,name))
    except (OSError,ValueError,TypeError) as error:raise Blocked('Missing or invalid evidence artifact: '+name) from error


def verify_monitor(run,name,task,candidate,jvm=False):
    body=load_artifact(run,name+'-evidence.json')
    expected=context(run,task['id'],candidate)
    if body.get('schema')!=1 or body.get('state')!='COMPLETED' or any(body.get(k)!=v for k,v in expected.items()):
        raise Blocked('Incomplete or foreign monitor evidence')
    required=monitor_artifacts(name,jvm)
    if set(body.get('artifact_hashes',{}))!=set(required):raise Blocked('Missing mandatory lifecycle artifact')
    generation=body.get('generation')
    if jvm and not generation:raise Blocked('Missing JVM generation')
    for reference,digest in body['artifact_hashes'].items():
        path=evidence_path(run,reference)
        if not path.is_file() or sha(path)!=digest:raise Blocked('Changed lifecycle artifact')
        if reference.endswith('.log'):continue
        value=load_artifact(run,reference)
        if any(value.get(k)!=v for k,v in expected.items()):raise Blocked('Foreign lifecycle artifact')
        if value.get('state')!='COMPLETED':raise Blocked('Incomplete lifecycle artifact')
        if jvm and value.get('generation')!=generation:raise Blocked('Foreign lifecycle generation')
        if '-environment-' in reference:
            expected_components=set(task.get('environment',[]))-{'toolchain'}
            observations=value.get('observations') if reference.endswith('-during.json') else [{'checks':value.get('checks',[])}]
            if not isinstance(observations,list) or not observations:raise Blocked('Missing lifecycle observations')
            for observation in observations:
                checks=observation.get('checks',[])
                names=[item.get('component') for item in checks]
                if (len(names)!=len(set(names)) or not expected_components<=set(names)
                        or any(item.get('status')!='READY' for item in checks)):
                    raise Blocked('Missing/failed lifecycle resource checks')
    if jvm:
        from .test_environment import expected_resources
        resources=expected_resources(task)
        manifest=load_artifact(run,name+'-actual-environment.json')
        names=[item.get('name') for item in manifest.get('resources',[])]
        urls=[item.get('health_url') for item in manifest.get('resources',[])]
        if not resources or len(urls)!=len(set(urls)) or len(names)!=len(set(names)) or set(names)!=set(resources):
            raise Blocked('Retained JVM resource set mismatch')
        approved=load_artifact(run,name+'-actual-environment.json.approved')
        final=load_artifact(run,name+'-actual-environment.json.completed')
        caps=[item.get('name') for item in final.get('checks',[])]
        if (approved.get('status')!='READY' or final.get('status')!='READY'
                or len(caps)!=len(set(caps)) or set(caps)!={cap for values in resources.values() for cap in values}
                or any(item.get('status')!='READY' for item in final.get('checks',[]))):
            raise Blocked('Retained JVM readiness is incomplete')
    return body


def complete_monitor(run,name,task,candidate,jvm=False,generation=None):
    body={'schema':1,**context(run,task['id'],candidate),'state':'COMPLETED','generation':generation,
          'artifact_hashes':{n:sha(evidence_path(run,n)) for n in monitor_artifacts(name,jvm)}}
    json_write(evidence_path(run,name+'-evidence.json'),body)
    return verify_monitor(run,name,task,candidate,jvm)


def verify_environment(run,task,candidate):
    value=load_artifact(run,'environment-raw.json')
    expected=context(run,task['id'],candidate)
    if (value.get('schema')!=1 or value.get('status')!='READY' or value.get('state')!='COMPLETED'
            or value.get('covered_task_ids')!=[task['id']]
            or any(value.get(k)!=v for k,v in expected.items())):
        raise Blocked('Foreign or incomplete required readiness snapshot')
    required=set(task.get('environment',[]))-{'toolchain'}
    if 'toolchain' in task.get('environment',[]):required.update(('node','npm','python','JAVA_HOME JDK'))
    checks=value.get('checks',[]);names=[item.get('component') for item in checks]
    if (len(names)!=len(set(names)) or not required<=set(names)
            or any(item.get('status')!='READY' for item in checks)):
        raise Blocked('Missing/failed required readiness resources')
    return value


def report_artifacts(run,stage,task,candidate,artifacts):
    refs={}
    if stage=='environment':
        verify_environment(run,task,candidate)
        refs['environment-raw.json']=sha(evidence_path(run,'environment-raw.json'))
    for source in artifacts:
        path=Path(source).absolute()
        # Reject lexical protected names before querying any metadata.
        if any(part.lower()=='engineering-log' for part in path.parts):raise Blocked('Protected artifact')
        try:reference=path.relative_to(Path(run).absolute()).as_posix()
        except ValueError as error:raise Blocked('Artifact outside stage attempt') from error
        refs[reference]=sha(evidence_path(run,reference))
    for name,jvm in monitor_policy(task,stage):
        manifest=verify_monitor(run,name,task,candidate,jvm)
        refs.update(manifest['artifact_hashes'])
        refs[name+'-evidence.json']=sha(evidence_path(run,name+'-evidence.json'))
    if not refs:raise Blocked('Missing stage artifacts')
    return refs


def verify_report(run,report,task,candidate):
    expected=context(run,task['id'],candidate)
    if report.get('status')!='PASS' or any(report.get(k)!=v for k,v in expected.items()):raise Blocked('Foreign or incomplete stage evidence')
    refs=report.get('artifact_hashes',{})
    if not refs:raise Blocked('Missing raw stage evidence')
    if report['stage']=='environment':
        if 'environment-raw.json' not in refs:raise Blocked('Stage omits required readiness snapshot')
        verify_environment(run,task,candidate)
    for name,jvm in monitor_policy(task,report['stage']):
        manifest=verify_monitor(run,name,task,candidate,jvm)
        required=set(monitor_artifacts(name,jvm)+[name+'-evidence.json'])
        if not required<=set(refs):raise Blocked('Stage omits mandatory monitor artifacts')
        if any(refs[n]!=h for n,h in manifest['artifact_hashes'].items()):raise Blocked('Stage differs from monitor evidence')
    for reference,digest in refs.items():
        path=evidence_path(run,reference)
        if not path.is_file() or sha(path)!=digest:raise Blocked('Missing or changed stage artifact')
    return report
