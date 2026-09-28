from pathlib import Path
import subprocess,time,json,argparse
parser=argparse.ArgumentParser();parser.add_argument('--pid',required=True);args=parser.parse_args()
root=Path.cwd();out=root/'logs/large-wall-concept'
def action(s):
 subprocess.run(['/Users/chris/.local/jdks/jdk-25.0.4+7/Contents/Home/bin/java','--add-modules','jdk.attach','-cp','logs/village-lighting-comparison','VillageProbe2',args.pid,str(root/'logs/village-lighting-comparison/probe2.jar'),s],check=True)
assert 'READY 0' in (out/'setup.txt').read_text()
entries=[]
action('tick freeze;hide;fov=70;weather clear')
try:
 for id,pose,clock,title in [
 ('overview','6336 182 120 180 2','noon','Large structure | 384 blocks'),
 ('gate','6336 164 -8 180 0','noon','Sealed gate | 56 blocks wide'),
 ('overview-night','6336 182 120 180 2','midnight','Large structure | night')]:
  action('time set '+clock+';tp @s '+pose);time.sleep(5);action('capture=large-'+id);time.sleep(1)
  entries.append(dict(id='large-'+id,title=title,pose=pose,clock=clock))
  print('CAPTURED',id,flush=True)
finally:action('tick unfreeze;time set noon;tp @s 6336 182 120 180 2')
(out/'gallery.json').write_text(json.dumps(entries,indent=2)+'\n')
