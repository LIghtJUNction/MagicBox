/* Token Cloud: bounded, event-driven canvas. No continuous idle animation. */
'use strict';
(() => {
 const $ = id => document.getElementById(id);
 const root = document.documentElement;
 const systemMotion = matchMedia('(prefers-reduced-motion: reduce)');
 const darkMedia = matchMedia('(prefers-color-scheme: dark)');
 const store = { get(k) { try { return localStorage.getItem(k); } catch (_) { return null; } }, set(k,v) { try { localStorage.setItem(k,v); } catch (_) {} } };
 let reduced = store.get('cloud.reduce') === 'true';
 let theme = store.get('cloud.theme') || (darkMedia.matches ? 'dark' : 'light');
 let state = { edition:'universal', phase:'idle', mode:'system', root:false, nodeCount:0, nodes:[], selected:'', version:'0.2.0-alpha.1', message:'添加订阅，选择自己的连接方式。', packageName:'com.github.lightjunction.magicbox', modes:[{id:'system',available:true},{id:'tun',available:false,reason:'需要 Root 权限'},{id:'ebpf',available:false,reason:'需要 Root、支持的内核与设备'}] };
 const native = typeof window.MagicBridge?.request === 'function';
 const pending = new Map(); let sequence=0, busy=false, page='home', toastTimer=0, pollTimer=0,paused=false;
 const phaseLabel={idle:'尚未连接',ready:'本地代理就绪',verified:'连接已验证',starting:'正在建立连接',stopping:'正在断开',blocked:'尚未就绪',error:'连接需要检查',unknown:'状态未知'};
 function toast(text){$('toast').textContent=String(text);$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>{$('toast').hidden=true;},5500);}
 function request(action,payload={}) {
  if(!native) return Promise.reject(new Error('这是交互预览。网络操作请在 Android 应用中完成。'));
  if(pending.size>=3) return Promise.reject(new Error('请先完成当前操作。'));
  const id=String(++sequence);
  return new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{pending.delete(id);reject(new Error('操作超时，请刷新状态后重试。'));},90000);
   pending.set(id,{resolve,reject,timer});
   try{window.MagicBridge.request(id,action,JSON.stringify(payload));}catch(_){clearTimeout(timer);pending.delete(id);reject(new Error('本地接口暂不可用。'));}
  });
 }
 window.CloudNative={result(id,result){const p=pending.get(String(id));if(!p)return;pending.delete(String(id));clearTimeout(p.timer);if(result.ok)p.resolve(result.data);else p.reject(new Error(result.message||'操作未完成，原有配置未被替换。'));}, update(next){render(next);},notice(text){toast(text);}};
 async function act(action,payload={}){
  if(busy)return;busy=true;updateBusy();
  try{const result=await request(action,payload);if(result?.state)render(result.state);else if(result?.edition)render(result);if(result?.message)toast(result.message);if(action==='import' || action==='refreshSubscription')$('subscription-input').value='';}
  catch(error){toast(error.message);}
  finally{busy=false;updateBusy();if(native)schedulePoll(400);}
 }
 function updateBusy(){for(const id of ['connect','import-text','import-file','refresh-sub','root','diagnose'])$(id).disabled=busy;$('connect-label').textContent=busy?'正在处理':(state.running?'断开连接':state.edition==='ui'&&!state.root?'授予 Root 权限':'建立连接');}
 function render(next){
  if(!next || typeof next!=='object')return;state={...state,...next};
  const isUi=state.edition==='ui';
  document.querySelectorAll('.edition').forEach(el=>el.textContent=isUi?'UI EDITION':'UNIVERSAL');
  $('edition-detail').textContent=isUi?'由 MagicNet 驱动':'独立运行';
  $('edition-name').textContent=isUi?'MagicBox UI 版':'MagicBox 通用版';
  $('components-label').textContent=isUi?'不含内核 · 强制依赖 Root 与 MagicNet':'内置同源 sing-box 与订阅转换组件';
  $('advanced').hidden=!isUi;
  $('package-label').textContent=state.packageName||'';
  $('version-label').textContent=state.version||'';
  $('status-title').textContent=phaseLabel[state.phase]||'状态未知';
  $('status-dot').className='status-dot '+(['ready','verified'].includes(state.phase)?'ready':state.phase==='error'?'error':'');
  $('status-detail').textContent=state.message||'';
  $('selected-node').textContent=state.selected|| (isUi?'由 MagicNet 管理节点':'还没有节点');
  $('root-status').textContent=state.root?'已授权 · 仅执行明确的本地操作':'按需申请，不静默提权';
  $('backend-label').textContent=isUi?'MagicNet / 共享运行状态':'MagicNet / 同源核心';
  for(const el of document.querySelectorAll('[data-mode]')){const id=el.dataset.mode;const mode=state.modes?.find(m=>m.id===id);el.classList.toggle('selected',state.mode===id);el.setAttribute('aria-pressed',String(state.mode===id));el.setAttribute('aria-disabled',String(!mode?.available));el.title=mode?.reason||'';}
  $('node-count').textContent=String(state.nodeCount||0);
  renderNodes();updateBusy();applyMotion();
 }
 const NODE_PAGE_SIZE = 100;
 let nodesSignature = '', nodePage = 0, nodeQuery = '';
 function renderNodes() {
  const nodes = Array.isArray(state.nodes) ? state.nodes : [];
  const matched = nodeQuery ? nodes.filter(node => `${node.tag} ${node.type || ''}`.toLocaleLowerCase().includes(nodeQuery)) : nodes;
  const pages = Math.max(1, Math.ceil(matched.length / NODE_PAGE_SIZE));
  nodePage = Math.min(nodePage, pages - 1);
  const start = nodePage * NODE_PAGE_SIZE;
  const shown = matched.slice(start, start + NODE_PAGE_SIZE);
  const signature = JSON.stringify([shown, state.selected, state.edition, nodePage, nodeQuery, matched.length]);
  $('node-search').hidden = !nodes.length;
  $('node-pagination').hidden = matched.length <= NODE_PAGE_SIZE;
  $('node-page-label').textContent = `${nodePage + 1} / ${pages} · ${matched.length} 个节点`;
  $('node-prev').disabled = nodePage === 0;
  $('node-next').disabled = nodePage >= pages - 1;
  if (signature === nodesSignature) return;
  nodesSignature = signature;
  const list = $('node-list');
  list.replaceChildren();
  if (!matched.length) {
   const empty = document.createElement('div'); empty.className = 'empty-state';
   const glyph = document.createElement('span'); glyph.textContent = '{ }'; glyph.setAttribute('aria-hidden', 'true');
   const p = document.createElement('p');
   p.textContent = nodeQuery ? '没有匹配的节点。' : state.edition === 'ui' ? '节点由 MagicNet 模块管理。' : '一切从一个连接开始。';
   const small = document.createElement('small');
   small.textContent = nodeQuery ? '试试节点名称或协议类型。' : state.edition === 'ui' ? '导入订阅或打开高级管理。' : '导入后，节点会出现在这里。';
   empty.append(glyph, p, small); list.append(empty); return;
  }
  const fragment = document.createDocumentFragment();
  shown.forEach((node, index) => {
   const button = document.createElement('button');
   button.className = 'node-row' + (node.tag === state.selected ? ' selected' : '');
   button.dataset.token = ''; button.setAttribute('aria-pressed', String(node.tag === state.selected));
   const glyph = document.createElement('span'); glyph.className = 'node-symbol'; glyph.textContent = String(start + index + 1).padStart(2, '0');
   const copy = document.createElement('span'); copy.className = 'node-copy';
   const title = document.createElement('strong'); title.textContent = node.tag;
   const type = document.createElement('small'); type.textContent = String(node.type || 'proxy').toUpperCase();
   copy.append(title, type);
   const radio = document.createElement('span'); radio.className = 'node-radio'; radio.setAttribute('aria-hidden', 'true');
   button.append(glyph, copy, radio); button.addEventListener('click', () => act('select', {tag: node.tag})); fragment.append(button);
  });
  list.append(fragment);
 }
 $('node-search').addEventListener('input', event => {
  nodeQuery = event.target.value.trim().toLocaleLowerCase(); nodePage = 0; renderNodes();
 });
 for (const [id, step] of [['node-prev', -1], ['node-next', 1]]) {
  $(id).addEventListener('click', () => {
   nodePage += step; renderNodes(); $('node-search').scrollIntoView({block:'start'});
  });
 }
 function navigate(next){if(!['home','subscriptions','settings'].includes(next))return;particles.cancel();page=next;document.querySelectorAll('.page').forEach(el=>el.hidden=el.id!==`page-${next}`);document.querySelectorAll('[data-page]').forEach(el=>{el.classList.toggle('active',el.dataset.page===next);if(el.dataset.page===next)el.setAttribute('aria-current','page');else el.removeAttribute('aria-current');});window.scrollTo(0,0);if(page==='home')drawCloud();}
 let restoreFocus=null;
 function dialog(title,text){particles.cancel();restoreFocus=document.activeElement;$('dialog-title').textContent=title;$('dialog-content').textContent=text;$('dialog-overlay').hidden=false;$('dialog-close').focus();}
 function closeDialog(){if($('dialog-overlay').hidden)return false;$('dialog-overlay').hidden=true;restoreFocus?.focus();return true;}
 window.CloudUI={back(){if(closeDialog())return true;if(page!=='home'){navigate('home');return true;}return false;}, suspend(){paused=true;particles.cancel();clearTimeout(pollTimer);},resume(){paused=false;drawCloud();schedulePoll(0);},metrics(){return {...particles.metrics, reduced:motionOff()};}};
 document.addEventListener('keydown',event=>{if(event.key==='Escape')closeDialog();if(event.key==='Tab'&&!$('dialog-overlay').hidden){const first=$('dialog-close'),last=$('dialog-done');if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}}});
 const motionOff=()=>paused||reduced||systemMotion.matches||state.powerSave===true;
 function applyMotion(){root.dataset.reduced=String(motionOff());$('reduce-motion').setAttribute('aria-checked',String(reduced));if(motionOff())particles.cancel();}
 function applyTheme(){root.dataset.theme=theme;store.set('cloud.theme',theme);particles.cancel();drawCloud();if(native)request('appearance',{theme}).catch(()=>{});}
 const letters=['{','}','0','1','/','<','>','∴',':','[',']','·'];
 function cloudPoints(width,height){const points=[];for(let i=0;i<235;i++){const t=i*2.39996323;const u=(i+.5)/235;const y=1-2*u;const r=Math.sqrt(Math.max(0,1-y*y));const x=Math.cos(t)*r;const z=Math.sin(t)*r;const perspective=1+z*.16;const bend=Math.sin(y*3)*.12;points.push({x:width/2+(x+bend)*width*.31*perspective,y:height*.48+y*height*.33+Math.sin(x*3)*height*.09,z,g:letters[i%letters.length],i});}return points.sort((a,b)=>a.z-b.z);}
 function fit(canvas){const rect=canvas.getBoundingClientRect();const dpr=Math.min(devicePixelRatio||1,2);const w=Math.round(rect.width*dpr),h=Math.round(rect.height*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}const ctx=canvas.getContext('2d');ctx.setTransform(dpr,0,0,dpr,0,0);return {ctx,width:rect.width,height:rect.height};}
 function drawCloud(){if(page!=='home'||document.hidden)return;const {ctx,width,height}=fit($('cloud'));ctx.clearRect(0,0,width,height);const css=getComputedStyle(root);const ink=css.getPropertyValue('--ink').trim(),accent=css.getPropertyValue('--accent').trim();ctx.textAlign='center';ctx.textBaseline='middle';ctx.font='10px monospace';for(const p of cloudPoints(width,height)){ctx.globalAlpha=.17+(p.z+1)*.27;ctx.fillStyle=p.i%11===0?accent:ink;ctx.fillText(p.g,p.x,p.y);}ctx.globalAlpha=1;}
 const particles=(()=>{
  const canvas=$('particles'),ctx=canvas.getContext('2d');const max=72;const data=new Float32Array(max*6);const glyphs=new Array(max);let count=0,frame=0,start=0,owner=null,drag=false,dx=0,dy=0,color='',last=0,cap=max;const metrics={frames:0,maxPaintMs:0,active:0,limit:max};
  function cancel(){cancelAnimationFrame(frame);frame=0;ctx.clearRect(0,0,innerWidth,innerHeight);if(owner){owner.classList.remove('is-dispersed');owner=null;}metrics.active=0;drag=false;}
  function begin(el,pointCloud=false){cancel();if(motionOff()||document.hidden)return false;fit(canvas);owner=el;const rect=el.getBoundingClientRect();const text=(el.textContent.trim().replace(/\s+/g,'')||'{}01').slice(0,32);count=cap;color=getComputedStyle(root).getPropertyValue('--accent').trim();const points=pointCloud?cloudPoints(rect.width,rect.height):null;for(let i=0;i<count;i++){const col=i%12,row=Math.floor(i/12);const k=i*6;data[k]=rect.left+rect.width*(col+.5)/12;data[k+1]=rect.top+rect.height*(row+.5)/Math.ceil(count/12);if(pointCloud){const p=points[i*3];data[k]=rect.left+p.x;data[k+1]=rect.top+p.y;}const angle=i*2.399963;data[k+2]=Math.cos(angle)*(22+i%7*8);data[k+3]=Math.sin(angle)*(17+i%9*6);data[k+4]=7+i%4;data[k+5]=.45+(i%5)*.11;glyphs[i]=i%3===0?text[i%text.length]:letters[i%letters.length];}el.classList.add('is-dispersed');start=performance.now();last=0;dx=dy=0;metrics.active=count;return true;}
  function paint(now){const paintStart=performance.now();if(last&&now-last<15){frame=requestAnimationFrame(paint);return;}last=now;const t=Math.min(1,(now-start)/680);const spread=drag?1:Math.sin(Math.PI*t);ctx.clearRect(0,0,innerWidth,innerHeight);ctx.fillStyle=color;ctx.font='11px monospace';ctx.textAlign='center';ctx.textBaseline='middle';for(let i=0;i<count;i++){const k=i*6;ctx.globalAlpha=data[k+5]*(drag?1:Math.min(1,t*10,(1-t)*9));ctx.fillText(glyphs[i],data[k]+(data[k+2]+dx)*spread,data[k+1]+(data[k+3]+dy)*spread);}ctx.globalAlpha=1;const elapsed=performance.now()-paintStart;metrics.frames++;metrics.maxPaintMs=Math.max(metrics.maxPaintMs,elapsed);if(elapsed>12)cap=40;if(!drag&&t>=1){const el=owner;cancel();el?.classList.add('token-recover');setTimeout(()=>el?.classList.remove('token-recover'),220);return;}frame=requestAnimationFrame(paint);}
  return{metrics,cancel,burst(el){if(begin(el)){drag=false;frame=requestAnimationFrame(paint);}},dragStart(el){if(begin(el,true)){drag=true;frame=requestAnimationFrame(paint);}},move(x,y){dx=Math.max(-100,Math.min(100,x));dy=Math.max(-80,Math.min(80,y));},release(){if(!owner)return;drag=false;start=performance.now()-340;},};
 })();
 document.addEventListener('click',event=>{const button=event.target.closest('[data-token]');if(button&&!button.disabled)particles.burst(button);},true);
 let gesture=null;const stage=$('cloud-stage');stage.addEventListener('pointerdown',event=>{if(event.isPrimary)gesture={id:event.pointerId,x:event.clientX,y:event.clientY,active:false};});stage.addEventListener('pointermove',event=>{if(!gesture||gesture.id!==event.pointerId)return;const dx=event.clientX-gesture.x,dy=event.clientY-gesture.y;if(!gesture.active){if(Math.abs(dy)>12&&Math.abs(dy)>Math.abs(dx)){gesture=null;return;}if(Math.abs(dx)<10)return;gesture.active=true;stage.setPointerCapture(event.pointerId);particles.dragStart($('cloud'));}particles.move(dx,dy);});function finishGesture(){if(gesture?.active)particles.release();gesture=null;}stage.addEventListener('pointerup',finishGesture);stage.addEventListener('pointercancel',()=>{gesture=null;particles.cancel();});stage.addEventListener('lostpointercapture',finishGesture);
 $('appearance').addEventListener('click',()=>{theme=theme==='light'?'dark':'light';applyTheme();});$('reduce-motion').addEventListener('click',()=>{reduced=!reduced;store.set('cloud.reduce',String(reduced));applyMotion();});systemMotion.addEventListener?.('change',applyMotion);darkMedia.addEventListener?.('change',event=>{if(!store.get('cloud.theme')){theme=event.matches?'dark':'light';applyTheme();}});
 document.querySelectorAll('[data-page]').forEach(el=>el.addEventListener('click',()=>navigate(el.dataset.page)));
 $('choose-node').addEventListener('click',()=>navigate('subscriptions'));
 $('connect').addEventListener('click',()=>{if(state.edition==='ui'&&!state.root){act('authorize');return;}if(!state.running&&state.edition!=='ui'&&!state.nodeCount){navigate('subscriptions');toast('先导入订阅或节点，再建立连接。');return;}act(state.running?'stop':'start');});
 document.querySelectorAll('[data-mode]').forEach(el=>el.addEventListener('click',()=>{const mode=state.modes?.find(m=>m.id===el.dataset.mode);if(!mode?.available){toast(mode?.reason||'此模式暂不可用。');return;}act('mode',{mode:el.dataset.mode});}));
 $('mode-help').addEventListener('click',()=>dialog('三种方式，不混为一谈。','系统代理\n在本机提供 HTTP / SOCKS 服务。无 Root 时需在 Wi-Fi 或支持代理的应用里手动设置 127.0.0.1:2080；它不是全设备 VPN，部分应用会忽略系统代理。\n\nTUN\n需要 Root，通过虚拟网卡接管流量。启动前检查权限和其他代理，失败会保留错误状态。\n\neBPF\n需要 Root、支持 eBPF 的 sing-box 和设备内核。只有能力验证通过后才能使用，不会用 TUN 状态冒充 eBPF 成功。\n\nUI 版由 MagicNet 提供服务，只显示模块实际支持的模式。'));
 $('import-text').addEventListener('click',()=>{const text=$('subscription-input').value.trim();if(!text){toast('请先粘贴订阅链接或配置。');return;}act('import',{text});});$('import-file').addEventListener('click',()=>act('file'));$('refresh-sub').addEventListener('click',()=>act('refreshSubscription'));$('root').addEventListener('click',()=>act('authorize'));$('diagnose').addEventListener('click',()=>act('diagnose'));$('advanced').addEventListener('click',()=>act('advanced'));$('project').addEventListener('click',()=>act('about'));$('dialog-close').addEventListener('click',closeDialog);$('dialog-done').addEventListener('click',closeDialog);$('dialog-overlay').addEventListener('click',event=>{if(event.target===$('dialog-overlay'))closeDialog();});
 let polling = false;
 async function poll() {
  if (!native || document.hidden || paused || busy || polling) return;
  polling = true;
  try { const next = await request('status'); if (!document.hidden && !paused) render(next); }
  catch (_) { if (!document.hidden && !paused) render({phase:'unknown', message:'读取状态失败，请重新检查权限与组件。'}); }
  finally { polling = false; schedulePoll(5000); }
 }
 function schedulePoll(ms) {
  clearTimeout(pollTimer);
  if (native && !document.hidden && !paused) pollTimer = setTimeout(poll, ms);
 }
 document.addEventListener('visibilitychange',()=>{if(document.hidden){particles.cancel();clearTimeout(pollTimer);}else{drawCloud();schedulePoll(0);}});let resizeTimer=0;addEventListener('resize',()=>{particles.cancel();clearTimeout(resizeTimer);resizeTimer=setTimeout(drawCloud,90);});
 $('preview-note').hidden=native;applyTheme();render(state);schedulePoll(0);
})();
