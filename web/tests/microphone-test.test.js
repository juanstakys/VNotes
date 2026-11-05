import {test} from 'node:test';
import assert from 'node:assert/strict';
import {testMicrophone} from '../audio.js';
test('settings monitor forwards RMS only and releases all resources on stop or setup failure',async()=>{
 const oldSecure=globalThis.isSecureContext,oldContext=globalThis.AudioContext,oldWorklet=globalThis.AudioWorkletNode;
 const oldNavigator=Object.getOwnPropertyDescriptor(globalThis,'navigator');
 let context,capture,track,fail=false;
 const node=()=>({connect(other){return other},disconnect(){this.disconnected=true;}});
 globalThis.isSecureContext=true;Object.defineProperty(globalThis,'navigator',{configurable:true,value:{mediaDevices:{getUserMedia(){}}}});
 globalThis.AudioContext=class{constructor(){context=this;this.state='running';this.audioWorklet={addModule:async()=>{if(fail)throw Error('worklet unavailable')}};}async resume(){}createMediaStreamSource(){return node();}createGain(){return {...node(),gain:{value:1}};}async close(){this.state='closed';}};
 globalThis.AudioWorkletNode=class{constructor(){Object.assign(this,node());this.port={};capture=this;}};
 const getStream=async()=>{track={stopped:false,stop(){this.stopped=true;}};return {getTracks:()=>[track]};};
 try{
  const levels=[];const monitor=await testMicrophone({getStream,onLevel:v=>levels.push(v),onError:e=>{throw e;}});
  capture.port.onmessage({data:{type:'chunk',level:.00002,buffer:new Float32Array(512)}});assert.deepEqual(levels,[.00002]);
  await monitor.stop();assert.ok(track.stopped);assert.equal(context.state,'closed');assert.equal(capture.port.onmessage,null);await monitor.stop();
  fail=true;await assert.rejects(testMicrophone({getStream,onLevel:()=>{},onError:()=>{}}),/worklet unavailable/);assert.ok(track.stopped);assert.equal(context.state,'closed');
 }finally{globalThis.isSecureContext=oldSecure;globalThis.AudioContext=oldContext;globalThis.AudioWorkletNode=oldWorklet;if(oldNavigator)Object.defineProperty(globalThis,'navigator',oldNavigator);else delete globalThis.navigator;}
});
