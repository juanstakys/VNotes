import {record} from '../audio.js';
import {ensureOffline, voiceManifest} from '../offline.js';
import {VoiceClient} from '../voice-client.js';
const output = document.querySelector('#output');
const print = text => {output.textContent += '\n' + text;};
const voice = new VoiceClient(data => {if (data.type==='status')print(data.message);else if(data.progress?.status==='progress') output.textContent=`Downloading ${data.progress.file}: ${Math.round(data.progress.progress || 0)}%`;});
function run(id, task) {document.querySelector(id).onclick = async()=>{const buttons=document.querySelectorAll('button');buttons.forEach(b=>b.disabled=true);try {await task();}catch(e){print('FAIL: '+e.message);console.error(e);}finally{buttons.forEach(b=>b.disabled=false);}};}
run('#load', async()=>{await ensureOffline();await voice.request('load');const urls=await voiceManifest();print(`PASS: model warmup complete; ${urls.length} cached dependencies.`);});
let captured;
run('#capture', async()=>{
  await ensureOffline();
  const context = new AudioContext({sampleRate:48000});await context.resume();
  try {
    const file = await fetch('./speech.wav');if(!file.ok)throw Error('Missing speech.wav fixture');
    const decoded=await context.decodeAudioData(await file.arrayBuffer());
    const source=context.createBufferSource();source.buffer=decoded;
    const destination=context.createMediaStreamDestination();source.connect(destination);
    let resolved,rejected;const finished=new Promise((resolve,reject)=>{resolved=resolve;rejected=reject;});
    print('Starting synthetic microphone at 48 kHz; capture context requests 16 kHz…');
    const capture=await record({silenceSeconds:.7,threshold:.006,getStream:async()=>destination.stream,
      onTick:()=>{},onStopping:reason=>print('Stop reason: '+reason),onError:rejected,
      onStop:(audio,usable,details)=>{captured=audio;print(JSON.stringify(details));if(!usable)rejected(Error('Speech was not detected'));else if(details.reason!=='silence')rejected(Error('Expected silence auto-stop'));else resolved(audio);}
    });
    source.start();
    const timeout=setTimeout(()=>{capture.stop(true);rejected(Error('Silence stop did not fire within 30 seconds'));},30000);
    let audio;try{audio=await finished;}finally{clearTimeout(timeout);await capture.stop(true);}
    print(`PASS: capture and auto-stop; ${audio.length} samples of 16 kHz PCM.`);
    const {text}=await voice.request('transcribe',audio);print('TRANSCRIPT: '+text);
    if(!/country/i.test(text)||!/ask/i.test(text))throw Error('Expected known speech words in transcription');
    print('PASS: real speech transcription.');
  } finally {await context.close();}
});
run('#short',async()=>{
 if(!captured)throw Error('Run capture first.');
 const {text}=await voice.request('transcribe',captured.subarray(0,5*16000));print('Short transcript: '+text);if(!text.trim())throw Error('Empty transcript');print('PASS: second request.');
});

run('#manual', async()=>{
 for(const cancel of [false,true]){
  const context=new AudioContext({sampleRate:48000});await context.resume();
  const oscillator=context.createOscillator();oscillator.frequency.value=220;
  const gain=context.createGain();gain.gain.value=.03;
  const destination=context.createMediaStreamDestination();oscillator.connect(gain).connect(destination);oscillator.start();
  let called=false;
  try{
   const capture=await record({silenceSeconds:2,getStream:async()=>destination.stream,onTick:()=>{},onError:e=>{throw e;},onStop:(audio,usable,details)=>{called=true;if(!usable||details.reason!=='manual'||!audio.length)throw Error('Manual capture invalid');}});
   await new Promise(resolve=>setTimeout(resolve,600));
   await Promise.all([capture.stop(cancel),capture.stop(cancel)]);
   if(called===cancel)throw Error(cancel?'Cancel emitted audio':'Manual stop lost audio');
   if(destination.stream.getTracks().some(t=>t.readyState!=='ended'))throw Error('Microphone tracks were not released');
   print(`PASS: ${cancel?'cancel discards capture':'manual stop produces audio'}; duplicate stop is safe; tracks released.`);
  }finally{oscillator.stop();await context.close();}
 }
});
run('#long',async()=>{
 if(!captured)throw Error('Run capture first.');
 const long=new Float32Array(captured.length*3);for(let i=0;i<3;i++)long.set(captured,i*captured.length);
 const {text}=await voice.request('transcribe',long);print('Long transcript: '+text);
 if((text.match(/country/gi)||[]).length<4)throw Error('Expected speech from more than one segment');
 print('PASS: long-note segmented inference.');
});
