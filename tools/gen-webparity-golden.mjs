// Golden vectors for the CITY'S OWN DAY, computed with suncalc's algorithm but without
// suncalc's day truncation.
//
// gen-moontimes-golden.mjs pins MoonCalc against suncalc.js over a UTC day. This corpus pins
// it over the day the user actually asked about, which is the place's local one.
//
// WHY IT DOES NOT JUST CALL getMoonTimes. app.js:327 intends the city-local day and hands
// SunCalc.getMoonTimes an instant at the city's local midnight with inUTC set. But
// getMoonTimes begins:
//
//     var a = new Date(n); r ? a.setUTCHours(0,0,0,0) : a.setHours(0,0,0,0);
//
// so it FLOORS that instant to UTC midnight. For anywhere east of Greenwich the city's local
// midnight is on the previous UTC day, and the website therefore returns the previous day's
// moonrise and moonset under the selected date's label: for Oslo on 2026-01-15 it reports
// 2026-01-14T05:48Z. That is a defect in app.js, found 2026-09-05 while porting this test,
// and it is tracked separately; the Android app is the one that was right about the day.
//
// The scan below is getMoonTimes' own hourly search with quadratic interpolation, lifted
// verbatim except that it starts where it is told to. That makes this corpus the definition
// of correct rather than a mirror of either surface's current behaviour.
//
// Regenerate: node tools/gen-webparity-golden.mjs
import { readFileSync, writeFileSync } from 'node:fs';
const H = process.env.HOME + '/ThomassenPovoaHoldingAS/StormberryAS/GitHub/MoonApp';
global.window = {}; eval(readFileSync(H + '/suncalc.js', 'utf8'));
const SunCalc = global.window.SunCalc;

// Verbatim from app.js:296, so the corpus cannot drift from the site by construction.
function cityUtcOffsetMs(ianaId, at) {
  try {
    const dtf = new Intl.DateTimeFormat('en-US', {
      timeZone: ianaId, hour12: false,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    const p = {};
    for (const { type, value } of dtf.formatToParts(at)) p[type] = value;
    const asUtc = Date.UTC(+p.year, p.month - 1, +p.day, +p.hour, +p.minute, +p.second);
    return asUtc - Math.floor(at.getTime() / 1000) * 1000;
  } catch (e) { return 0; }
}

/**
 * SunCalc.getMoonTimes with the setUTCHours(0,0,0,0) line removed, so the 24-hour window
 * begins exactly at `start` instead of at the UTC midnight before it.
 */
function moonTimesFrom(start, lat, lon) {
  const hoursLater = (d, h) => new Date(d.valueOf() + h * 3600000);
  const alt = h => SunCalc.getMoonPosition(hoursLater(start, h), lat, lon).altitude - 0.133 * Math.PI / 180;
  let h0 = alt(0), rise, set, ye;
  for (let i = 1; i <= 24; i += 2) {
    const h1 = alt(i), h2 = alt(i + 1);
    const a = (h0 + h2) / 2 - h1;
    const b = (h2 - h0) / 2;
    const xe = -b / (2 * a);
    ye = (a * xe + b) * xe + h1;
    const d = b * b - 4 * a * h1;
    let roots = 0, x1, x2;
    if (d >= 0) {
      const dx = Math.sqrt(d) / (Math.abs(a) * 2);
      x1 = xe - dx; x2 = xe + dx;
      if (Math.abs(x1) <= 1) roots++;
      if (Math.abs(x2) <= 1) roots++;
      if (x1 < -1) x1 = x2;
    }
    if (roots === 1) { if (h0 < 0) rise = i + x1; else set = i + x1; }
    else if (roots === 2) { rise = i + (ye < 0 ? x2 : x1); set = i + (ye < 0 ? x1 : x2); }
    if (rise && set) break;
    h0 = h2;
  }
  const out = {};
  if (rise) out.rise = hoursLater(start, rise);
  if (set) out.set = hoursLater(start, set);
  if (!rise && !set) out[ye > 0 ? 'alwaysUp' : 'alwaysDown'] = true;
  return out;
}

const places = [
  ['Bergen', 60.3913, 5.3221, 'Europe/Oslo'],
  ['Tromso', 69.6492, 18.9553, 'Europe/Oslo'],
  ['Oslo', 59.9139, 10.7522, 'Europe/Oslo'],
  ['Equator', 0.0, 0.0, 'UTC'],
  ['Rio', -22.9068, -43.1729, 'America/Sao_Paulo'],
  ['Singapore', 1.3521, 103.8198, 'Asia/Singapore'],
  ['Sydney', -33.8688, 151.2093, 'Australia/Sydney'],
  ['Reykjavik', 64.1466, -21.9426, 'Atlantic/Reykjavik'],
  ['Longyearbyen', 78.2232, 15.6267, 'Arctic/Longyearbyen'],
];
const days = [
  '2026-01-15', '2026-02-28', '2026-03-21', '2026-05-09', '2026-06-21',
  '2026-09-04', '2026-09-18', '2026-11-02', '2026-12-21',
];

const rows = [
  '# Golden moonrise/moonset AS THE WEBSITE COMPUTES THEM (city-local day window).',
  '# Mirrors app.js:326-328. Regenerate: node tools/gen-webparity-golden.mjs',
  '# place,lat,lon,tz,date,rise_iso_or_blank,set_iso_or_blank,flag',
];
for (const [name, lat, lon, tz] of places) {
  for (const day of days) {
    const [year, month, dayN] = day.split('-').map(Number);
    const dateForCalc = new Date(Date.UTC(year, month - 1, dayN, 12, 0, 0));
    const tzOffsetMs = cityUtcOffsetMs(tz, dateForCalc);
    const cityMidnightUtc = new Date(Date.UTC(year, month - 1, dayN) - tzOffsetMs);
    const t = moonTimesFrom(cityMidnightUtc, lat, lon);
    const iso = x => (x && !isNaN(x)) ? new Date(x).toISOString().replace('.000', '') : '';
    const flag = t.alwaysUp ? 'alwaysUp' : t.alwaysDown ? 'alwaysDown' : '';
    rows.push(`${name},${lat},${lon},${tz},${day},${iso(t.rise)},${iso(t.set)},${flag}`);
  }
}
writeFileSync(H + '/android/app/src/test/resources/webparity-golden.csv', rows.join('\n') + '\n');
console.log('  vectors:', rows.length - 3);
