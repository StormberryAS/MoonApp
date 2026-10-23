#!/usr/bin/env node
/**
 * gen-webparity-golden.ts: golden vectors for the CITY'S OWN DAY, computed with suncalc's algorithm
 * but without suncalc's day truncation.
 * ================================================================
 * gen-moontimes-golden.ts pins MoonCalc against suncalc.js over a UTC day. This corpus pins it over
 * the day the user actually asked about, which is the place's local one.
 *
 * WHY IT DOES NOT JUST CALL getMoonTimes. The page intends the city-local day and once handed
 * SunCalc.getMoonTimes an instant at the city's local midnight with inUTC set. But getMoonTimes
 * begins:
 *
 *     var a = new Date(n); r ? a.setUTCHours(0,0,0,0) : a.setHours(0,0,0,0);
 *
 * so it FLOORS that instant to UTC midnight. For anywhere east of Greenwich the city's local
 * midnight is on the previous UTC day, and the website therefore returned the previous day's
 * moonrise and moonset under the selected date's label: for Oslo on 2026-01-15 it reported
 * 2026-01-14T05:48Z. That was a defect in app.js, found 2026-09-05 while porting this test and
 * fixed the same day with moonTimesFrom; the Android app was the one that was right about the day.
 *
 * The scan below is getMoonTimes' own hourly search with quadratic interpolation, lifted verbatim
 * except that it starts where it is told to. That makes this corpus the definition of correct
 * rather than a mirror of either surface's current behaviour.
 *
 * moonTimesFrom is a copy of the page's (ts/app.ts). The day is found independently of the page:
 * dayStart below bisects for the first instant whose local date is the day, where the page tries
 * the offsets either side (localDayWindow). tests/golden.test.ts closes the gap from the other
 * side: for every row it calls the compiled page's own localDayWindow and moonTimesFrom and
 * requires the same instants, so the copy and the page cannot drift apart unnoticed.
 *
 * THE DAY (23 October 2026, fix F-5). Until then the window started at the zone's offset at 12:00
 * UTC applied to midnight and always ran 24 hours, so on a day the zone changes its offset it
 * started an hour off and missed or repeated an hour. It is now the local day from its first
 * instant to the next day's (23 or 25 hours on those days). None of the 81 rows falls on such a
 * day, so none of them changed. The flag column (alwaysUp or alwaysDown, from the sign of the last parabola
 * vertex, as getMoonTimes sets it) is this corpus's own and is not what the page shows.
 *
 * Usage, from anywhere (Node 24 runs the TypeScript directly):
 *   node tools/gen-webparity-golden.ts           write the CSV
 *   node tools/gen-webparity-golden.ts --check   write nothing; exit 1 unless the committed CSV is
 *                                                byte for byte what this script writes
 *
 * TYPESCRIPT (22 October 2026). Until then this was tools/gen-webparity-golden.mjs, which read and
 * wrote a path under $HOME that no longer exists and loaded suncalc.js with eval. It now works
 * relative to the repository and runs the vendored file in this realm. Two lines differ in form,
 * not in result: `p.month - 1` (a string coerced by the minus) is written +p.month - 1, and
 * isNaN(date) is written isNaN(date.getTime()). The data rows are byte for byte what the JavaScript
 * wrote; the only change to the CSV is the generator's name in its second comment line.
 * ================================================================
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const REPO_ROOT = resolve(import.meta.dirname, '..');
const OUT_PATH = join(REPO_ROOT, 'android', 'app', 'src', 'test', 'resources', 'webparity-golden.csv');

interface SunCalcLike {
  getMoonPosition(date: Date, lat: number, lng: number): { altitude: number; azimuth: number; distance: number };
}

/** The bundled suncalc.js, run as the page runs it: a classic script that publishes window.SunCalc. */
function loadSunCalc(): SunCalcLike {
  const source = readFileSync(join(REPO_ROOT, 'suncalc.js'), 'utf8');
  const win: { SunCalc?: SunCalcLike } = {};
  new Function('window', 'exports', 'module', 'define', source)(win, undefined, undefined, undefined);
  if (!win.SunCalc) throw new Error('suncalc.js did not publish window.SunCalc');
  return win.SunCalc;
}
const SunCalc = loadSunCalc();

