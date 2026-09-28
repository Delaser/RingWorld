from pathlib import Path
from PIL import Image
import json,zipfile,io,base64,html,subprocess
out=Path('logs/wall-elements-scale').resolve()
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
assert len(entries)==16
specs=['5 wide | 31 high | projects 3','3 wide | 33 high | recessed 3','5 x 5 opening | recessed 3','15 x 7 grille | recessed 2','37 long | ledge projects 3','9 wide | 29 high | projects 2','21 x 17 frame | recessed 3','Braced opening | up to 24 high']
page='<!doctype html><meta charset="utf-8"><title>Wall elements at actual scale</title><style>body{max-width:1600px;margin:24px auto;padding:0 20px;background:#111820;color:#eef3f8;font:17px system-ui}img{width:100%}a{color:#acd9ff}section{display:grid;grid-template-columns:1fr 1fr;gap:12px}</style><h1>Wall elements at actual scale</h1><p>Eight samples on the existing shoreline wall. Top Y95; shoreline Y63: 33 exposed blocks. Original Industrial palette. Front left, angled right. Built in the copied test world; no raised test panels.</p>'
overview='<rect width="2560" height="3488" fill="#111820"/>'
for e in entries:
 if e['view']!='front':continue
 x=e['index']%2*1280;y=e['index']//2*872
 overview+=label(e['title'],'Existing wall | 33 exposed blocks',x+24,y+12,3)+embed(raw/(e['id']+'.png'),x,y+72,1280,800)
save('overview',2560,3488,overview)
page+='<a href="overview.png"><img src="overview.png"></a>'
for index in range(8):
 pair=[next(e for e in entries if e['index']==index and e['view']==v) for v in ['front','angled']]
 body='<rect width="2560" height="872" fill="#111820"/>'
 page+=f'<h2>{pair[0]["title"]}</h2><p>{specs[index]} blocks.</p><section>'
 for col,e in enumerate(pair):
  p=raw/(e['id']+'.png')
  save(e['id']+'-labelled',2560,1672,'<rect width="2560" height="1672" fill="#111820"/>'+label(e['title'],e['view']+' | existing wall: 33 blocks exposed',24,12,3)+embed(p,0,72,2560,1600))
  body+=label(e['title'],e['view']+' | 33-block exposed wall',col*1280+24,12,3)+embed(p,col*1280,72,1280,800)
  page+=f'<a href="{e["id"]}-labelled.png"><img src="{e["id"]}-labelled.png"></a>'
 save('element-'+str(index)+'-pair',2560,872,body)
 page+='</section>'
page+='<p>Actual world settings: minimum Y -64, configured wall height 160, exclusive wall top 96. Visible height varies with terrain; the chosen shoreline sites share surface height 63. These are authored visual samples, not functional drains/doors or a promoted procedural generator. Daylight, FOV70, hidden HUD; labels added after capture.</p>'
(out/'index.html').write_text(page)
