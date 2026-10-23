// The browser smoke test: the page as production serves it (tests/serve.py, with the zone's headers
// and the _config.yml exclusions), in headless Chromium. Added 22 October 2026 with the TypeScript
// move, on SunApp's tests/smoke.ts.
//
//   node tests/smoke.ts [--chromium /usr/bin/chromium] [--shots DIR]
//
// Checks: every scenario of tests/scenarios.ts gives, step for step, the snapshot frozen in
// tests/page-vectors.json, which was captured from the MoonApp 90d11dd page (the last hand-written
// app.js) and captured again once fixes F-1 to F-3 and F-5 had changed results on purpose (every changed
// field reviewed), so the page behaves as 90d11dd did apart from those fixes; no request leaves the
// origin, and no
// console error or policy violation under the zone's header; the compiled app.js is what is served,
// under its current stamp; the sources and tooling are not served; the real dropdown lists what
// tests/search-vectors.json says for 20 of its queries; the page shows
// webparity-golden.csv's moonrise and moonset for all 91 rows, and transit-parity.csv's transits and
// lunar-daykind-parity.csv's labels for every row, in the browser; no sideways scroll at
// 320, 390 and 1280 px with a result shown or the city list open. Prints one line per check and
// exits 1 on the first failure.

import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import assert from 'node:assert/strict';
import { launchChromium, openPage } from './cdp.ts';
import type { Page } from './cdp.ts';
import { clockAt, NOW, pickCity, press, runScenarios } from './scenarios.ts';
import type { Snap, Vector } from './scenarios.ts';
import type { SearchVector } from './capture-search.ts';
import { startServer } from './server.ts';
import { defined } from './helpers.ts';

const arg = (name: string): string | null => {
  const i = process.argv.indexOf(name);
  return i > 0 ? (process.argv[i + 1] ?? null) : null;
};
const chromium = arg('--chromium') ?? '/usr/bin/chromium';
const shots = arg('--shots');
if (shots) mkdirSync(shots, { recursive: true });
const work = mkdtempSync(join(tmpdir(), 'moonapp-smoke-'));
const repo = new URL('../', import.meta.url);
const read = (p: string): string => readFileSync(new URL(p, repo), 'utf8');

const server = await startServer();
const base = server.base;
const browser = await launchChromium({ chromium, out: work });

async function cleanup(): Promise<void> {
  await browser.close();
  server.stop();
  rmSync(work, { recursive: true, force: true });
}

let passed = 0;
async function check(name: string, fn: () => Promise<void>): Promise<void> {
  try {
    await fn();
    passed += 1;
    console.log(`ok ${passed} ${name}`);
  } catch (err) {
    console.log(`not ok ${name}\n  ${String((err as Error | null)?.stack || err).split('\n').join('\n  ')}`);
    await cleanup();
    process.exit(1);
  }
}

/** Nothing off the origin, no error, no policy refusal. */
function quiet(p: Page, where: string): void {
  assert.deepEqual(p.log.offsite, [], `${where}: requests off the origin`);
  assert.deepEqual(p.log.errors, [], `${where}: console or page errors`);
  assert.deepEqual(p.log.csp, [], `${where}: policy violations`);
}

await check('the page as 90d11dd left it, with fixes F-1 to F-3 and F-5: every scenario step gives its frozen snapshot', async () => {
  const frozen = JSON.parse(read('tests/page-vectors.json')) as { capturedFrom: string; vectors: Vector[] };
  assert.match(frozen.capturedFrom, /^MoonApp 90d11dd with fixes F-1 to F-3 and F-5 /);
  const now = await runScenarios(browser, base, { after: (p, sc) => quiet(p, sc) });
  assert.deepEqual(now.map((v) => v.name), frozen.vectors.map((v) => v.name), 'the same steps, in the same order');
  const diffs: string[] = [];
  now.forEach((v, i) => {
    const want: Snap = defined(frozen.vectors[i], `vector ${i}`).snap;
    for (const k of new Set([...Object.keys(want), ...Object.keys(v.snap)])) {
      if (want[k] !== v.snap[k]) diffs.push(`${v.name}: ${k}\n      frozen: ${JSON.stringify(want[k])}\n      now:    ${JSON.stringify(v.snap[k])}`);
    }
  });
  assert.deepEqual(diffs, [], `${diffs.length} differences`);
  console.log(`  (${now.length} snapshots match)`);
});

