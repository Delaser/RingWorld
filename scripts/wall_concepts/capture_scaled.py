from pathlib import Path
import subprocess,time,json,argparse
parser=argparse.ArgumentParser();parser.add_argument('--pid',required=True);args=parser.parse_args()
root=Path.cwd();out=root/'logs/wall-elements-scale'
origins=[4160,4352,4416,4480,4544,4608,4672,4736]
names=['Buttress','Expansion joint','Drainage outlet','Ventilation bank','Maintenance gallery','Service shaft','Exposed machinery','Braced breach']
def action(s):
 subprocess.run(['/Users/chris/.local/jdks/jdk-25.0.4+7/Contents/Home/bin/java','--add-modules','jdk.attach','-cp','logs/village-lighting-comparison','VillageProbe2',args.pid,str(root/'logs/village-lighting-comparison/probe2.jar'),s],check=True)
assert 'READY 7' in (out/'setup2.txt').read_text()
entries=[];action('tick freeze;hide;weather clear;time set noon;fov=70')
try:
 for index,(origin,name) in enumerate(zip(origins,names)):
  for view in ['front','angled']:
   pose=f'{origin+32} 80 -88 180 0' if view=='front' else f'{origin+50} 82 -92 149 3'
   action('tp @s '+pose);time.sleep(5)
   id=f'scaled-{index}-{view}';action('capture='+id);time.sleep(1)
   entries.append(dict(id=id,index=index,title=name,view=view,pose=pose,exposedHeight=33))
   (out/'gallery.json').write_text(json.dumps(entries,indent=2)+'\n');print('CAPTURED',id,flush=True)
finally:action('tick unfreeze;tp @s 4192 80 -88 180 0')
