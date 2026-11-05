import * as db from './db.js';
import {record, testMicrophone} from './audio.js';
import {thresholdFromSlider, sliderFromThreshold, validThreshold, levelPosition} from './sensitivity.js';
import {ensureOffline, voiceManifest, hasVoice} from './offline.js';
import {VoiceClient} from './voice-client.js';
const $ = selector => document.querySelector(selector);
const icons = {
  mic: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14"/></svg>',
  stop: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/></svg>'
};
let notes = [], tab = 'list', silence = 2, threshold = 0.01, recorder = null, recording = false, starting = false, stopping = false, processing = false, loading = false, ready = false, editTarget = null, pending = null, toastTimer, locked = false;
let lastCapture = null;
let micTest = null, testOpening = false, testGeneration = 0, testLevel = 0;
async function stopMicTest() {
  testGeneration++;
  const current = micTest; micTest = null;
  if(current) await current.stop();
  if($('#test-mic')) {
    $('#test-mic').textContent = 'Test microphone'; $('#test-mic').disabled = testOpening;
    $('#mic-test-status').textContent = 'Test stopped. No audio was saved.';
    $('#mic-test-status').dataset.detected = 'false';
    $('#mic-test-fill').style.width = '0%';
    $('#mic-test-meter').setAttribute('aria-valuenow','0');
    $('#mic-test-meter').setAttribute('aria-valuetext','Test stopped');
  }
}
function updateMicFeedback() {
  if(!$('#sensitivity'))return;
  $('#sensitivity-value').textContent = `${(0.01 / threshold).toLocaleString('en', {maximumFractionDigits: 1})}× normal · threshold ${threshold.toFixed(5)} RMS`;
  $('#sensitivity').setAttribute('aria-valuetext', $('#sensitivity-value').textContent);
  $('#mic-test-threshold').style.left = `${levelPosition(threshold)}%`;
  if(!micTest) return;
  const detected = testLevel >= threshold;
  $('#mic-test-fill').style.width = `${levelPosition(testLevel)}%`;
  $('#mic-test-status').dataset.detected = String(detected);
  $('#mic-test-status').textContent = `${detected ? 'Above threshold · sound detected' : 'Below threshold · quiet'} · input ${testLevel.toFixed(5)} RMS`;
  $('#mic-test-meter').setAttribute('aria-valuenow', String(Math.round(levelPosition(testLevel))));
  $('#mic-test-meter').setAttribute('aria-valuetext', detected ? 'Sound detected' : 'Quiet');
}
const diagnostics = [];
function log(stage, message) {diagnostics.push({time: new Date().toISOString(), stage, message}); if (diagnostics.length > 30) diagnostics.shift();}
function report(stage, error) {
  const message = error?.message || String(error);
  log(stage, {message, stack: error?.workerStack || error?.stack}); console.error(stage, error);
  $('#error-message').textContent = `${stage}: ${message}`;
  $('#error-panel').hidden = false;
  notify(`${stage} failed. See the details above the input.`);
}
$('#dismiss-error').onclick = () => {$('#error-panel').hidden = true;};
const voice = new VoiceClient(data => {
  const p = data.progress;
  const message = data.type === 'progress' ? (p.status === 'progress' ? `Downloading ${p.file}: ${Math.round(p.progress || 0)}%` : 'Preparing English voice recognition…') : data.message;
  if (loading) $('#setup-status').textContent = message;
  else if (processing) $('#record-status').textContent = message;
});
const escape = text => String(text).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
function notify(message, undo) {
  clearTimeout(toastTimer); $('#toast span').textContent = message; $('#toast').hidden = false;
  const button = $('#toast button'); button.hidden = !undo;
  button.onclick = async () => {button.disabled = true; try {await undo(); $('#toast').hidden = true;} catch(e) {notify(e.message);} finally {button.disabled = false;}};
  toastTimer = setTimeout(() => {$('#toast').hidden = true;}, 6000);
}
async function mutate(action) {
  if (locked) return;
  locked = true;
  try {await action(); notes = await db.all('notes'); render();} catch (error) {notify(`Could not save: ${error.message}`);} finally {locked = false;}
}
function render() {
  const deleted = tab === 'deleted';
  const visible = notes.filter(n => Boolean(n.deletedAt) === deleted).sort((a,b) => (deleted ? b.deletedAt - a.deletedAt : b.createdAt - a.createdAt));
  for (const name of ['list', 'deleted']) {$('#tab-' + name).setAttribute('aria-selected', String(tab === name)); $('#tab-' + name).tabIndex = tab === name ? 0 : -1;}
  $('#notes').setAttribute('aria-labelledby', 'tab-' + tab);
  $('#notes').classList.toggle('deleted', deleted);
  $('#notes').replaceChildren();
  visible.forEach((note, index) => {
    const wrap = document.createElement('article'); wrap.className = 'note-wrap';
    wrap.innerHTML = `<div class="swipe-label" aria-hidden="true"><span>${deleted ? 'Erase' : 'Done'}</span><span>${deleted ? 'Restore' : 'Edit'}</span></div><div class="note"><p class="note-text">${escape(note.text)}</p><button class="note-menu" aria-label="Actions for ${escape(note.text.slice(0,50))}">···</button></div>`;
    const card = wrap.querySelector('.note');
    wrap.querySelector('button').onclick = () => noteMenu(note);
    let pointer;
    card.onpointerdown = event => {if (event.target.closest('button') || event.button !== 0) return; pointer = {x:event.clientX,y:event.clientY,id:event.pointerId,dx:0,axis:null};};
    card.onpointermove = event => {
      if (!pointer) return;
      const dx = event.clientX-pointer.x, dy=event.clientY-pointer.y;
      if (!pointer.axis && Math.max(Math.abs(dx),Math.abs(dy)) > 10) {pointer.axis = Math.abs(dx)>Math.abs(dy)*1.3 ? 'x':'y'; if(pointer.axis==='x') card.setPointerCapture(event.pointerId);}
      if(pointer.axis==='x') {pointer.dx=dx; card.style.transform=`translateX(${Math.max(-140,Math.min(140,dx))}px)`;}
    };
    card.onpointerup = () => {const dx = pointer?.dx || 0; pointer=null; card.style.transform=''; if(dx>85) deleted ? erase(note) : move(note,true); else if(dx < -85) deleted ? move(note,false) : chooseEdit(note);};
    card.onpointercancel = () => {pointer=null;card.style.transform='';};
    $('#notes').append(wrap);
  });
  $('#composer').hidden = deleted; $('#trash-tools').hidden = !deleted; $('#empty-trash').disabled = !visible.length;
  $('#setup-card').hidden = deleted || ready;
  updateControls();
}
function updateControls() {
  const typed = $('#draft').value.trim().length > 0;
  $('#action').innerHTML = recording ? icons.stop : typed ? icons.plus : icons.mic;
  $('#action').setAttribute('aria-label', recording ? 'Stop recording' : typed ? 'Add note' : 'Record a note');
  $('#action').classList.toggle('recording', recording);
  $('#action').disabled = starting || stopping || (!typed && (processing || loading));
  $('#draft').disabled = recording || starting || stopping;
  $('#cancel-record').hidden = !recording || stopping;
  $('#record-status').hidden = !(recording || processing || starting || stopping);
  $('#recording-feedback').hidden = !recording;
  $('#draft').hidden = recording;
  $('#record-status').classList.toggle('sr-only',recording);
  if(!recording)$('#level-meter').value=0;
  $('#cancel-processing').hidden = !processing;
  $('#retry-audio').disabled = processing || loading;
  $('#discard-audio').disabled = processing;
  $('#retry').hidden = !pending || processing || recording || starting || stopping;
}
function show(title, content, actions) {
  if(micTest||testOpening)void stopMicTest();
  $('#modal').setAttribute('aria-label', title);
  $('#modal-content').innerHTML = `<h2>${escape(title)}</h2>${content}`;
  $('#modal-actions').replaceChildren();
  for (const {label,run,style} of actions) {
    const b = document.createElement('button'); b.type='button'; b.textContent=label; if(style) b.className=style;
    b.onclick = async () => {b.disabled=true; try {await run();} catch(e) {notify(e.message);} finally {b.disabled=false;}}; $('#modal-actions').append(b);
  }
  if (!$('#modal').open) $('#modal').showModal();
}
const close = () => $('#modal').close();
$('#modal').addEventListener('close', () => {void stopMicTest();});
document.addEventListener('visibilitychange', () => {if(document.hidden)void stopMicTest();});
$('#modal-form').onsubmit = e => e.preventDefault();
function noteMenu(note) {
  show('Note actions', `<p>${escape(note.text)}</p>`, [{label:'Cancel',run:close},{label:note.deletedAt?'Restore':'Edit',run:()=>{close();note.deletedAt?move(note,false):chooseEdit(note);}},{label:note.deletedAt?'Erase forever':'Done',run:()=>{close();note.deletedAt?erase(note):move(note,true);}}]);
}
function chooseEdit(note) {
  if (recording || processing || starting || stopping) return notify('Finish the current recording first.');
  show('How would you like to edit?', '<p>Type a change or record a fresh thought.</p>', [{label:'Cancel',run:close},{label:'Record',run:()=>{close();startRecording(note.id);}},{label:'Type',style:'primary',run:()=>typeEdit(note)}]);
}
function typeEdit(note, text=note.text, recorded=false) {
  show(recorded?'Review your recording':'Edit your note', `<label for="edit-text">Note</label><textarea id="edit-text" maxlength="20000">${escape(text)}</textarea>`, [
    {label:'Cancel',run:close}, ...(recorded?[{label:'Record again',run:()=>{close();startRecording(note.id);}}]:[]),
    {label:'Save',style:'primary',run:async()=>{const value=$('#edit-text').value.trim();if(!value)return notify('Write something before saving.');await mutate(async()=>{const current=await db.get('notes',note.id);if(!current)throw Error('This note no longer exists.');await db.put('notes',note.id,{...current,text:value,updatedAt:Date.now()});close();});}}
  ]); $('#edit-text').focus();
}
async function add(text, fromAudio=false) {const now=Date.now();const note={id:crypto.randomUUID(),text,createdAt:now,updatedAt:now,deletedAt:null}; if(fromAudio)await db.saveAudioNote(note);else await db.put('notes',note.id,note);}
function move(note, deleted) {
  mutate(async()=>{
    const current=await db.get('notes',note.id);if(!current)return;
    await db.put('notes',note.id,{...current,deletedAt:deleted?Date.now():null,createdAt:deleted?current.createdAt:Date.now()});
    notify(deleted?'Moved to DONE':'Restored to List',()=>mutate(()=>db.put('notes',note.id,current)));
  });
}
function erase(note) {
  show('Erase this note forever?', '<p>This cannot be undone.</p>', [{label:'Cancel',run:close},{label:'Erase note',style:'danger',run:()=>mutate(async()=>{await db.remove('notes',note.id);close();})}]);
}
$('#empty-trash').innerHTML=icons.trash;
$('#empty-trash').onclick=()=>show('Empty DONE?', '<p>All done notes will be permanently erased. This cannot be undone.</p>', [{label:'Cancel',run:close},{label:'Erase all',style:'danger',run:()=>mutate(async()=>{const current=await db.all('notes');await db.writeNotes(current.filter(n=>!n.deletedAt),true);close();})}]);
for(const name of ['list','deleted']) {
  $('#tab-'+name).onclick=()=>{if(recording || starting || stopping)return notify('Stop or cancel recording before changing tabs.');tab=name;render();};
  $('#tab-'+name).onkeydown=e=>{if(['ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();const other=name==='list'?'deleted':'list';$('#tab-'+other).click();$('#tab-'+tab).focus();}};
}
$('#draft').oninput=()=>{updateControls();$('#draft').style.height='auto';$('#draft').style.height=Math.min(150,$('#draft').scrollHeight)+'px';};
$('#composer').onsubmit=async event=>{event.preventDefault();if(starting||stopping)return;if(recording)return recorder?.stop().catch(e=>notify(e.message));const text=$('#draft').value.trim();if(text)await mutate(async()=>{await add(text);$('#draft').value='';$('#draft').style.height='auto';});else await startRecording();};
$('#draft').onkeydown=e=>{if(e.key==='Enter'&&(e.ctrlKey||e.metaKey)){$('#composer').requestSubmit();}};
async function setup() {
  if(loading||processing||recording||starting||stopping)return;
  loading=true;$('#setup').disabled=true;$('#setup-status').textContent='Preparing download…';updateControls();
  try {
    await ensureOffline(); await voice.request('load');
    const manifest = await voiceManifest(); await db.put('settings','voiceManifestV2',manifest);
    ready=true; log('Setup', 'Moonshine Tiny / WASM / English ready');
    try {await navigator.storage?.persist?.();} catch {} notify('Voice notes are ready offline.');
  } catch(e) {ready=false;$('#setup-status').textContent=`Setup failed: ${e.message}. Reconnect and retry.`;report('Voice setup', e);}
  finally{loading=false;$('#setup').disabled=false;render();}
}
$('#setup').onclick=setup;
async function startRecording(target=null) {
  if(starting||recording||stopping||processing||loading)return;
  if(pending)return notify('Retry or discard the previous recording first.');
  if(!ready){notify('Set up voice notes before recording.');return;}
  $('#record-time').textContent='0:00'; starting=true;editTarget=target;$('#record-status').textContent='Opening microphone…';updateControls();
  try {
    recorder=await record({silenceSeconds:silence, threshold,
      onTick:(seconds,vad)=>{
        const suffix = vad.heard && vad.quietMs > 100 ? `Silence · stopping in ${vad.remainingSeconds.toFixed(1)} s` : vad.heard ? 'Listening…' : 'Waiting for your voice…';
        $('#record-status').textContent=`${target?'Replacing note · ':''}${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')} · ${suffix}`;
        $('#level-meter').value = levelPosition(vad.level) / 100;
        $('#record-time').textContent=`${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`;
        $('#level-meter').setAttribute('aria-valuetext', vad.level >= threshold ? 'Sound detected' : 'Quiet');
      },
      onStopping:()=>{stopping=true;recording=false;$('#record-status').textContent='Finishing recording…';updateControls();},
      onError:error=>{recorder=null;recording=false;stopping=false;report('Microphone capture',error);updateControls();},
      onStop:async(audio,usable,details)=>{
        recorder=null;recording=false;stopping=false;lastCapture=details;log('Capture',JSON.stringify(details));
        if(!usable){notify('No speech detected. Try a higher sensitivity or tap Stop manually.');updateControls();return;}
        pending={audio,target:editTarget};
        try{await db.put('audio','pending',pending);}catch(e){report('Saving retry audio',e);updateControls();return;}
        await transcribe();
      }
    });
    recording=true;
    if(document.hidden)await recorder.stop(false,'hidden');
  } catch(e) {report('Microphone',e);} finally {starting=false;updateControls();}
}
$('#cancel-record').onclick=async()=>{await recorder?.stop(true);recorder=null;recording=false;stopping=false;editTarget=null;updateControls();notify('Recording cancelled.');};
document.addEventListener('visibilitychange',()=>{if(document.hidden&&recording)void recorder?.stop(false,'hidden');});
$('#cancel-processing').onclick=()=>voice.reset(Error('Transcription cancelled. Retry or discard the saved recording.'));
async function transcribe() {
  if(!pending||processing||loading)return;
  processing=true;$('#record-status').textContent='Turning your voice into text… You can keep typing.';updateControls();
  try {
    await ensureOffline();const {text}=await voice.request('transcribe',pending.audio);
    if(!text)throw Error('No words found. Retry or discard this recording.');
    if(pending.target){const note=await db.get('notes',pending.target);if(!note)throw Error('The note being edited no longer exists.');typeEdit(note,text,true);}
    else {await add(text,true);notes=await db.all('notes');}
    await db.remove('audio','pending');pending=null;
  } catch(e) {report('Transcription',e);} finally {processing=false;render();}
}
$('#retry-audio').onclick=transcribe;
$('#discard-audio').onclick=async()=>{await db.remove('audio','pending');pending=null;updateControls();};
function settings() {
  if(recording||starting||stopping)return notify('Finish the recording before opening Settings.');
  show('Make it yours', `<p>Your notes stay in this browser, on this device. Export a backup before clearing browser data.</p><label for="silence">Stop after silence</label><select id="silence">${[.7,1,2,3,5].map(n=>`<option value="${n}" ${n===silence?'selected':''}>${n} seconds</option>`).join('')}</select><label for="sensitivity">Microphone sensitivity</label><input id="sensitivity" type="range" min="0" max="100" step="0.1" value="${sliderFromThreshold(threshold)}" aria-describedby="sensitivity-value sensitivity-help"><div class="slider-labels"><span>Less sensitive</span><span>More sensitive</span></div><output id="sensitivity-value" for="sensitivity"></output><p id="sensitivity-help">Slide right to detect a quieter voice. Keep room noise below the threshold, then speak and check that your voice crosses it.</p><div class="mic-test"><button type="button" id="test-mic">Test microphone</button><div id="mic-test-meter" class="mic-test-meter" role="meter" aria-label="Test microphone input level" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><div id="mic-test-fill"></div><span id="mic-test-threshold" title="Detection threshold"></span></div><div class="slider-labels"><span>Quiet</span><span>Loud</span></div><p id="mic-test-status" role="status">Test uses the microphone without saving or transcribing audio.</p><small>The vertical line marks your detection threshold. Adjust the slider while testing.</small></div><p>Recording stops after at least 300 ms of sound followed by silence, or at 2 minutes. Quiet voices and noisy rooms can affect detection.</p><p>Voice: ${ready?'Ready offline · English':'Not downloaded'} · v2 energy VAD</p><details><summary>Diagnostic details</summary><pre id="diagnostics"></pre></details><div class="settings-buttons"><button type="button" id="export">Export backup</button><button type="button" id="import">Import backup</button><button type="button" id="voice-setup">${ready?'Check voice setup':'Download voice model'}</button></div><input id="backup" type="file" accept="application/json,.json" hidden><p>Install: use your browser’s “Install app” or “Add to Home screen” menu. Initial voice setup needs internet; microphone access needs HTTPS or localhost.</p>`, [{label:'Done',style:'primary',run:close}]);
  $('#diagnostics').textContent=JSON.stringify({version:'2.1.0',secureContext:globalThis.isSecureContext,online:navigator.onLine,browser:navigator.userAgent,controller:navigator.serviceWorker?.controller?.scriptURL,threshold,silence,lastCapture,events:diagnostics},null,2);
  updateMicFeedback();
  $('#sensitivity').oninput=e=>{threshold=thresholdFromSlider(e.target.value); updateMicFeedback();};
  $('#sensitivity').onchange=async()=>{try{await db.put('settings','threshold',threshold);}catch(e){report('Saving sensitivity',e);}};
  $('#test-mic').onclick=async()=>{
    if(micTest) {await stopMicTest();return;}
    if(testOpening)return;
    testOpening=true; const generation=++testGeneration;
    $('#test-mic').disabled=true; $('#mic-test-status').textContent='Opening microphone…';
    try {
      const monitor=await testMicrophone({onLevel:level=>{testLevel=level;updateMicFeedback();},onError:error=>{void stopMicTest();if($('#mic-test-status'))$('#mic-test-status').textContent=error.message;}});
      if(generation!==testGeneration || !$('#modal').open || document.hidden){await monitor.stop();return;}
      micTest=monitor; $('#test-mic').textContent='Stop test';
    }catch(e){if($('#mic-test-status'))$('#mic-test-status').textContent=`Microphone test failed: ${e.message}`;}
    finally{testOpening=false;if($('#test-mic'))$('#test-mic').disabled=false;}
  };
  $('#silence').onchange=async e=>{silence=Number(e.target.value);await db.put('settings','silence',silence);};
  $('#voice-setup').onclick=async()=>{await stopMicTest();close();setup();};
  $('#export').onclick=async()=>{const payload={format:'vnotes',version:1,exportedAt:new Date().toISOString(),notes:await db.all('notes'),settings:{silence,threshold}};const url=URL.createObjectURL(new Blob([JSON.stringify(payload,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=`vnotes-${new Date().toISOString().slice(0,10)}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),10000);};
  $('#import').onclick=()=>$('#backup').click();
  $('#backup').onchange=async e=>{
    try{
      const file=e.target.files[0];if(!file)return;if(file.size>10000000)throw Error('Backup exceeds 10 MB.');
      const data=JSON.parse(await file.text());
      if(data.format!=='vnotes'||data.version!==1||!Array.isArray(data.notes)||data.notes.length>50000)throw Error('Unsupported backup format.');
      const ids=new Set(); const incoming=data.notes.map(n=>{
        if(typeof n.id!=='string'||!n.id||ids.has(n.id)||typeof n.text!=='string'||!n.text.trim()||n.text.length>20000||![n.createdAt,n.updatedAt].every(t=>Number.isFinite(t)&&t>0&&t<8640000000000000)||!(n.deletedAt===null||(Number.isFinite(n.deletedAt)&&n.deletedAt>0&&n.deletedAt<8640000000000000)))throw Error('Invalid note in backup.');
        ids.add(n.id);return {id:n.id,text:n.text,createdAt:n.createdAt,updatedAt:n.updatedAt,deletedAt:n.deletedAt};
      });
      const existing=new Set(notes.map(n=>n.id));const fresh=incoming.filter(n=>!existing.has(n.id));
      show('Import backup?',`<p>Add ${fresh.length} notes. Existing notes with matching IDs are kept. Current notes will be preserved.</p>`,[{label:'Cancel',run:close},{label:'Import',style:'primary',run:()=>mutate(async()=>{await db.writeNotes(fresh);close();notify('Backup imported.');})}]);
    }catch(error){notify(`Import failed: ${error.message}`);}
  };
}
$('#settings').onclick=settings;
window.addEventListener('unhandledrejection',event=>{report('Unexpected error',event.reason);});
async function init() {
  render();
  try {
    [notes,pending]=await Promise.all([db.all('notes'),db.get('audio','pending')]);
    const savedSilence=await db.get('settings','silence'); silence=[.7,1,2,3,5].includes(savedSilence)?savedSilence:2;
    const savedThreshold=await db.get('settings','threshold'); threshold=validThreshold(savedThreshold)?savedThreshold:.01;
    ready=await hasVoice(await db.get('settings','voiceManifestV2'));
    render();
  }catch(e){report('Local storage',e);render();}
  ensureOffline().catch(e=>report('Offline setup',e));
}
$('#reload-update').onclick=()=>{if(recording||starting||stopping||processing||$('#draft').value.trim())return notify('Finish recording and save your draft before reloading.');location.reload();};
if(navigator.serviceWorker){
  const wasControlled=Boolean(navigator.serviceWorker.controller);
  navigator.serviceWorker.addEventListener('controllerchange',()=>{if(wasControlled)$('#update-banner').hidden=false;});
}
if(globalThis.ResizeObserver)new ResizeObserver(entries=>{document.querySelector('main').style.paddingBottom=(entries[0].target.offsetHeight+24)+'px';}).observe(document.querySelector('footer'));
init();
