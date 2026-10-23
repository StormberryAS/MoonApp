#!/usr/bin/env node
/**
 * gen-moontimes-golden.ts: the moonrise and moonset golden corpus for the Android port, UTC days.
 * ================================================================
 * Writes android/app/src/test/resources/moontimes-golden.csv: 8 places x 6 dates, each with what
 * SunCalc.getMoonTimes returns in UTC mode (the window is the UTC day), rise and set as ISO
 * instants (blank when absent) and suncalc's own alwaysUp or alwaysDown flag. MoonTimesGoldenTest
 * holds MoonCalc.times to it. The page itself does not call getMoonTimes (it searches the city's
 * own day: see gen-webparity-golden.ts), so this corpus pins the Kotlin against the library alone.
 *
 * Usage, from anywhere (Node 24 runs the TypeScript directly):
 *   node tools/gen-moontimes-golden.ts           write the CSV
 *   node tools/gen-moontimes-golden.ts --check   write nothing; exit 1 unless the committed CSV is
 *                                                byte for byte what this script writes
 * tests/golden.test.ts and tools/build-ts.sh --check run the --check mode.
 *
 * TYPESCRIPT (22 October 2026). Until then this was tools/gen-moontimes-golden.mjs, which read and
 * wrote a path under $HOME that no longer exists and loaded suncalc.js with eval. It now works
 * relative to the repository and runs the vendored file in this realm, as gen-golden.ts does. The
 * data rows are byte for byte what the JavaScript wrote; the only change to the CSV is the
 * generator's name in its second comment line.
 * ================================================================
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const REPO_ROOT = resolve(import.meta.dirname, '..');
const OUT_PATH = join(REPO_ROOT, 'android', 'app', 'src', 'test', 'resources', 'moontimes-golden.csv');

interface MoonTimesLike { rise?: Date; set?: Date; alwaysUp?: true; alwaysDown?: true }
interface SunCalcLike {
  getMoonTimes(date: Date, lat: number, lng: number, inUTC?: boolean): MoonTimesLike;
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

const PLACES: ReadonlyArray<readonly [string, number, number]> = [
  ['Bergen', 60.3913, 5.3221], ['Tromso', 69.6492, 18.9553], ['Oslo', 59.9139, 10.7522],
  ['Equator', 0.0, 0.0], ['Rio', -22.9068, -43.1729], ['Singapore', 1.3521, 103.8198],
  ['Sydney', -33.8688, 151.2093], ['Reykjavik', 64.1466, -21.9426],
];
const DAYS = ['2026-01-15', '2026-03-21', '2026-06-21', '2026-09-04', '2026-09-18', '2026-12-21'] as const;

function build(): { csv: string; count: number } {
  const rows = ['# Golden moonrise/moonset from suncalc.js (SunCalc.getMoonTimes, UTC mode).',
    '# Regenerate: node tools/gen-moontimes-golden.ts',
    '# place,lat,lon,date,rise_iso_or_blank,set_iso_or_blank,flag'];
  const iso = (x: Date | undefined): string => (x ? new Date(x).toISOString().replace('.000', '') : '');
  for (const [name, lat, lon] of PLACES) {
    for (const day of DAYS) {
      const d = new Date(day + 'T00:00:00Z');
      const t = SunCalc.getMoonTimes(d, lat, lon, true);
      const flag = t.alwaysUp ? 'alwaysUp' : t.alwaysDown ? 'alwaysDown' : '';
      rows.push(`${name},${lat},${lon},${day},${iso(t.rise)},${iso(t.set)},${flag}`);
    }
  }
  return { csv: rows.join('\n') + '\n', count: rows.length - 3 };
}

const { csv, count } = build();
if (process.argv.includes('--check')) {
  let committed = '';
  try { committed = readFileSync(OUT_PATH, 'utf8'); } catch { committed = ''; }
  if (committed !== csv) {
    process.stderr.write(`${OUT_PATH} is NOT what tools/gen-moontimes-golden.ts writes: run it and review the diff\n`);
    process.exit(1);
  }
  process.stdout.write(`golden check OK: ${OUT_PATH} matches, ${count} data rows\n`);
} else {
  mkdirSync(dirname(OUT_PATH), { recursive: true });
  writeFileSync(OUT_PATH, csv, 'utf8');
  process.stdout.write(`wrote ${OUT_PATH}: ${count} vectors\n`);
}