await check('the compiled app.js is what is served, under its current stamp', async () => {
  const res = await fetch(`${base}app.js`);
  assert.equal(res.status, 200);
  assert.equal(await res.text(), read('app.js'));
  const stamp = createHash('sha256').update(readFileSync(new URL('app.js', repo))).digest('hex').slice(0, 8);
  const html = await (await fetch(base)).text();
  assert.ok(html.includes(`<script src="app.js?v=${stamp}"></script>`));
});

await check('sources, tooling and tests are not served; the site is', async () => {
  const hidden = ['ts/app.ts', 'ts/app-dom.d.ts', 'ts/cities.d.ts', 'ts/suncalc.d.ts', 'tests/smoke.ts', 'tests/page-vectors.json', 'tests/search-vectors.json',
    'tests/serve.py', 'tools/build-ts.sh', 'tools/gen-golden.ts', 'tools/gen-moontimes-golden.ts', 'tools/gen-webparity-golden.ts', 'tools/check-city-parity.py',
    'package.json', 'package-lock.json', 'tsconfig.json', '.npmrc', '.gitattributes', 'node_modules/typescript/package.json', 'bump_assets.py', 'README.md',
    'android/gradlew', 'android/app/build.gradle.kts', 'fastlane/metadata/android/en-US/full_description.txt', 'zapstore.yaml', 'DISCLAIMER.md', 'LICENSE',
    'jsconfig.json', 'types/app.d.ts', 'tools/gen-lunar-parity.ts', 'data/cities.tsv',
    'android/app/src/test/resources/transit-parity.csv',
    'tools/gen-search-parity.ts', 'android/app/src/test/resources/citysearch-golden.csv'];
  for (const path of hidden) assert.equal((await fetch(`${base}${path}`)).status, 404, `${path} is not served`);
  for (const path of ['', 'index.html', 'app.js', 'cities.js', 'suncalc.js', 'style.css', 'disclaimer.html', 'favicon.svg', 'fonts/inter/inter.css', 'apple-touch-icon.png']) {
    assert.equal((await fetch(`${base}${path}`)).status, 200, `${path || '/'} is served`);
  }
});

await check('the real dropdown lists what search-vectors.json says, for 20 of its queries', async () => {
  const frozen = JSON.parse(read('tests/search-vectors.json')) as { vectors: SearchVector[] };
  const queries = ['tromso', 'tromsø', 'askoy', 'kleppesto', 'erdal', 'gothenburg', 'cologne', 'herat', 'sao paulo', 'new york', 'norway', '&', '<b>', 'a', 'zz',
    '  bergen  ', 'BERGEN', 'san', 'new', String.fromCharCode(0x301)];
  const p = await openPage(browser, base, { timezone: 'Europe/Oslo', init: clockAt(NOW) });
  for (const q of queries) {
    const want = frozen.vectors.find((v) => v.q === q);
    assert.ok(want, `${JSON.stringify(q)} is in search-vectors.json`);
    await p.type('#city-search', q);
    const got: { open: boolean; rows: string[][] } = await p.ev(`(() => {
      const dd = document.getElementById('city-dropdown');
      const rows = [...dd.querySelectorAll('li')].map((li) => [li.querySelector('.city-name').textContent, li.querySelector('.city-country').textContent]);
      return { open: !dd.hidden, rows: dd.hidden ? [] : rows };
    })()`);
    assert.equal(got.open, want.open, `${JSON.stringify(q)}: list open`);
    assert.deepEqual(got.rows, want.rows.map((r) => [r[0], r[1]]), `${JSON.stringify(q)}: rows`);
  }
  quiet(p, 'dropdown');
  await p.close();
});

await check('the page shows webparity-golden.csv\'s moonrise and moonset for all 91 rows, in the browser', async () => {
  const rows = read('android/app/src/test/resources/webparity-golden.csv').split('\n').filter((l) => l && !l.startsWith('#')).map((l) => l.split(','));
  assert.equal(rows.length, 91);
  const p = await openPage(browser, base, { timezone: 'Europe/Oslo', init: clockAt(NOW) });
  const got: string[][] = await p.ev(`(async () => {
    const out = [];
    for (const [place, lat, lon, tz, date] of ${JSON.stringify(rows)}) {
      state.tab = 'city';
      state.city = { name: place, fold: '', country: 'Test', cfold: '', lat: Number(lat), lon: Number(lon), tz, alt: '' };
      els.dateInput.value = date;
      await onCalculate();
      out.push([els.resMoonrise.textContent, els.resMoonset.textContent]);
    }
    return out;
  })()`);
  const hms = (iso: string, tz: string): string => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZone: tz }).format(new Date(iso));
  const bad: string[] = [];
  rows.forEach((r, i) => {
    const [place, , , tz, date, rise, set] = r as [string, string, string, string, string, string, string];
    const shown = defined(got[i], `row ${i}`);
    if (rise && shown[0] !== hms(rise, tz)) bad.push(`${place} ${date} rise: page ${shown[0]}, csv ${hms(rise, tz)}`);
    if (set && shown[1] !== hms(set, tz)) bad.push(`${place} ${date} set: page ${shown[1]}, csv ${hms(set, tz)}`);
  });
  assert.deepEqual(bad, []);
  quiet(p, 'web parity');
  await p.close();
});

