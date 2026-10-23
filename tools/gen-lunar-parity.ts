#!/usr/bin/env node
/**
 * gen-lunar-parity.ts: the parity corpora for fixes F-1 to F-3 and F-5, written from the compiled page.
 * ================================================================
 * Writes three CSVs into android/app/src/test/resources, each the answer of the page itself
 * (app.js, loaded with suncalc.js and cities.js by tests/load.ts, exactly as the browser runs
 * them), so the Android port can be held to the website:
 *
 *   lunation-parity.csv       F-1: the next new and full moon at or after the start of a day in a
 *                             zone, dated in that zone, with the instants themselves
 *                             (getNextLunations). LunationParityTest reads it.
 *   transit-parity.csv        F-2: the lunar transit in a place's local day (from its first
 *                             instant to the next day's, F-5), blank on a day without one
 *                             (lunarTransitFrom). TransitParityTest.
 *   lunar-daykind-parity.csv  F-3: whether the moon rises and sets, stays up or stays down, as the
 *                             results card labels it (onCalculate). LunarDayKindParityTest.
 *
 * The rows are chosen to reach the cases the fixes are about: every 2026 lunation dated in eight
 * zones on its own day and the day after (F-1); every day without a transit at eight places plus
 * a regular sample (F-2); every 2026 day whose peak lies between the horizon and the rise
 * threshold on a two-degree latitude grid, with its neighbours, plus five polar places (F-3).
 * Every day a place's zone changes its offset is in the F-2 and F-3 corpora with the days either
 * side, since F-5 made those days 23 or 25 hours long (the Android side must agree there too).
 *
 * The page is called from TypeScript through tests/load.ts fn(), so no page code is written
 * inside a string here (23 October 2026; until then eight blocks were, one of them a hand copy
 * of the page's offset code).
 *
 * Usage, from anywhere (Node 24 runs the TypeScript directly):
 *   node tools/gen-lunar-parity.ts           write the three CSVs
 *   node tools/gen-lunar-parity.ts --check   write nothing; exit 1 unless each committed CSV is
 *                                            byte for byte what this script writes
 * tests/golden.test.ts and tools/build-ts.sh --check run the --check mode, so a change to the
 * page's lunar code that is not carried into the corpora (and so into the Android tests) fails.
 *
 * Added 22 October 2026 with the fixes, on SunApp's tools/gen-solar-golden.ts.
 * ================================================================
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { loadPage } from '../tests/load.ts';

const REPO_ROOT = resolve(import.meta.dirname, '..');
const RES = join(REPO_ROOT, 'android', 'app', 'src', 'test', 'resources');
const page = loadPage({ root: pathToFileURL(REPO_ROOT + '/') });

const DAY = 86400000;

const isoDate = (t: number): string => new Date(t).toISOString().slice(0, 10);
const iso = (t: number | null): string => (t === null ? '' : new Date(t).toISOString());
const addDays = (date: string, n: number): string => isoDate(Date.parse(`${date}T00:00:00Z`) + n * DAY);

const getNextLunations = page.fn('getNextLunations');
const lunarTransitFrom = page.fn('lunarTransitFrom');
const moonTimesFrom = page.fn('moonTimesFrom');
const localYmd = page.fn('localYmd');
const localDayWindow = page.fn('localDayWindow');
/** The page's MOON_RISE_ALTITUDE: 0.133 degrees, where it calls the moon's centre risen. */
const RISE_ALTITUDE = page.run<number>('MOON_RISE_ALTITUDE');
const onCalculate = page.fn('onCalculate');
const { SunCalc, state, els } = { SunCalc: page.get('SunCalc'), state: page.get('state'), els: page.get('els') };

/** The page's local date of an instant in a zone, YYYY-MM-DD (years 1 to 9999 here). */
const localDate = (t: number, tz: string): string => localYmd(new Date(t), tz).map((n, i) => String(n).padStart(i ? 2 : 4, '0')).join('-');

/** The page's day for a date in a zone (localDayWindow): its first instant and the next day's. */
function dayWindow(tz: string, date: string): { start: number; end: number } {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number];
  const w = localDayWindow(y, m, d, tz);
  return { start: w.start.getTime(), end: w.end.getTime() };
}

/** Whether a date in a zone is not 24 hours long, or a neighbour of such a day. */
const nearOffsetChange = (tz: string, date: string): boolean => [-1, 0, 1].some((n) => {
  const w = dayWindow(tz, addDays(date, n));
  return w.end - w.start !== DAY;
});

// F-1 ---------------------------------------------------------------------------------------
const ZONES = ['Europe/Oslo', 'America/Los_Angeles', 'Pacific/Kiritimati', 'Pacific/Pago_Pago', 'Australia/Sydney', 'America/Santiago', 'Asia/Kolkata', 'UTC'];

