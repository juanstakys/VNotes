import {test} from 'node:test';
import assert from 'node:assert/strict';
import {EnergyVAD} from '../vad.js';
import {splitAudio, describeError} from '../speech-utils.js';
for (const rate of [16000,44100,48000]) {
 test(`silence stop uses actual audio duration at ${rate}Hz`,()=>{
  const vad=new EnergyVAD({sampleRate:rate,silenceSeconds:.7});
  const block=Math.round(rate*.01);
  for(let i=0;i<200;i++)assert.equal(vad.push(0,block),false,'leading silence never stops');
  for(let i=0;i<30;i++)assert.equal(vad.push(.03,block),false);
  assert.equal(vad.heard,true);
  for(let i=0;i<69;i++)assert.equal(vad.push(0,block),false);
  assert.equal(vad.push(0,block),true);
 });
}
test('brief click does not arm auto-stop; speech resets quiet countdown',()=>{
 const vad=new EnergyVAD({sampleRate:16000});vad.push(.2,512);assert.equal(vad.push(0,32000),false);
 vad.push(.02,4800);assert.equal(vad.push(0,16000),false);vad.push(.02,512);assert.equal(vad.quietMs,0);assert.equal(vad.push(0,32000),true);
});
test('sensitivity detects quiet voice and rejects background noise',()=>{
 const high=new EnergyVAD({sampleRate:16000,threshold:.006});high.push(.008,4800);assert.ok(high.heard);
 const low=new EnergyVAD({sampleRate:16000,threshold:.02});low.push(.008,48000);assert.equal(low.heard,false);
});
test('long audio splits into <=20 second chunks without dropping or repeating samples',()=>{
 const audio=Float32Array.from({length:16000*65},(_,i)=>i);
 const segments=splitAudio(audio);assert.ok(segments.length>1);assert.ok(segments.every(a=>a.length<=320000));
 const restored=new Float32Array(audio.length);let offset=0;for(const segment of segments){restored.set(segment,offset);offset+=segment.length;}
 assert.equal(offset,audio.length);assert.deepEqual(restored,audio);
});
test('runtime failures retain useful error details',()=>{
 assert.match(describeError(Error('decoder crashed')),/decoder crashed/);assert.match(describeError(1234),/1234/);assert.match(describeError({code:'OOM'}),/OOM/);
});
