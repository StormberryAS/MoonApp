// The parity contracts with the Android port: the three corpora are what the vendored suncalc.js
// gives (tools/gen-*.ts --check), the web catalogue and the Android asset are the same data
// (tools/check-city-parity.py), and the page itself computes what webparity-golden.csv says for
// every one of its 81 rows: to the millisecond through its own moonTimesFrom, and to the second on
// screen through onCalculate. Added 22 October 2026 with the TypeScript move, on SunApp's
// tests/golden.test.ts. The generators keep their own copy of the page's moonTimesFrom and
// cityUtcOffsetMs; this file is what keeps the copies and the page from drifting apart.

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

/** The city's local midnight of date as an instant: the page's cityUtcOffsetMs, restated. */
const cityMidnight = (tz: string, date: string): number => {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number];
  const noon = new Date(Date.UTC(y, m - 1, d, 12));
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: tz, hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(noon).map((x) => [x.type, x.value]));
  const off = Date.UTC(Number(p.year), Number(p.month) - 1, Number(p.day), Number(p.hour), Number(p.minute), Number(p.second)) - Math.floor(noon.getTime() / 1000) * 1000;
  return Date.UTC(y, m - 1, d) - off;
};

test("the page's moonTimesFrom gives webparity-golden.csv's instants for all 81 rows", () => {
  const rows = parityRows();
  assert.equal(rows.length, 81);
  const moonTimesFrom = loadPage().fn('moonTimesFrom');
  const iso = (ms: number | null): string => (ms === null ? '' : new Date(ms).toISOString().replace('.000', ''));
  const bad: string[] = [];
  for (const r of rows) {
    const t = moonTimesFrom(new Date(cityMidnight(r.tz, r.date)), r.lat, r.lon);
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