function lunationCsv(): { csv: string; count: number } {
  const rows = [
    '# The next new and full moon as the web page gives them (getNextLunations, fix F-1): the first',
    '# at or after the start of the selected day in the zone, dated in that zone. Written from the',
    '# compiled app.js by tools/gen-lunar-parity.ts; do not edit by hand.',
    '# tz,date,next_new_date,next_new_iso,next_full_date,next_full_iso',
  ];
  // Every new and full moon from late 2025 to early 2027, as instants.
  const events: number[] = [];
  for (let d = Date.UTC(2025, 11, 1, 12); d < Date.UTC(2027, 0, 31, 12); d += 14 * DAY) {
    const r = getNextLunations(new Date(d), 'UTC');
    for (const at of [r.newAt, r.fullAt]) if (at !== null && !events.includes(at.getTime())) events.push(at.getTime());
  }
  events.sort((a, b) => a - b);
  let count = 0;
  for (const tz of ZONES) {
    const dates = new Set<string>();
    for (const t of events) {
      const d = localDate(t, tz);
      if (d < '2026-01-01' || d > '2026-12-31') continue;
      dates.add(d); dates.add(addDays(d, 1));
    }
    // The zone's offset changes, the two ends of the date picker's useful range, and two days
    // whose event falls between 10:00 and 12:00 UTC the day before, the first hours of the day in
    // Kiritimati (UTC+14), which the search must start early enough to see.
    for (const d of ['2026-03-08', '2026-03-29', '2026-04-05', '2026-09-06', '2026-10-04', '2026-10-25', '2026-11-01', '1900-01-01', '2100-12-31', '2000-04-19', '2002-01-14']) dates.add(d);
    for (const date of [...dates].sort()) {
      const [y, m, d] = date.split('-').map(Number) as [number, number, number];
      const r = getNextLunations(new Date(Date.UTC(y, m - 1, d, 12, 0, 0)), tz);
      const day = (x: Date | null): string => (x ? x.toISOString().slice(0, 10) : '');
      rows.push([tz, date, day(r.nextNew), iso(r.newAt ? r.newAt.getTime() : null), day(r.nextFull), iso(r.fullAt ? r.fullAt.getTime() : null)].join(','));
      count++;
    }
  }
  return { csv: rows.join('\n') + '\n', count };
}

// F-2 ---------------------------------------------------------------------------------------
const TRANSIT_PLACES: ReadonlyArray<readonly [string, number, number, string]> = [
  ['Bergen', 60.3913, 5.3221, 'Europe/Oslo'],
  ['Tromso', 69.6492, 18.9553, 'Europe/Oslo'],
  ['Sydney', -33.8688, 151.2093, 'Australia/Sydney'],
  ['Quito', -0.2299, -78.5250, 'America/Guayaquil'],
  ['Anchorage', 61.2181, -149.9003, 'America/Anchorage'],
  // Added with F-5 for their offset changes: one at midnight (Santiago), one of half an hour.
  ['New York', 40.7128, -74.0060, 'America/New_York'],
  ['Santiago', -33.4489, -70.6693, 'America/Santiago'],
  ['Lord Howe', -31.5553, 159.0821, 'Australia/Lord_Howe'],
];

function transitCsv(): { csv: string; count: number } {
  const rows = [
    '# The lunar transit as the web page finds it (lunarTransitFrom, fixes F-2 and F-5): the',
    '# culmination in the place\'s local day, from day_start_iso to day_end_iso (23 or 25 hours on',
    '# the days the zone changes its offset), refined by the parabola vertex and truncated to whole',
    '# seconds; blank when the moon does not culminate above the 0.133 degree rise threshold in that',
    '# window. Written from the compiled app.js by tools/gen-lunar-parity.ts; do not edit by hand.',
    '# place,lat,lon,tz,date,day_start_iso,day_end_iso,transit_iso_or_blank',
  ];
  let count = 0;
  for (const [name, lat, lon, tz] of TRANSIT_PLACES) {
    const all: Array<{ date: string; start: number; end: number; t: number | null }> = [];
    for (let k = 0; k < 365; k++) {
      const date = isoDate(Date.UTC(2026, 0, 1 + k));
      const { start, end } = dayWindow(tz, date);
      const at = lunarTransitFrom(new Date(start), lat, lon, (end - start) / 3600000);
      all.push({ date, start, end, t: at ? at.getTime() : null });
    }
    all.forEach((r, k) => {
      const nearBlank = all.slice(Math.max(0, k - 1), k + 2).some((x) => x.t === null);
      if (!(k % 7 === 0 || nearBlank || nearOffsetChange(tz, r.date))) return;
      rows.push([name, lat.toFixed(4), lon.toFixed(4), tz, r.date, iso(r.start), iso(r.end), iso(r.t)].join(','));
      count++;
    });
  }
  return { csv: rows.join('\n') + '\n', count };
}

