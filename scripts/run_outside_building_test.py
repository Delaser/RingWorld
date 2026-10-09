#!/usr/bin/env python3
"""Run only the opt-in #255 fixture; requires an already accepted disposable EULA.

The standard prepare tasks reset their ignored multiplayer worlds. Do not run
another build, game or fixture against this checkout at the same time.
"""
import os,sys,subprocess,time,pathlib,shutil
root=pathlib.Path(__file__).resolve().parents[1]
if len(sys.argv) != 3 or sys.argv[1] not in {'261','262','263'} or sys.argv[2] not in {'fabric','neoforge'}:
 raise SystemExit('Usage: python3 scripts/run_outside_building_test.py {261|262|263} {fabric|neoforge}')
version,loader=sys.argv[1:3]
pins={'261':[], '262':['-Pminecraft_version=26.2','-Ploader_version=0.19.3','-Pfabric_api_version=0.158.0+26.2','-Pneoforge_version=26.2.0.69','-Pmoddevgradle_version=2.0.144'], '263':['-Pminecraft_version=26.3','-Ploader_version=0.19.5','-Pfabric_api_version=0.160.5+26.3','-Pneoforge_version=26.3.0.7-beta','-Pmoddevgradle_version=2.0.147']}[version]
env=dict(os.environ)
env['JAVA_TOOL_OPTIONS']=(env.get('JAVA_TOOL_OPTIONS','')+' -Dringworld.outsideBuildingTest=true -Dringworld.backgroundTestWindow=true -Dringworld.multiplayerTestViewDistanceChunks=5').strip()
base_java_options=env['JAVA_TOOL_OPTIONS']
prefix=':neoforge:' if loader=='neoforge' else ':'
base=['./gradlew',*pins,'-PringMultiplayerWidthBlocks=128','--max-workers=1','--console=plain']
procs=[]
logs={}
def launch(role,task):
 path=root/'logs/outside-building-255'/f'{version}-{loader}'/(role+'.log');path.parent.mkdir(parents=True,exist_ok=True);logs[role]=path
 f=path.open('w');p=subprocess.Popen([*base,prefix+task],cwd=root,env=env,stdout=f,stderr=subprocess.STDOUT);procs.append((p,f));return p
def wait_for(role,needle,seconds):
 end=time.time()+seconds
 while time.time()<end:
  value=logs[role].read_text(errors='replace')
  if needle in value:return
  if any(x in value for x in ['BUILD FAILED','Encountered an unexpected exception','result=false timeout']):raise RuntimeError(role+' failed; '+str(logs[role]))
  time.sleep(1)
 raise RuntimeError(role+' timeout waiting '+needle)
try:
 for resume in [False, True]:
  procs.clear(); logs.clear()
  env['JAVA_TOOL_OPTIONS'] = base_java_options + ' -Dringworld.outsideBuildingResume=' + str(resume).lower()
  phase='reopen' if resume else 'fresh'
  original_base=base
  if resume:
   prepare='prepareNeoForgeMultiplayerHarness' if loader=='neoforge' else 'prepareMultiplayerHarness'
   base=[*original_base,'-x',prefix+prepare]
  launch('server-'+phase,'runMultiplayerServer');wait_for('server-'+phase, 'Done (',180);print(phase+' server ready',flush=True)
  runtime=root/('neoforge/run-multiplayer' if loader=='neoforge' else 'run-multiplayer')
  for role in ['client-a','client-b']:
   (runtime/role/'options.txt').write_text('onboardAccessibility:false\nsoundCategory_master:0.0\nmaxFps:40\n')
  launch('a-'+phase,'runMultiplayerClientA');wait_for('a-'+phase,'client world fully loaded',180);print(phase+' client A ready',flush=True)
  launch('b-'+phase,'runMultiplayerClientB');wait_for('b-'+phase,'client world fully loaded',180);print(phase+' client B ready',flush=True)
  wait_for('server-'+phase,'[outside-building] result=true resume='+str(resume).lower(),300)
  print(phase+' scenario PASS '+version+' '+loader,flush=True)
  for p,f in procs:
   try:p.wait(timeout=45)
   except subprocess.TimeoutExpired:raise RuntimeError("Process did not stop normally")
   if p.returncode != 0:raise RuntimeError("Nonzero process exit "+str(p.returncode))
   f.close()
  out=root/'logs/outside-building-255'/f'{version}-{loader}'/phase;out.mkdir(parents=True,exist_ok=True)
  runtime=root/('neoforge/run-multiplayer' if loader=='neoforge' else 'run-multiplayer')
  for role in ['client-a','client-b']:
   shutil.copytree(runtime/role/'screenshots',out/role,dirs_exist_ok=True)
  print('PASS '+version+' '+loader+' '+phase+' clean process stops and evidence archived',flush=True)
  base=original_base
finally:
 # Cancel only the child Gradle processes created by this invocation.
 for p,f in procs:
  if p.poll() is None:p.terminate()
  f.close()
