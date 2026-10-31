#!/usr/bin/env node
/**
 * gen-quarter-parity.ts: the parity corpus for the first and last quarter (MoonApp 1.0.8).
 * ================================================================
 * The Android app's large home screen widget shows the next new moon, first quarter, full moon and
 * last quarter (MoonPhase.nextPhases). New and full moon are the website's getNextLunations, held
 * to it by lunation-parity.csv. The website has no quarters, so this script writes what its own
 * suncalc.js and its own phaseCrossing bisection give for them, with the same scan as
 * getNextLunations: daily samples at 12:00 UTC from two days before the selected day, each crossing
 * of 0.25 and 0.75 (upward) bracketed by two samples and found by phaseCrossing, an event dated
 * before the selected day in the zone skipped, the date the zone's (localYmd). NextPhasesTest in
 * the Android tests reads the CSV and holds the app to it within 2 seconds and to the day.
 *
 * Rows: for the eight zones of lunation-parity.csv, every 2026 quarter dated in the zone, on its
 * own day and the day after, plus the zones' offset changes, both ends of the date picker's range
 * and the two Kiritimati days of lunation-parity.csv.
 *
 * Usage, from anywhere (Node 24 runs the TypeScript directly):
 *   node tools/gen-quarter-parity.ts           write the CSV
 *   node tools/gen-quarter-parity.ts --check   write nothing; exit 1 unless the committed CSV is
 *                                              byte for byte what this script writes
 * tests/golden.test.ts and tools/build-ts.sh --check run the --check mode.
 *
 * Added 30 October 2026 with the home screen widgets, on tools/gen-lunar-parity.ts.
 * ================================================================
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { loadPage } from '../tests/load.ts';

const REPO_ROOT = resolve(import.meta.dirname, '..');
const OUT = join(REPO_ROOT, 'android', 'app', 'src', 'test', 'resources', 'quarter-parity.csv');
const page = loadPage({ root: pathToFileURL(REPO_ROOT + '/') });

const DAY = 86400000;
/** The page's LUNATION_SEARCH_DAYS: enough for all four phases. */
const SEARCH_DAYS = page.run<number>('LUNATION_SEARCH_DAYS');

const isoDate = (t: number): string => new Date(t).toISOString().slice(0, 10);
const addDays = (date: string, n: number): string => isoDate(Date.parse(`${date}T00:00:00Z`) + n * DAY);

const phaseCrossing = page.fn('phaseCrossing');
const localYmd = page.fn('localYmd');
const SunCalc = page.get('SunCalc');

/** The page's local date of an instant in a zone, YYYY-MM-DD. */
const localDate = (t: number, tz: string): string => localYmd(new Date(t), tz).map((n, i) => String(n).padStart(i ? 2 : 4, '0')).join('-');

const phaseAt = (t: number): number => SunCalc.getMoonIllumination(new Date(t)).phase;

/** The first first quarter and last quarter at or after the start of date in tz, as instants. */
function nextQuarters(date: string, tz: string): { first: number | null; last: number | null } {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number];
  const start = Date.UTC(y, m - 1, d, 12, 0, 0) - 2 * DAY;
  let first: number | null = null;
  let last: number | null = null;
  let prev = phaseAt(start);
  for (let k = 1; k <= SEARCH_DAYS; k++) {
    const t1 = start + k * DAY;
    const curr = phaseAt(t1);
    if (first === null && prev < 0.25 && curr >= 0.25) {
      const at = phaseCrossing(t1 - DAY, t1, 0.25);
      if (localDate(at, tz) >= date) first = at;
    }
    if (last === null && prev < 0.75 && curr >= 0.75) {
      const at = phaseCrossing(t1 - DAY, t1, 0.75);
      if (localDate(at, tz) >= date) last = at;
    }
    if (first !== null && last !== null) break;
    prev = curr;
  }
  return { first, last };
}

const ZONES = ['Europe/Oslo', 'America/Los_Angeles', 'Pacific/Kiritimati', 'Pacific/Pago_Pago', 'Australia/Sydney', 'America/Santiago', 'Asia/Kolkata', 'UTC'];

function quarterCsv(): { csv: string; count: number } {
  const rows = [
    '# The next first and last quarter as the website\'s suncalc.js and phaseCrossing give them: the',
    '# first at or after the start of the selected day in the zone, dated in that zone, by the scan of',
    '# getNextLunations. Written from the compiled app.js by tools/gen-quarter-parity.ts; do not edit by hand.',
    '# tz,date,next_first_quarter_date,next_first_quarter_iso,next_last_quarter_date,next_last_quarter_iso',
  ];
  // Every quarter from late 2025 to early 2027, as instants.
  const events: number[] = [];
  for (let t = Date.UTC(2025, 11, 1, 12); t < Date.UTC(2027, 0, 31, 12); t += 7 * DAY) {
    const r = nextQuarters(isoDate(t), 'UTC');
    for (const at of [r.first, r.last]) if (at !== null && !events.includes(at)) events.push(at);
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
    for (const d of ['2026-03-08', '2026-03-29', '2026-04-05', '2026-09-06', '2026-10-04', '2026-10-25', '2026-11-01', '1900-01-01', '2100-12-31', '2000-04-19', '2002-01-14']) dates.add(d);
    for (const date of [...dates].sort()) {
      const r = nextQuarters(date, tz);
      const day = (at: number | null): string => (at === null ? '' : localDate(at, tz));
      const iso = (at: number | null): string => (at === null ? '' : new Date(at).toISOString());
      rows.push([tz, date, day(r.first), iso(r.first), day(r.last), iso(r.last)].join(','));
      count++;
    }
  }
  return { csv: rows.join('\n') + '\n', count };
}

const { csv, count } = quarterCsv();
if (process.argv.includes('--check')) {
  let committed = '';
  try { committed = readFileSync(OUT, 'utf8'); } catch { committed = ''; }
  if (committed !== csv) {
    process.stderr.write(`${OUT} is NOT what tools/gen-quarter-parity.ts writes: run it and review the diff\n`);
    process.exit(1);
  }
  process.stdout.write(`golden check OK: ${OUT} matches, ${count} data rows\n`);
} else {
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, csv, 'utf8');
  process.stdout.write(`wrote ${OUT}: ${count} rows\n`);
}
