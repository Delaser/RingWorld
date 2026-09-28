from pathlib import Path
from PIL import Image
import json,zipfile,io,base64,html,subprocess
out=Path('logs/large-wall-concept').resolve()
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
page='<!doctype html><meta charset="utf-8"><title>Large wall structure concept</title><style>body{max-width:1600px;margin:24px auto;padding:0 20px;background:#111820;color:#eef3f8;font:17px system-ui}img{width:100%}a{color:#acd9ff}</style><h1>Large-scale wall structure</h1><p>384-block study: quiet armour sections, reinforced joints, a 56-block sealed gate and a small maintenance entrance. Original Industrial palette; approved 10% decay. An authored scale study in the copied world, not a new production generator.</p>'
for e in entries:
 save(e['id']+'-labelled',2560,1672,'<rect width="2560" height="1672" fill="#111820"/>'+label(e['title'],'Original palette | armour, joints, recessed entrances',24,12,3)+embed(raw/(e['id']+'.png'),0,72,2560,1600))
 page+=f'<h2>{e["title"]}</h2><a href="{e["id"]}-labelled.png"><img src="{e["id"]}-labelled.png"></a>'
page+='<p>Real Minecraft blocks. Gate and hatch recess two blocks. Labels added after capture with Minecraft font. Lights are deliberately concentrated at entrances. Sample is elevated for inspection; full-ring placement and distant Atlas material matching are not implemented.</p>'
(out/'index.html').write_text(page)