await check('the page shows transit-parity.csv\'s transits and lunar-daykind-parity.csv\'s labels for every row, in the browser', async () => {
  const rows = (f: string): string[][] => read(`android/app/src/test/resources/${f}`).split('\n').filter((l) => l && !l.startsWith('#')).map((l) => l.split(','));
  const transits = rows('transit-parity.csv');
  const kinds = rows('lunar-daykind-parity.csv');
  assert.equal(transits.length, 820);
  assert.equal(kinds.length, 355);
  const p = await openPage(browser, base, { timezone: 'Europe/Oslo', init: clockAt(NOW) });
  const got: { transits: string[][]; kinds: string[] } = await p.ev(`(async () => {
    const show = async (place, lat, lon, tz, date) => {
      state.tab = 'city';
      state.city = { name: place, fold: '', country: 'Test', cfold: '', lat: Number(lat), lon: Number(lon), tz, alt: '' };
      els.dateInput.value = date;
      await onCalculate();
    };
    const transits = [], kinds = [];
    for (const [place, lat, lon, tz, date] of ${JSON.stringify(transits)}) { await show(place, lat, lon, tz, date); transits.push([els.resTransit.textContent, els.resMoonrise.textContent]); }
    for (const [place, lat, lon, tz, date] of ${JSON.stringify(kinds)}) {
      await show(place, lat, lon, tz, date);
      const h = els.resMoonrise.textContent;
      kinds.push(/Always Up/.test(h) ? 'ALWAYS_UP' : /Always Down/.test(h) ? 'ALWAYS_DOWN' : 'NORMAL');
    }
    return { transits, kinds };
  })()`);
  const hms = (iso: string, tz: string): string => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, timeZone: tz }).format(new Date(iso));
  const bad: string[] = [];
  transits.forEach((r, i) => {
    const [place, , , tz, date, , , at] = r as [string, string, string, string, string, string, string, string];
    // A blank transit shows the dash. A day the card labels Always Down never has a transit (F-3):
    // only a culmination above the rise threshold counts.
    const [shown, rise] = defined(got.transits[i], `row ${i}`) as [string, string];
    const want = at ? hms(at, tz) : String.fromCharCode(0x2014);
    if (shown !== want) bad.push(`${place} ${date} transit: page ${shown}, csv ${want}`);
    if (at && /Always Down/.test(rise)) bad.push(`${place} ${date}: a transit on an Always Down day`);
  });
  kinds.forEach((r, i) => { if (got.kinds[i] !== r[5]) bad.push(`${r[0]} ${r[4]} kind: page ${got.kinds[i]}, csv ${r[5]}`); });
  assert.deepEqual(bad, []);
  quiet(p, 'lunar parity');
  await p.close();
});

await check('no sideways scroll at 320, 390 and 1280 px, with a result shown and with the city list open', async () => {
  for (const width of [320, 390, 1280]) {
    const p = await openPage(browser, base, { width, timezone: 'Europe/Oslo', init: clockAt(NOW) });
    const wide = (): Promise<boolean> => p.ev('document.documentElement.scrollWidth > document.documentElement.clientWidth');
    assert.equal(await wide(), false, `${width}px as loaded`);
    await p.type('#city-search', 'santiago');
    assert.equal(await wide(), false, `${width}px with the list open`);
    await pickCity(p, 'longyearbyen', 'Longyearbyen', 'Svalbard and Jan Mayen');
    await p.setDate('#date-input', '2026-01-03');
    await press(p, '#calculate-btn');
    assert.equal(await wide(), false, `${width}px with a polar label shown`);
    await pickCity(p, 'anchorage', 'Anchorage, AK', 'USA');
    await p.setDate('#date-input', '2026-06-21');
    await press(p, '#calculate-btn');
    assert.equal(await wide(), false, `${width}px with a result shown`);
    if (shots) await p.shot(join(shots, `anchorage-${width}.png`));
    quiet(p, `${width}px`);
    await p.close();
  }
});

console.log(`smoke: all ${passed} checks passed`);
await cleanup();
