// Golden moonrise/moonset vectors from suncalc.js, the library the website ships.
import { readFileSync, writeFileSync } from 'node:fs';
const H = process.env.HOME + '/ThomassenPovoaHoldingAS/StormberryAS/GitHub/MoonApp';
global.window = {}; eval(readFileSync(H + '/suncalc.js','utf8')); const SunCalc = global.window.SunCalc;
const places = [
  ['Bergen',60.3913,5.3221], ['Tromso',69.6492,18.9553], ['Oslo',59.9139,10.7522],
  ['Equator',0.0,0.0], ['Rio',-22.9068,-43.1729], ['Singapore',1.3521,103.8198],
  ['Sydney',-33.8688,151.2093], ['Reykjavik',64.1466,-21.9426],
];
const rows = ['# Golden moonrise/moonset from suncalc.js (SunCalc.getMoonTimes, UTC mode).',
  '# Regenerate: node tools/gen-moontimes-golden.mjs',
  '# place,lat,lon,date,rise_iso_or_blank,set_iso_or_blank,flag'];
for (const [name,lat,lon] of places)
  for (const day of ['2026-01-15','2026-03-21','2026-06-21','2026-09-04','2026-09-18','2026-12-21']) {
    const d = new Date(day + 'T00:00:00Z');
    const t = SunCalc.getMoonTimes(d, lat, lon, true);
    const iso = x => x ? new Date(x).toISOString().replace('.000','') : '';
    const flag = t.alwaysUp ? 'alwaysUp' : t.alwaysDown ? 'alwaysDown' : '';
    rows.push(`${name},${lat},${lon},${day},${iso(t.rise)},${iso(t.set)},${flag}`);
  }
writeFileSync(H + '/android/app/src/test/resources/moontimes-golden.csv', rows.join('\n') + '\n');
console.log('  vectors:', rows.length - 3);
