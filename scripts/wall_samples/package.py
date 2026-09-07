"""Package real framebuffer captures as selector-ready assets; requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw
import argparse, hashlib, html, io, json, shutil, subprocess, zipfile
p=argparse.ArgumentParser()
p.add_argument('--raw',type=Path,required=True)
p.add_argument('--minecraft-jar',type=Path,required=True)
p.add_argument('--output',type=Path,default=Path('docs/media/wall-style-samples'))
a=p.parse_args(); out=a.output; out.mkdir(parents=True,exist_ok=True)
for folder in ['fullsize','previews','labelled']: (out/folder).mkdir(exist_ok=True)
palettes=[('weathered','Weathered stone'),('ancient','Ancient masonry'),('natural','Natural rock'),('alloy','Ring alloy'),('industrial','Industrial'),('overgrown','Overgrown ruin'),('monolith','Clean monolith'),('nether','Nether fortress'),('obsidian','Obsidian bastion'),('wood','Timber rampart')]
patterns=[('masonry','Masonry',1),('panels','Panels & ribs',3),('gradient','Gradient',4),('hybrid','Hybrid',5),('engineered','Industrial structures',6)]
with zipfile.ZipFile(a.minecraft_jar) as jar:
    font=next(f for f in json.loads(jar.read('assets/minecraft/font/include/default.json'))['providers'] if f.get('file')=='minecraft:font/ascii.png')
    atlas=Image.open(io.BytesIO(jar.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
    chars=font['chars']; cw=atlas.width//len(chars[0]); ch=atlas.height//len(chars)
    glyphs={c:atlas.crop((x*cw,y*ch,(x+1)*cw,(y+1)*ch)) for y,row in enumerate(chars) for x,c in enumerate(row)}
def label(im,text,x,y,scale=2):
    for c in text:
        if c==' ': x+=4*scale; continue
        glyph=glyphs[c]; box=glyph.getbbox(); width=box[2] if box else 3
        mask=glyph.getchannel('A').resize((cw*scale,ch*scale),Image.Resampling.NEAREST)
        im.paste('#000000',(x+scale,y+scale),mask); im.paste('#ffffff',(x,y),mask)
        x+=(width+1)*scale
entries=[]; sheet=Image.new('RGB',(2400,3260),'#141c24')
for row,(material,material_label) in enumerate(palettes):
    for col,(pattern,pattern_label,pid) in enumerate(patterns):
        name=f'wall-{material}-{pattern}'; source=a.raw/(name+'.png')
        if not source.exists(): source=a.raw/name
        im=Image.open(source).convert('RGB'); assert im.width>=1920 and im.height>=1080
        im.save(out/'fullsize'/(name+'.webp'),quality=90,method=4)
        preview=im.resize((640,round(im.height*640/im.width)),Image.Resampling.LANCZOS)
        preview.save(out/'previews'/(name+'.png'),optimize=True)
        display=im.resize((1280,round(im.height*1280/im.width)),Image.Resampling.LANCZOS)
        card=Image.new('RGB',(1280,display.height+64),'#141c24'); card.paste(display,(0,64))
        label(card,material_label+' / '+pattern_label,20,18,3)
        card.save(out/'labelled'/(name+'.jpg'),quality=94,subsampling=0)
        x=col*480;y=row*326
        label(sheet,material_label,x+12,y+9,2);label(sheet,pattern_label,x+12,y+31,2)
        thumb=im.resize((480,round(im.height*480/im.width)),Image.Resampling.LANCZOS);sheet.paste(thumb,(x,y+54))
        entries.append(dict(id=name,palette=material.upper(),paletteId=row,paletteLabel=material_label,
            pattern=pattern.upper(),patternId=pid,patternLabel=pattern_label,
            raw='fullsize/'+name+'.webp',preview='previews/'+name+'.png',labelled='labelled/'+name+'.jpg',
            size=list(im.size),sourcePngSha256=hashlib.sha256(source.read_bytes()).hexdigest(),sha256=hashlib.sha256((out/'fullsize'/(name+'.webp')).read_bytes()).hexdigest(),
            machinery=material=='industrial' and pattern=='engineered'))
sheet.save(out/'comparison.jpg',quality=94,subsampling=0)
manifest=dict(schemaVersion=1,minecraftVersion='26.2',loader='Fabric',sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
    sourceFixture='RingWallSelectorSamples.java',seed=8128,sourceCircumference=16384,sourceCenterX=1850,sourceY=[64,96],
    specimenSize=[96,33,7],specimenOrigin=[464,192,0],camera=[512.5,206,-32.5,0,0],fov=60,decayPercent=0,time='noon',weather='clear',
    notes=['Actual game blocks, sampled using production material generation. Specimens elevated for an unobstructed view.',
    'Source center chosen to show the generated exposed-machinery motif with Industrial / Industrial structures.',
    'Industrial structures adds geometric elements only with the Industrial palette.',
    'Clustered and Strata remain readable in older saves but are not selectable and are excluded.',
    'Minecraft bitmap-font labels added after capture; previews and raw images contain no labels or HUD.'],entries=entries)
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
page='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>RingWorld wall samples</title>
<style>body{margin:0;background:#10171e;color:#eff3f7;font:16px system-ui}main{max-width:1500px;margin:auto;padding:28px}h1{margin-bottom:8px}p{color:#bdcad6}select,a{color:#d9efff}select{background:#233443;padding:10px;font:inherit;border:1px solid #627789;border-radius:5px}label{display:inline-block;margin:12px 20px 12px 0}#hero{display:block;width:100%;max-height:72vh;object-fit:contain;background:#080c10}#grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:16px}figure{margin:0;background:#1b2732;padding:8px}figure img{width:100%}figcaption{padding:7px 0}button{border:0;padding:0;cursor:pointer;background:none;color:inherit;text-align:left;font:inherit;width:100%}a{margin-right:16px}small{display:block;color:#b9c6d1}</style>
<main><h1>Wall material × pattern</h1><p>50 real Minecraft samples. Matched camera, noon, clear weather, 7-block thickness and 0% decay.</p>
<label>Material <select id="material"></select></label><label>Pattern <select id="pattern"></select></label>
<img id="hero" alt="Selected wall sample"><p id="caption"></p><p><a id="raw">Clean full-size screenshot</a><a id="preview">Selector preview</a><a href="comparison.jpg">All 50 together</a><a href="manifest.json">Asset manifest</a></p>
<p>Industrial structures adds machinery only with the Industrial material. These isolated specimens use the actual generation functions; terrain and weather will affect their appearance in a world.</p><div id="grid"></div></main><script>const entries=DATA;
const material=document.getElementById('material'),pattern=document.getElementById('pattern');
for(const [node,key,label] of [[material,'palette','paletteLabel'],[pattern,'pattern','patternLabel']]){for(const e of entries.filter((e,i,a)=>a.findIndex(v=>v[key]===e[key])===i)){const o=document.createElement('option');o.value=e[key];o.textContent=e[label];node.append(o)}}
function show(){const e=entries.find(e=>e.palette===material.value&&e.pattern===pattern.value);document.getElementById('hero').src=e.labelled;document.getElementById('hero').alt=e.paletteLabel+' / '+e.patternLabel;document.getElementById('caption').textContent=e.paletteLabel+' / '+e.patternLabel;document.getElementById('raw').href=e.raw;document.getElementById('preview').href=e.preview;history.replaceState(null,'','#'+e.id)}
material.onchange=pattern.onchange=show;
for(const e of entries){const f=document.createElement('figure'),b=document.createElement('button'),im=document.createElement('img'),c=document.createElement('figcaption');im.src=e.preview;im.loading='lazy';im.alt=e.paletteLabel+' / '+e.patternLabel;c.textContent=im.alt;b.append(im,c);b.onclick=()=>{material.value=e.palette;pattern.value=e.pattern;show();window.scrollTo({top:0,behavior:'smooth'})};f.append(b);document.getElementById('grid').append(f)}
const selected=entries.find(e=>'#'+e.id===location.hash);if(selected){material.value=selected.palette;pattern.value=selected.pattern}show();</script></html>'''
(out/'index.html').write_text(page.replace('DATA',json.dumps(entries)))
print(f'Packaged {len(entries)} combinations in {out}')
