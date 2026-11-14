export const languages = [["id", "Indonesia"], ["en", "English"], ["ms", "Bahasa Melayu"], ["es", "Español"], ["pt", "Português"], ["fr", "Français"], ["de", "Deutsch"], ["ru", "Русский"], ["ar", "العربية"], ["hi", "हिन्दी"], ["ja", "日本語"], ["ko", "한국어"], ["zh", "中文"], ["vi", "Tiếng Việt"], ["th", "ไทย"], ["tr", "Türkçe"], ["it", "Italiano"], ["nl", "Nederlands"], ["pl", "Polski"], ["uk", "Українська"]];
export const englishStrings = [
  "Features",
  "Gallery",
  "About",
  "Get the app",
  "System status",
  "REMOTE DESKTOP FOR ANDROID",
  "Your desktop,",
  "wherever",
  "you go",
  "Access a Windows PC or cloud PC from Android. Work, create, and control your desktop from one place.",
  "Download XyDesk",
  "Explore features",
  "POWERED BY FREERDP",
  "MADE IN INDONESIA",
  "YOUR PC. YOUR CONTROL. ↗",
  "DESKTOP ACCESS",
  "TOUCH CONTROLS",
  "FILE TRANSFER",
  "BUILT FOR ANDROID",
  "MADE FOR MOVEMENT",
  "A full desktop.",
  "In your hands.",
  "A remote desktop experience built for touch, without losing the control you need.",
  "Connect to your PC",
  "Access a Windows desktop or cloud PC over RDP from your Android device.",
  "Control at your fingertips",
  "Use touch, keyboard and gamepad controls inside your session to navigate your desktop.",
  "Files move with you",
  "Transfer files between phone and PC through the virtual XyDesk drive in your RDP session.",
  "A CLOSER LOOK",
  "More than",
  "a second screen.",
  "Explore the XyDesk Remote session interface: controls and desktop in one view.",
  "Meet XyDesk Remote",
  "SESSION ARCHIVE · ANDROID",
  "APK DOWNLOADS",
  "Total from available public releases",
  "PUBLIC RELEASE",
  "Version available to the public",
  "SERVICE STATUS",
  "Operational",
  "View system status ↗",
  "READY TO CONNECT?",
  "Start here",
  "Choose the APK for your Android device.",
  "RECOMMENDED",
  "OLDER DEVICES",
  "Options & compatibility",
  "CHECK ABI AND ALL DOWNLOADS",
  "Your desktop, close wherever you are.",
  "Privacy",
  "Terms",
  "Licenses",
  "Made in Indonesia",
  "Home",
  "Download"
];
// Results may be incomplete when the translation provider is unavailable.
// Fall back to English and cache only fully populated translations.
export async function translatedStrings(lang, signal) {
  const key = 'xydesk_next_translations_v1_' + lang;
  try { const stored = JSON.parse(localStorage.getItem(key) || 'null'); if (stored && typeof stored === 'object' && englishStrings.every(s => typeof stored[s] === 'string' && stored[s])) return stored; } catch {}
  const out = {};
  const chunks = [];
  for (let i = 0; i < englishStrings.length; i += 40) chunks.push(englishStrings.slice(i, i + 40));
  for (const items of chunks) {
    const r = await fetch('/api/ui/translate', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({target:lang==='zh'?'zh-CN':lang,items}),signal});
    if (!r.ok) throw new Error('translation unavailable');
    const data = await r.json();
    if (data.status !== 'ok' || !Array.isArray(data.items)) throw new Error('invalid translation');
    items.forEach((source,index)=>{ if (typeof data.items[index] === 'string' && data.items[index].trim()) out[source]=data.items[index]; });
  }
  if (Object.keys(out).length === englishStrings.length) try { localStorage.setItem(key,JSON.stringify(out)); } catch {}
  return out;
}
