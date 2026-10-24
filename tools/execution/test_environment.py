"""Bind monitored integration checks to the endpoints actually used by the test JVM."""
import ipaddress,json,urllib.parse,urllib.request
from pathlib import Path
from .core import Blocked,json_read,json_write

def check_manifest(manifest,task,candidate,run_id,fetch=None):
 if manifest.get('schema')!=1 or manifest.get('task')!=task or manifest.get('candidate')!=candidate or manifest.get('run_id')!=run_id:raise Blocked('Test environment manifest identity mismatch')
 if not manifest.get('generation') or not manifest.get('resources'):raise Blocked('Missing actual test resources')
 results=[]
 for item in manifest['resources']:
  url=urllib.parse.urlparse(item['health_url'])
  if url.scheme!='http' or url.hostname not in {'127.0.0.1','::1'} or url.username or url.password:raise Blocked('Test probe must be loopback HTTP without credentials in URL')
  if fetch is None:
   with urllib.request.urlopen(item['health_url'],timeout=3) as response:data=json.load(response)
  else:data=fetch(item['health_url'])
  if data.get('status')!='READY' or data.get('run_id')!=run_id or data.get('generation')!=manifest['generation']:raise Blocked('Actual test resource unhealthy or belongs to another JVM')
  if not data.get('checks') or any(c.get('status')!='READY' for c in data['checks']):raise Blocked('Missing/failed actual dependency checks')
  results.append({'name':item['name'],'health_url':item['health_url'],'checks':data['checks']})
 return results

def handshake(path,task,candidate,run_id):
 p=Path(path)
 if not p.is_file():return None
 manifest=json_read(p);checks=check_manifest(manifest,task,candidate,run_id)
 json_write(str(p)+'.approved',{'schema':1,'run_id':run_id,'generation':manifest['generation'],'candidate':candidate,'status':'READY'})
 return {'generation':manifest['generation'],'checks':checks}
