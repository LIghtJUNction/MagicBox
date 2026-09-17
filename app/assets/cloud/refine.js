'use strict';
(() => {
 const reduce = matchMedia('(prefers-reduced-motion: reduce)');
 const shots = new WeakMap();
 const canvas = document.createElement('canvas');
 canvas.id = 'click-particles';
 canvas.setAttribute('aria-hidden','true');
 document.body.append(canvas);
 const ctx = canvas.getContext('2d');
 const glyphs = ['{','}','0','1','/','<','>','∴',':','[',']','·'];
 let frame=0,start=0,active=0,frames=0,points=[];
 function fit(){const dpr=Math.min(devicePixelRatio||1,2),w=Math.round(innerWidth*dpr),h=Math.round(innerHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}ctx.setTransform(dpr,0,0,dpr,0,0);}
 function quiet(){return document.hidden||reduce.matches||document.documentElement.dataset.reduced==='true';}
 function capture(el){if(!el||!el.getClientRects().length)return null;const r=el.getBoundingClientRect(),text=(el.textContent.trim().replace(/\s+/g,'')||'{}01').slice(0,28);return{left:r.left,top:r.top,width:r.width,height:r.height,text};}
 function stop(){cancelAnimationFrame(frame);frame=0;active=0;points=[];ctx.clearRect(0,0,innerWidth,innerHeight);}
 function launch(shot){stop();if(!shot||quiet())return;fit();const count=64,accent=getComputedStyle(document.documentElement).getPropertyValue('--accent').trim();points=[];for(let i=0;i<count;i++){const col=i%12,row=Math.floor(i/12),angle=i*2.399963;points.push({x:shot.left+shot.width*(col+.5)/12,y:shot.top+shot.height*(row+.5)/Math.ceil(count/12),vx:Math.cos(angle)*(42+i%7*11),vy:Math.sin(angle)*(30+i%9*8),a:.72+(i%5)*.055,g:i%3===0?shot.text[i%shot.text.length]:glyphs[i%glyphs.length],c:accent});}active=count;start=performance.now();frame=requestAnimationFrame(paint);}
 function paint(now){frame=0;const t=Math.min(1,(now-start)/860),spread=Math.sin(Math.PI*Math.pow(t,.82));ctx.clearRect(0,0,innerWidth,innerHeight);ctx.textAlign='center';ctx.textBaseline='middle';ctx.font='600 12px ui-monospace, monospace';for(const p of points){ctx.fillStyle=p.c;ctx.globalAlpha=p.a*Math.min(1,t*10,(1-t)*8);ctx.fillText(p.g,p.x+p.vx*spread,p.y+p.vy*spread);}ctx.globalAlpha=1;frames++;if(t>=1){stop();return;}frame=requestAnimationFrame(paint);}
 document.addEventListener('click',event=>{const el=event.target.closest?.('[data-token]');if(el&&!el.disabled){const shot=capture(el);if(shot)shots.set(event,shot);}},true);
 document.addEventListener('click',event=>{const shot=shots.get(event);if(shot)launch(shot);});
 const rootStatus=document.getElementById('root-status');
 if(rootStatus){const normalize=()=>{const granted=rootStatus.textContent.includes('已授权');rootStatus.textContent=granted?'已授权':'未授权';};new MutationObserver(normalize).observe(rootStatus,{childList:true,subtree:true,characterData:true});normalize();}
 const status=document.getElementById('status-detail');if(status?.textContent==='添加订阅，选择自己的连接方式。')status.textContent='';
 document.addEventListener('visibilitychange',()=>{if(document.hidden)stop();});
 addEventListener('resize',stop);
 window.MagicClick={metrics(){return{active,frames};}};
})();
