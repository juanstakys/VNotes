const opened = new Promise((resolve, reject) => {
  const request = indexedDB.open('vnotes', 1);
  request.onupgradeneeded = () => {
    for (const name of ['notes', 'settings', 'audio']) request.result.createObjectStore(name);
  };
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});
export async function transact(store, mode, action) {
  const db = await opened;
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode);
    const request = action(tx.objectStore(store));
    tx.oncomplete = () => resolve(request?.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error('Storage transaction aborted'));
  });
}
export const get = (s, k) => transact(s, 'readonly', o => o.get(k));
export const put = (s, k, v) => transact(s, 'readwrite', o => o.put(v, k));
export const remove = (s, k) => transact(s, 'readwrite', o => o.delete(k));
export const all = s => transact(s, 'readonly', o => o.getAll());
export async function writeNotes(notes, clear = false) {
  return transact('notes', 'readwrite', o => { if (clear) o.clear(); for (const n of notes) o.put(n, n.id); });
}
// Commit a transcribed note and clear its retry audio atomically: a failed write cannot create duplicates on retry.
export async function saveAudioNote(note) {
  const db = await opened;
  return new Promise((resolve, reject) => {
    const tx = db.transaction(['notes', 'audio'], 'readwrite');
    tx.objectStore('notes').put(note, note.id);
    tx.objectStore('audio').delete('pending');
    tx.oncomplete = resolve;
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error('Could not save the transcription'));
  });
}
