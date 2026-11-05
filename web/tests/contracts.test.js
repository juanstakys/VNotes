import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile, access} from 'node:fs/promises';
import vm from 'node:vm';
const read = path => readFile(new URL('../'+path,import.meta.url),'utf8');
test('install precaches every required shell asset',async()=>{
 const source=await read('sw.js'); const names=[...source.matchAll(/'([^']+\.(?:html|css|js|png|svg|webmanifest))'/g)].map(m=>m[1]);
 assert.ok(names.includes('stt-worker.js'));assert.ok(names.includes('capture-worklet.js'));
 await Promise.all(names.map(n=>access(new URL('../'+n,import.meta.url))));
 const manifest=JSON.parse(await read('manifest.webmanifest'));assert.equal(manifest.display,'standalone');assert.ok(manifest.icons.some(i=>i.sizes==='512x512'));
});
test('audio worklet batches 512 frames, reports RMS, and flushes partial blocks',async()=>{
 let Processor;const messages=[];
 vm.runInNewContext(await read('capture-worklet.js'),{AudioWorkletProcessor:class{constructor(){this.port={postMessage:data=>messages.push(data)}}},registerProcessor:(name,p)=>{assert.equal(name,'capture');Processor=p;},Float32Array});
 const p=new Processor();const left=new Float32Array(128).fill(1),right=new Float32Array(128).fill(-.5);
 for(let i=0;i<4;i++)assert.equal(p.process([[left,right]]),true);
 assert.equal(messages.length,1);assert.equal(messages[0].buffer.length,512);assert.equal(messages[0].level,.25);assert.ok(messages[0].buffer.every(v=>v===.25));assert.ok(left.every(v=>v===1));
 p.process([[left,right]]);p.port.onmessage({data:'flush'});assert.equal(messages[1].buffer.length,128);assert.equal(messages[2].type,'flushed');assert.equal(p.process([[left,right]]),false);
});
test('service worker serves cached voice assets offline and refuses failed response caching',async()=>{
 const handlers={},stored=new Map();let puts=0;
 const cache={match:async req=>stored.get(typeof req==='string'?req:req.url),put:async(req,res)=>{puts++;stored.set(req.url,res)},addAll:async()=>{}};
 vm.runInNewContext(await read('sw.js'),{self:{addEventListener:(name,handler)=>handlers[name]=handler,clients:{claim:async()=>{}}},caches:{open:async()=>cache},location:{origin:'https://vnotes.test'},URL,Response,fetch:async()=>new Response('missing',{status:404})});
 const request={url:'https://huggingface.co/model/resolve/main/config.json',method:'GET',mode:'cors'};
 let answer;handlers.fetch({request,respondWith:p=>answer=p});assert.equal((await answer).status,404);assert.equal(puts,0);
 const hit=new Response('cached');stored.set(request.url,hit);handlers.fetch({request,respondWith:p=>answer=p});assert.equal(await (await answer).text(),'cached');assert.equal(puts,0);
});
test('offline shell navigation falls back to cached root',async()=>{
 const handlers={};const cache={match:async key=>key==='./'?new Response('<title>VNotes</title>'):undefined};
 vm.runInNewContext(await read('sw.js'),{self:{addEventListener:(n,h)=>handlers[n]=h},caches:{open:async()=>cache},location:{origin:'https://vnotes.test'},URL,Response,fetch:async()=>{throw Error('offline')}});
 let answer;handlers.fetch({request:{url:'https://vnotes.test/index.html',method:'GET',mode:'navigate'},respondWith:p=>answer=p});
 assert.match(await (await answer).text(),/VNotes/);
});
