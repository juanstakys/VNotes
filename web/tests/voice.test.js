import {test} from 'node:test';
import assert from 'node:assert/strict';
import {VoiceClient} from '../voice-client.js';
import {hasVoice} from '../offline.js';
test('worker client transfers a copy, correlates requests and ignores stale replies',async()=>{
 let worker;
 globalThis.Worker=class {constructor(){worker=this;}postMessage(message,transfer){this.message=message;this.transfer=transfer;}terminate(){this.terminated=true;}};
 const statuses=[];const client=new VoiceClient(data=>statuses.push(data));
 const source=new Float32Array([1,2,3]);const promise=client.request('transcribe',source);
 assert.notEqual(worker.message.audio.buffer,source.buffer);assert.equal(worker.transfer[0],worker.message.audio.buffer);
 await assert.rejects(client.request('load'),/busy/);
 worker.onmessage({data:{id:0,type:'result',text:'stale'}});assert.ok(client.current);
 worker.onmessage({data:{id:worker.message.id,type:'status',message:'working'}});assert.equal(statuses.length,1);
 worker.onmessage({data:{id:worker.message.id,type:'result',text:'hello'}});assert.equal((await promise).text,'hello');assert.equal(client.current,null);assert.deepEqual([...source],[1,2,3]);
 const cancelled=client.request('load');client.reset();await assert.rejects(cancelled,/kept for retry/);assert.ok(worker.terminated);
 const failed=client.request('load');worker.onmessage({data:{id:worker.message.id,type:'error',message:'decoder OOM',stack:'backend details'}});await assert.rejects(failed,error=>error.message==='decoder OOM'&&error.workerStack==='backend details');assert.ok(worker.terminated);
 delete globalThis.Worker;
});
test('offline readiness checks every asset, not just weight filename fragments',async()=>{
 const existing=new Set(['runtime','encoder','decoder']);
 globalThis.caches={open:async()=>({match:async key=>existing.has(key)?{}:undefined})};
 assert.equal(await hasVoice(null),false);assert.equal(await hasVoice([]),false);
 assert.equal(await hasVoice(['runtime','encoder','decoder']),true);
 assert.equal(await hasVoice(['runtime','encoder','decoder','missing-tokenizer']),false);
 delete globalThis.caches;
});