/** A local date in an IANA zone as one number that orders as dates do. */
function localKey(t: number, tz: string): number {
  const p: Record<string, string> = {};
  for (const { type, value } of new Intl.DateTimeFormat('en-US', { timeZone: tz, year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(new Date(t))) p[type] = value;
  return (Number(p.year) * 100 + Number(p.month)) * 100 + Number(p.day);
}

/** The first instant (to the second) whose local date in tz is year-month-day or later: bisected
    between 15 hours either side of that date's midnight in UTC, which brackets every zone. */
function dayStart(year: number, month: number, day: number, tz: string): number {
  const wall = Date.UTC(year, month - 1, day);
  const want = (year * 100 + month) * 100 + day;
  let lo = wall - 15 * 3600000, hi = wall + 15 * 3600000;
  while (hi - lo > 1000) {
    const mid = lo + Math.floor((hi - lo) / 2000) * 1000;
    if (localKey(mid, tz) >= want) hi = mid; else lo = mid;
  }
  return hi;
}

interface Times { rise?: Date; set?: Date; alwaysUp?: true; alwaysDown?: true }

/**
 * SunCalc.getMoonTimes with the setUTCHours(0,0,0,0) line removed, so the window begins exactly
 * at `start` instead of at the UTC midnight before it, and runs `hours` long (F-5): a crossing
 * after its end is not counted, which with 24 hours never happens.
 */
function moonTimesFrom(start: Date, lat: number, lon: number, hours: number): Times {
  const hoursLater = (d: Date, h: number): Date => new Date(d.valueOf() + h * 3600000);
  const alt = (h: number): number => SunCalc.getMoonPosition(hoursLater(start, h), lat, lon).altitude - 0.133 * Math.PI / 180;
  let h0 = alt(0), rise: number | undefined, set: number | undefined, ye = 0;
  for (let i = 1; i - 1 < hours; i += 2) {
    const h1 = alt(i), h2 = alt(i + 1);
    const a = (h0 + h2) / 2 - h1;
    const b = (h2 - h0) / 2;
    const xe = -b / (2 * a);
    ye = (a * xe + b) * xe + h1;
    const d = b * b - 4 * a * h1;
    let roots = 0, x1 = 0, x2 = 0;
    if (d >= 0) {
      const dx = Math.sqrt(d) / (Math.abs(a) * 2);
      x1 = xe - dx; x2 = xe + dx;
      if (Math.abs(x1) <= 1) roots++;
      if (Math.abs(x2) <= 1) roots++;
      if (x1 < -1) x1 = x2;
    }
    const inWindow = (x: number): boolean => i + x <= hours;
    if (roots === 1) { if (inWindow(x1)) { if (h0 < 0) rise = i + x1; else set = i + x1; } }
    else if (roots === 2) {
      const r = ye < 0 ? x2 : x1, s = ye < 0 ? x1 : x2;
      if (inWindow(r)) rise = i + r;
      if (inWindow(s)) set = i + s;
    }
    if (rise && set) break;
    h0 = h2;
  }
  const out: Times = {};
  if (rise) out.rise = hoursLater(start, rise);
  if (set) out.set = hoursLater(start, set);
  if (!rise && !set) out[ye > 0 ? 'alwaysUp' : 'alwaysDown'] = true;
  return out;
}

/** The places and dates of the corpus; tests/golden.test.ts reads them back from the CSV. */
const PLACES: ReadonlyArray<readonly [string, number, number, string]> = [
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
const DAYS = [
  '2026-01-15', '2026-02-28', '2026-03-21', '2026-05-09', '2026-06-21',
  '2026-09-04', '2026-09-18', '2026-11-02', '2026-12-21',
] as const;
function build(): { csv: string; count: number } {
  const rows = [
    '# Golden moonrise/moonset AS THE WEBSITE COMPUTES THEM (city-local day window).',
    '# The day runs from its first instant to the next day\'s (fix F-5). Regenerate: node tools/gen-webparity-golden.ts',
    '# place,lat,lon,tz,date,rise_iso_or_blank,set_iso_or_blank,flag',
  ];
  const iso = (x: Date | undefined): string => ((x && !isNaN(x.getTime())) ? new Date(x).toISOString().replace('.000', '') : '');
  const row = (name: string, lat: number, lon: number, tz: string, day: string): void => {
    const [year, month, dayN] = day.split('-').map(Number) as [number, number, number];
    const start = dayStart(year, month, dayN, tz);
    const hours = (dayStart(year, month, dayN + 1, tz) - start) / 3600000;
    const t = moonTimesFrom(new Date(start), lat, lon, hours);
    const flag = t.alwaysUp ? 'alwaysUp' : t.alwaysDown ? 'alwaysDown' : '';
    rows.push(`${name},${lat},${lon},${tz},${day},${iso(t.rise)},${iso(t.set)},${flag}`);
  };
  for (const [name, lat, lon, tz] of PLACES) for (const day of DAYS) row(name, lat, lon, tz, day);
  return { csv: rows.join('\n') + '\n', count: rows.length - 3 };
}

const { csv, count } = build();
if (process.argv.includes('--check')) {
  let committed = '';
  try { committed = readFileSync(OUT_PATH, 'utf8'); } catch { committed = ''; }
  if (committed !== csv) {
    process.stderr.write(`${OUT_PATH} is NOT what tools/gen-webparity-golden.ts writes: run it and review the diff\n`);
    process.exit(1);
  }
  process.stdout.write(`golden check OK: ${OUT_PATH} matches, ${count} data rows\n`);
} else {
  mkdirSync(dirname(OUT_PATH), { recursive: true });
  writeFileSync(OUT_PATH, csv, 'utf8');
  process.stdout.write(`wrote ${OUT_PATH}: ${count} vectors\n`);
}
