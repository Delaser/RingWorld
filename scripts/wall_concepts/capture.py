from pathlib import Path
import subprocess,time,json,argparse
parser=argparse.ArgumentParser()
parser.add_argument("--pid",required=True)
args=parser.parse_args()
root=Path.cwd();out=root/'logs/wall-concept-comparison';raw=root/'logs/industrial-wall-study/run/screenshots'
java='/Users/chris/.local/jdks/jdk-25.0.4+7/Contents/Home/bin/java'
def action(s):
 subprocess.run([java,'--add-modules','jdk.attach','-cp','logs/village-lighting-comparison','VillageProbe2',args.pid,str(root/'logs/village-lighting-comparison/probe2.jar'),s],check=True)
assert 'READY 4' in (out/'setup-v2.txt').read_text()
entries=[];names=['Original','Repair history','Service routes','Staggered courses','Weathered joints']
action('tick freeze;hide;fov=60;weather clear')
try:
 for view in ['front','angled']:
  for phase,clock in [('day','noon'),('night','midnight')]:
   action('time set '+clock)
   for index,name in enumerate(names):
    origin=4096+index*128
    pose=f'{origin+48} 152 70 180 0' if view=='front' else f'{origin+100} 152 55 133.3 0'
    action('tp @s '+pose);time.sleep(3)
    id=f'concept-{index}-{view}-{phase}'
    action('capture='+id);time.sleep(1)
    assert (raw/(id+'.png')).exists()
    entries.append(dict(id=id,index=index,title=name,view=view,phase=phase,pose=pose,decay=40))
    (out/'gallery.json').write_text(json.dumps(entries,indent=2)+'\n')
    print('CAPTURED',id,flush=True)
finally:action('tick unfreeze;time set noon;tp @s 4272 152 70 180 0')
