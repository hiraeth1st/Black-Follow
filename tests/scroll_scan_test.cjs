const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/res/raw/scroll_scan.js','utf8');let checks=0;
function check(value,label){assert.ok(value,label);checks++;}
const cfg={id:'456',username:'target',kind:'followers',token:'test-token'};
function env(){
 let now=0,reply={},status=200,calls=0,opened=0,spinner=false,pending=null;
 const moves=[],box={scrollTop:0,scrollHeight:1800,clientHeight:600,parentElement:null,scrollBy(x){moves.push(x);this.scrollTop=Math.min(this.scrollTop+x.top,this.scrollHeight-this.clientHeight);}};
 const row={href:'https://www.instagram.com/person/',parentElement:box,getBoundingClientRect(){return {width:100,height:50};}};
 const profileLink={href:'https://www.instagram.com/target/followers/',getBoundingClientRect(){return {width:100,height:30};},click(){opened++;ctx.location.pathname='/target/followers/';}};
 const root={contains(e){return e===box||e===row;},querySelectorAll(q){return q==='a[href]'?[row]:spinner?[row]:[];}};box.parentElement=root;
 const doc={body:root,scrollingElement:box,querySelectorAll(q){return q==='a[href]'?[profileLink]:[];}};
 class XHR{constructor(){this.listeners=[];this.responseType='';}open(){}send(){}addEventListener(n,f){this.listeners.push(f);}getResponseHeader(){return null;}done(body,code=200){this.status=code;this.responseText=JSON.stringify(body);for(const f of this.listeners.splice(0))f.call(this);}}
 const ctx={location:{origin:'https://www.instagram.com',href:'https://www.instagram.com/target/',pathname:'/target/'},document:doc,URL,Date:{now:()=>now},XMLHttpRequest:XHR,innerHeight:800,getComputedStyle(){return {overflowY:'auto'};},fetch:async()=>{calls++;if(pending)await pending;return {status,headers:{get(){return '120';}},clone(){return {json:async()=>reply};}};}};
 ctx.window=ctx;ctx.top=ctx;vm.createContext(ctx);
 function install(config=cfg){return vm.runInContext(source.replace('__BF_CONFIG__',JSON.stringify(config)),ctx);}
 const api={ctx,box,moves,install,tick(n=2000){now+=n;},step(move=true){return ctx.__bfScrollScan.step(move);},setSpinner(v){spinner=v;},get calls(){return calls;},get opened(){return opened;},async send(body,code=200,url='/api/v1/friendships/456/followers/'){reply=body;status=code;const r=await ctx.fetch(url);await new Promise(setImmediate);return r;},defer(){let release;pending=new Promise(r=>release=r);return ()=>{pending=null;release();};}};
 return api;
}
const user=id=>({pk:String(id),username:'person'+id,full_name:'Person '+id});
(async()=>{
 let e=env();check(e.install()===true,'trusted page installs');check(e.install()===true,'same controller is not wrapped twice');
 e.step();e.step();check(e.opened===1,'opens the requested list once');check(e.calls===0,'controller does not issue standalone API requests');
 e.tick();check(e.step().moved===0,'no scrolling before the first captured page');
 await e.send({users:[user(1),user(2)],next_max_id:'opaque+/='});let r=e.step();check(r.packets.length===1&&r.packets[0].users[0].id==='1','captures numeric identities and first page');check(r.moved===0,'waits for response rendering');
 e.tick();r=e.step();check(r.moved===300&&e.moves[0].behavior==='smooth','moves half of the list viewport smoothly, not to the bottom');
 e.tick();e.setSpinner(true);check(e.step().moved===0,'visible loading indicator pauses scrolling');e.setSpinner(false);
 let release=e.defer(),request=e.send({users:[user(3)],next_max_id:'two'});e.tick();check(e.step().pending===1&&e.moves.length===1,'in-flight list request pauses scrolling');release();await request;
 e.tick();r=e.step(false);check(r.moved===0,'paused step does not scroll');
 await e.send({users:[user(3)],next_max_id:null},200,'/api/v1/friendships/456/followers/?max_id=opaque%2B%2F%3D');r=e.step(false);check(r.packets[0].cursor==='opaque+/='&&!r.packets[0].more,'opaque cursor retained and terminal page captured');
 e.tick();r=e.step();check(!r.ended,'terminal response alone does not skip remaining visual scroll');
 for(let i=0;i<6;i++){e.tick();r=e.step();}check(r.ended,'finishes only after settled bottom and terminal response');
 e=env();e.install();await e.send({users:[user(9)]},200,'/api/v1/friendships/999/followers/');check(e.step().packets.length===0,'another target is ignored');
 await e.send({users:[user(9)]},200,'/api/v1/friendships/456/following/');check(e.step().packets.length===0,'other relationship kind is ignored');
 await e.send({users:[user(9)]},200,'/api/v1/friendships/456/followers/?query=person');check(e.step().error==='BF_WEB_SEARCH','search results cannot contaminate a full list');
 e=env();e.install();await e.send({message:'please_wait_a_few_minutes'},429);r=e.step();check(r.packets[0].gate==='rate'&&r.packets[0].retry==='120'&&r.moved===0,'429 stops and forwards Retry-After');
 e=env();e.install();await e.send({challenge:{url:'DO_NOT_EXPORT'}},200);r=e.step();check(r.packets[0].gate==='restricted'&&!JSON.stringify(r).includes('DO_NOT_EXPORT'),'challenge stops without exporting body');
 e=env();e.install();await e.send({users:[{pk:9007199254740992,username:'wrong'}]});check(e.step().error==='BF_WEB_ID','unsafe JS numeric identity is rejected');
 e=env();e.install();await e.send({users:'bad'});check(e.step().error==='BF_WEB_SCHEMA','malformed list cannot be counted');
 e=env();e.install();let xhr=new e.ctx.XMLHttpRequest();xhr.open('GET','/api/v1/friendships/456/followers/');xhr.send();check(e.step().pending===1,'XHR participates in load waiting');xhr.done({users:[user(1)]});r=e.step();check(r.pending===0&&r.packets[0].users.length===1,'XHR response capture without changing returned content');
 e=env();e.install();for(let i=0;i<13;i++)await e.send({users:[user(i)]});r=e.step();check(r.error==='BF_WEB_QUEUE'&&r.packets.length===12,'bridge queue is bounded');
 e=env();e.ctx.location.origin='https://example.org';check(e.install()===false,'untrusted origin is rejected');
 console.log('PASS: '+checks+' WebView script fixture checks');
})().catch(e=>{console.error(e);process.exitCode=1;});
