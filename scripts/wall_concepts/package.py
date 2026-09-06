from pathlib import Path
from PIL import Image
import json,zipfile,io,base64,html,subprocess
out=Path('logs/wall-concept-comparison').resolve()
raw=Path('logs/industrial-wall-study/run/screenshots').resolve()
with zipfile.ZipFile('/Users/chris/.gradle/caches/fabric-loom/26.2/minecraft-client.jar') as z:
 font=next(p for p in json.loads(z.read('assets/minecraft/font/include/default.json'))['providers'] if p.get('file')=='minecraft:font/ascii.png')
 bitmap=Image.open(io.BytesIO(z.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
 rows=font['chars'];cw=bitmap.width//len(rows[0]);ch=bitmap.height//len(rows);glyphs={}
 for y,row in enumerate(rows):
  for x,c in enumerate(row):
   pts=[(i,j) for j in range(ch) for i in range(cw) if bitmap.getpixel((x*cw+i,y*ch+j))[3]>0]
   glyphs[c]=(pts,max((i for i,j in pts),default=2)+1)
def path(text,x,y,s):
 a=[]
 for c in text:
  if c==' ':x+=4*s;continue
  pts,w=glyphs[c]
  a.extend(f'M{x+i*s},{y+j*s}h{s}v{s}h-{s}z' for i,j in pts);x+=(w+1)*s
 return ''.join(a)
def embed(p,x,y,w,h):
 b=base64.b64encode(p.read_bytes()).decode();return f'<image x="{x}" y="{y}" width="{w}" height="{h}" href="data:image/png;base64,{b}"/>'
def label(title,subtitle,x,y,s):
 d=path(title,x,y,s)+path(subtitle,x,y+12*s,max(2,s-1))
 return f'<path d="{d}" fill="black" transform="translate({s},{s})"/><path d="{d}" fill="white"/>'
def save(name,w,h,body):
 p=out/(name+'.svg');p.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}">{body}</svg>')
 subprocess.run(['sips','-s','format','png',str(p),'--out',str(out/(name+'.png'))],check=True,stdout=subprocess.DEVNULL)

entries=json.loads((out/'gallery.json').read_text())
assert len(entries)==20
for e in entries:
 p=raw/(e['id']+'.png')
 save(e['id']+'-labelled',2560,1672,'<rect width="2560" height="1672" fill="#111820"/>'+label(e['title'],e['view']+' | '+e['phase']+' | 40% decay',24,12,3)+embed(p,0,72,2560,1600))
for view in ['front','angled']:
 body='<rect width="2560" height="4360" fill="#111820"/>'
 for e in entries:
  if e['view']!=view:continue
  x=0 if e['phase']=='day' else 1280;y=e['index']*872
  body+=label(e['title'],e['phase']+' | 40% decay',x+24,y+12,3)+embed(raw/(e['id']+'.png'),x,y+72,1280,800)
 save(view+'-comparison',2560,4360,body)
page='<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Industrial wall concepts</title><style>body{max-width:1600px;margin:24px auto;padding:0 20px;background:#111820;color:#eef3f8;font:17px system-ui}img{max-width:100%}section{display:grid;grid-template-columns:1fr 1fr;gap:12px}figure{margin:0}a{color:#acd9ff}</style><h1>Industrial wall concepts</h1><p>Original, Repair history, Service routes, Staggered courses, Weathered joints. Same original palette and 40% approved decay. Day left; night right. Experimental real-block specimens; original world-generation selection remains in place.</p>'
for view in ['front','angled']:
 page+=f'<h2>{view.title()} comparison</h2><a href="{view}-comparison.png"><img src="{view}-comparison.png"></a><h3>Full-size screenshots</h3><section>'
 for index in range(5):
  for phase in ['day','night']:
   e=next(e for e in entries if e['view']==view and e['index']==index and e['phase']==phase)
   page+=f'<figure><a href="{e["id"]}-labelled.png"><img loading="lazy" src="{e["id"]}-labelled.png" alt="{e["title"]} {phase}"></a></figure>'
 page+='</section>'
page+='<p>96 x 48 x 7 blocks per specimen. Fixed relative cameras, FOV 60, hidden HUD, frozen simulation during captures. Minecraft font labels added afterwards. Original material coverage is matched before decay; removed top blocks and identical light positions can slightly alter visible ratios. These samples do not implement an opposite-ring Atlas wall shader. CPU prototype tests are not an FPS benchmark.</p>'
(out/'index.html').write_text(page)