// F-3 ---------------------------------------------------------------------------------------
const DAYKIND_PLACES: ReadonlyArray<readonly [string, number, number, string]> = [
  ['Longyearbyen', 78.2233, 15.6469, 'Arctic/Longyearbyen'],
  ['Tromso', 69.6492, 18.9553, 'Europe/Oslo'],
  ['Murmansk', 68.9792, 33.0925, 'Europe/Moscow'],
  ['Utqiagvik', 71.2906, -156.7886, 'America/Anchorage'],
  ['McMurdo', -77.8419, 166.6863, 'Antarctica/McMurdo'],
];

/** What the results card says for a place and date: NORMAL, ALWAYS_UP or ALWAYS_DOWN. */
async function cardKind(lat: number, lon: number, tz: string, date: string): Promise<string> {
  state.tab = 'city';
  state.city = { name: 'Corpus', fold: '', country: 'Corpus', cfold: '', lat, lon, tz, alt: '' };
  els.dateInput.value = date;
  await onCalculate();
  const rise = els.resMoonrise.textContent;
  return /Always Up/.test(rise) ? 'ALWAYS_UP' : /Always Down/.test(rise) ? 'ALWAYS_DOWN' : 'NORMAL';
}

async function dayKindCsv(): Promise<{ csv: string; count: number }> {
  const rows = [
    '# The lunar day kind as the web page labels it (onCalculate, fix F-3): NORMAL when the moon rises',
    '# or sets in the place\'s local day, else ALWAYS_UP when its centre peaks above the 0.133 degree',
    '# rise threshold and ALWAYS_DOWN when it does not. The grid rows (lon 0, UTC) are every 2026 day',
    '# whose peak lies between the horizon and that threshold, with the days either side; the',
    '# places are every seventh day and every day their zone changes its offset, with its neighbours.',
    '# Written from the compiled app.js by tools/gen-lunar-parity.ts; do not edit by hand.',
    '# place,lat,lon,tz,date,day_kind',
  ];
  let count = 0;
  const band: Array<[number, string]> = [];
  for (let lat = -89; lat <= 89; lat += 2) {
    for (let k = 0; k < 365; k++) {
      const start = Date.UTC(2026, 0, 1 + k);
      const t = moonTimesFrom(new Date(start), lat, 0);
      if (t.rise || t.set) continue;
      let peak = -Infinity;
      for (let i = 0; i <= 96; i++) peak = Math.max(peak, SunCalc.getMoonPosition(new Date(start + i * 900000), lat, 0).altitude);
      if (peak > 0 && peak <= RISE_ALTITUDE) band.push([lat, isoDate(start)]);
    }
  }
  for (const [lat, date] of band) {
    for (const d of [addDays(date, -1), date, addDays(date, 1)]) {
      rows.push([`Grid ${Math.abs(lat)}${lat < 0 ? 'S' : 'N'}`, lat.toFixed(4), (0).toFixed(4), 'UTC', d, await cardKind(lat, 0, 'UTC', d)].join(','));
      count++;
    }
  }
  for (const [name, lat, lon, tz] of DAYKIND_PLACES) {
    for (let k = 0; k < 365; k++) {
      const date = isoDate(Date.UTC(2026, 0, 1 + k));
      if (!(k % 7 === 0 || nearOffsetChange(tz, date))) continue;
      rows.push([name, lat.toFixed(4), lon.toFixed(4), tz, date, await cardKind(lat, lon, tz, date)].join(','));
      count++;
    }
  }
  return { csv: rows.join('\n') + '\n', count };
}

const outputs: Array<[string, { csv: string; count: number }]> = [
  ['lunation-parity.csv', lunationCsv()],
  ['transit-parity.csv', transitCsv()],
  ['lunar-daykind-parity.csv', await dayKindCsv()],
];
if (process.argv.includes('--check')) {
  let bad = 0;
  for (const [name, { csv, count }] of outputs) {
    const path = join(RES, name);
    let committed = '';
    try { committed = readFileSync(path, 'utf8'); } catch { committed = ''; }
    if (committed !== csv) {
      process.stderr.write(`${path} is NOT what tools/gen-lunar-parity.ts writes: run it and review the diff\n`);
      bad = 1;
    } else {
      process.stdout.write(`golden check OK: ${path} matches, ${count} data rows\n`);
    }
  }
  process.exit(bad);
} else {
  for (const [name, { csv, count }] of outputs) {
    const path = join(RES, name);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, csv, 'utf8');
    process.stdout.write(`wrote ${path}: ${count} rows\n`);
  }
}
