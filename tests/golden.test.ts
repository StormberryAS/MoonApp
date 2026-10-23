// The parity contracts with the Android port: the three suncalc corpora are what the vendored
// suncalc.js gives and the three F-1 to F-5 corpora what the compiled page gives (tools/gen-*.ts
// --check), the web catalogue and the Android asset are the same data
// (tools/check-city-parity.py), and the page itself computes what webparity-golden.csv says for
// every one of its 81 rows: to the millisecond through its own moonTimesFrom, and to the second on
// screen through onCalculate. Added 22 October 2026 with the TypeScript move, on SunApp's
// tests/golden.test.ts. gen-webparity-golden.ts keeps its own copy of the page's moonTimesFrom
// and finds the local day its own way; this file is what keeps the copy and the page from
// drifting apart.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { loadPage } from './load.ts';
import { defined } from './helpers.ts';

const root = new URL('../', import.meta.url).pathname;
const gen = (name: string): string => execFileSync(process.execPath, [`${root}tools/${name}.ts`, '--check'], { encoding: 'utf8' });

test('mooncalc-golden.csv is exactly what tools/gen-golden.ts writes from suncalc.js', () => {
  assert.match(gen('gen-golden'), /golden check OK: .* 60 data rows/);
});

test('moontimes-golden.csv is exactly what tools/gen-moontimes-golden.ts writes from suncalc.js', () => {
  assert.match(gen('gen-moontimes-golden'), /golden check OK: .* 48 data rows/);
});

test('webparity-golden.csv is exactly what tools/gen-webparity-golden.ts writes from suncalc.js', () => {
  assert.match(gen('gen-webparity-golden'), /golden check OK: .* 81 data rows/);
});

test('the F-1 to F-5 parity corpora are exactly what tools/gen-lunar-parity.ts writes from the page', () => {
  const out = gen('gen-lunar-parity');
  assert.match(out, /golden check OK: .*lunation-parity\.csv matches, 487 data rows/);
  assert.match(out, /golden check OK: .*transit-parity\.csv matches, 820 data rows/);
  assert.match(out, /golden check OK: .*lunar-daykind-parity\.csv matches, 355 data rows/);
});

test('cities.js and data/cities.tsv hold the same catalogue', () => {
  const out = execFileSync('python3', ['-I', `${root}tools/check-city-parity.py`], { encoding: 'utf8' });
  assert.match(out, /^city parity OK: 25007 rows/);
});

interface ParityRow { place: string; lat: number; lon: number; tz: string; date: string; rise: string; set: string; flag: string }
const parityRows = (): ParityRow[] => {
  const lines = readFileSync(new URL('../android/app/src/test/resources/webparity-golden.csv', import.meta.url), 'utf8')
    .split('\n').filter((l) => l && !l.startsWith('#'));
  return lines.map((l) => {
    const c = l.split(',');
    const at = (i: number): string => defined(c[i], `column ${i} of ${l}`);
    return { place: at(0), lat: Number(at(1)), lon: Number(at(2)), tz: at(3), date: at(4), rise: at(5), set: at(6), flag: at(7) };
  });
};

test("the page's moonTimesFrom gives webparity-golden.csv's instants for all 81 rows", () => {
  const rows = parityRows();
  assert.equal(rows.length, 81);
  const page = loadPage();
  const moonTimesFrom = page.fn('moonTimesFrom'), localDayWindow = page.fn('localDayWindow');
  const iso = (ms: number | null): string => (ms === null ? '' : new Date(ms).toISOString().replace('.000', ''));
  const bad: string[] = [];
  for (const r of rows) {
    const [y, m, d] = r.date.split('-').map(Number) as [number, number, number];
    const day = localDayWindow(y, m, d, r.tz);
    const t = moonTimesFrom(day.start, r.lat, r.lon, (day.end.getTime() - day.start.getTime()) / 3600000);
    const rise = iso(t.rise ? t.rise.getTime() : null), set = iso(t.set ? t.set.getTime() : null);
    if (rise !== r.rise || set !== r.set) bad.push(`${r.place} ${r.date}: page ${rise} ${set}, csv ${r.rise} ${r.set}`);
  }
  assert.deepEqual(bad, []);
});

test('the page shows those instants on screen, in the place\'s zone, for all 81 rows', async () => {
  const rows = parityRows();
  const page = loadPage();
  const onCalculate = page.fn('onCalculate');
  const state = page.get('state');
  const els = page.get('els');
  const hms = (iso: string, tz: string): string => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZone: tz }).format(new Date(iso));
  const bad: string[] = [];
  for (const r of rows) {
    state.tab = 'city';
    state.city = { name: r.place, fold: '', country: 'Test', cfold: '', lat: r.lat, lon: r.lon, tz: r.tz, alt: '' };
    els.dateInput.value = r.date;
    await onCalculate();
    const shown = [els.resMoonrise.textContent, els.resMoonset.textContent];
    if (r.rise && shown[0] !== hms(r.rise, r.tz)) bad.push(`${r.place} ${r.date} rise: page ${shown[0]}, csv ${hms(r.rise, r.tz)}`);
    if (r.set && shown[1] !== hms(r.set, r.tz)) bad.push(`${r.place} ${r.date} set: page ${shown[1]}, csv ${hms(r.set, r.tz)}`);
    if (!r.rise && !r.set) {
      // Neither crossing: the page shows a polar label in both cards (it decides which from the
      // moon's peak altitude, not from the flag column, which is suncalc's own).
      for (const s of shown) assert.match(s, /Always (Up|Down)$/, `${r.place} ${r.date}`);
    } else {
      if (!r.rise) assert.equal(shown[0], '\u2014', `${r.place} ${r.date}: no moonrise`);
      if (!r.set) assert.equal(shown[1], '\u2014', `${r.place} ${r.date}: no moonset`);
    }
  }
  assert.deepEqual(bad, []);
});
