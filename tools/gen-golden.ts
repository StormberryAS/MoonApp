#!/usr/bin/env node
/**
 * gen-golden.ts: the illumination golden corpus for the Android port.
 * ================================================================
 * Writes android/app/src/test/resources/mooncalc-golden.csv: 60 instants spread over 2026 (five a
 * month, at every hour band the old phase bug broke), each with SunCalc.getMoonIllumination's
 * fraction and phase to nine decimals. MoonCalcGoldenTest holds MoonCalc.illumination to it.
 *
 * The values come from the vendored suncalc.js, the exact library the web page ships, because the
 * Kotlin MoonCalc is a transcription of it: the corpus is a parity contract, not an almanac.
 *
 * Usage, from anywhere (Node 24 runs the TypeScript directly):
 *   node tools/gen-golden.ts           write the CSV
 *   node tools/gen-golden.ts --check   write nothing; exit 1 unless the committed CSV is byte for
 *                                      byte what this script writes
 * tests/golden.test.ts and tools/build-ts.sh --check run the --check mode.
 *
 * TYPESCRIPT (22 October 2026). Until then this was tools/gen-golden.mjs, which read and wrote
 * $HOME/ThomassenPovoaHoldingAS/StormberryAS/GitHub/MoonApp, a path that no longer exists, and
 * loaded suncalc.js with eval. It now works relative to the repository and runs the vendored file's
 * own text with a plain object standing in for window, in this realm (SunApp's gen-solar-golden.ts
 * does the same), so the Dates it returns are this realm's. The data rows are byte for byte what
 * the JavaScript wrote; the only change to the CSV is the generator's name in its second comment
 * line.
 * ================================================================
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const REPO_ROOT = resolve(import.meta.dirname, '..');
const OUT_PATH = join(REPO_ROOT, 'android', 'app', 'src', 'test', 'resources', 'mooncalc-golden.csv');

interface SunCalcLike {
  getMoonIllumination(date: Date): { fraction: number; phase: number; angle: number };
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

function build(): { csv: string; count: number } {
  const rows = ['# Golden vectors for MoonCalc.illumination, generated from SunCalc 1.8.0 (suncalc.js),',
    '# the exact library the web app ships. Regenerate with tools/gen-golden.ts.',
    '# iso_utc,fraction,phase'];
  // Spread across a full year, all hours, including the times of day the old bug broke.
  const stamps: Date[] = [];
  for (let m = 0; m < 12; m++) {
    for (const [d, h] of [[3, 0], [9, 6], [16, 12], [23, 18], [27, 3]] as const) {
      stamps.push(new Date(Date.UTC(2026, m, d, h, 17, 0)));
    }
  }
  for (const dt of stamps) {
    const i = SunCalc.getMoonIllumination(dt);
    rows.push(`${dt.toISOString().replace('.000', '')},${i.fraction.toFixed(9)},${i.phase.toFixed(9)}`);
  }
  return { csv: rows.join('\n') + '\n', count: stamps.length };
}

const { csv, count } = build();
if (process.argv.includes('--check')) {
  let committed = '';
  try { committed = readFileSync(OUT_PATH, 'utf8'); } catch { committed = ''; }
  if (committed !== csv) {
    process.stderr.write(`${OUT_PATH} is NOT what tools/gen-golden.ts writes: run it and review the diff\n`);
    process.exit(1);
  }
  process.stdout.write(`golden check OK: ${OUT_PATH} matches, ${count} data rows\n`);
} else {
  mkdirSync(dirname(OUT_PATH), { recursive: true });
  writeFileSync(OUT_PATH, csv, 'utf8');
  process.stdout.write(`wrote ${OUT_PATH}: ${count} golden vectors\n`);
}
