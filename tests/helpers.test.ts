// The page's own helpers, called in Node through tests/load.ts: the number parser, the date and
// time formatters, the zone abbreviation (its error path included), the HTML escape, the offline
// nearest-city zone lookup, the phase names at every band edge and the next new and full moon.
// Added 22 October 2026 with the TypeScript move, on SunApp's tests/helpers.test.ts. Every
// expected value below was also produced by the 90d11dd app.js (the hand-written JavaScript the
// TypeScript replaced: MOONAPP_PAGE_ROOT pointed at that tree, see tests/load.ts), so these vectors
// pin the behaviour across the move.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPage } from './load.ts';

const page = loadPage();
/** Calls a page function by name with JSON-able arguments; the result comes back through JSON. */
const call = <T>(fn: string, ...args: unknown[]): T => {
  page.set('__args', JSON.stringify(args));
  return JSON.parse(page.run<string>(`JSON.stringify(${fn}(...JSON.parse(__args)) ?? null)`)) as T;
};
const ch = (...codes: number[]): string => String.fromCharCode(...codes);
const MINUS = ch(0x2212);

test('parseDecimal: the shared Labs parser', () => {
  const cases: Array<[string | null, number, number, number | null]> = [
    ['60,39', -90, 90, 60.39],
    ['60.39', -90, 90, 60.39],
    [' 60,39 ', -90, 90, 60.39],
    [`${MINUS}33.87`, -90, 90, -33.87],
    [`${ch(0xFF0D)}33,87`, -90, 90, -33.87],
    [`${ch(0x2013)}33.87`, -90, 90, -33.87],
    ['+5', -90, 90, 5],
    ['.5', -90, 90, 0.5],
    ['5.', -90, 90, 5],
    // Whitespace inside is dropped before parsing: "6 0.39" reads as 60.39, as in 90d11dd.
    ['6 0.39', -90, 90, 60.39],
    ['90', -90, 90, 90],
    ['-90', -90, 90, -90],
    ['90.0001', -90, 90, null],
    ['-180.5', -180, 180, null],
    ['1e2', -180, 180, null],
    ['0x10', -180, 180, null],
    ['1,5,3', -90, 90, null],
    ['60,', -90, 90, null],
    [',5', -90, 90, null],
    ['N60', -90, 90, null],
    ['abc', -90, 90, null],
    ['', -90, 90, null],
    ['   ', -90, 90, null],
    // Arabic-Indic digits are not ASCII digits: refused, as in 90d11dd.
    [ch(0x0666, 0x0660), -90, 90, null],
    [null, -90, 90, null],
  ];
  for (const [text, min, max, want] of cases) assert.equal(call('parseDecimal', text, min, max), want, JSON.stringify(text));
  assert.equal(page.run('parseDecimal(undefined, -90, 90)'), null);
});

test('formatDate: DD/Month/YYYY from the UTC date parts', () => {
  const months = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  months.forEach((name, i) => {
    assert.equal(page.run(`formatDate(new Date(Date.UTC(2026, ${i}, 5, 12)))`), `05/${name}/2026`);
  });
  assert.equal(page.run('formatDate(new Date(Date.UTC(1900, 0, 1, 12)))'), '01/January/1900');
  assert.equal(page.run('formatDate(new Date(Date.UTC(2100, 11, 31, 12)))'), '31/December/2100');
  // An invalid Date: the month lookup misses and the JavaScript printed "undefined" (kept).
  assert.equal(page.run('formatDate(new Date(NaN))'), 'NaN/undefined/NaN');
});

test('formatTime: HH:MM:SS in the target zone, not the machine zone', () => {
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 5, 21, 22, 5, 3)), 'Europe/Oslo')"), '00:05:03');
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 5, 21, 22, 5, 3)), 'UTC')"), '22:05:03');
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 0, 15, 10, 0, 0)), 'Pacific/Apia')"), '23:00:00');
  // Both European DST changes in 2026: 29 March and 25 October.
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 2, 29, 0, 59, 59)), 'Europe/Oslo')"), '01:59:59');
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 2, 29, 1, 0, 0)), 'Europe/Oslo')"), '03:00:00');
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 9, 25, 0, 30, 0)), 'Europe/Oslo')"), '02:30:00');
  assert.equal(page.run("formatTime(new Date(Date.UTC(2026, 9, 25, 1, 30, 0)), 'Europe/Oslo')"), '02:30:00');
});

test('getTimezoneAbbreviation: Intl short names, and "" on every error path', () => {
  assert.equal(call('getTimezoneAbbreviation', 'America/New_York', '2026-07-01'), 'EDT');
  assert.equal(call('getTimezoneAbbreviation', 'America/New_York', '2026-01-15'), 'EST');
  assert.equal(call('getTimezoneAbbreviation', 'Europe/Oslo', '2026-01-15'), 'GMT+1');
  assert.equal(call('getTimezoneAbbreviation', 'Europe/Oslo', '2026-07-01'), 'GMT+2');
  // A zone Intl does not know throws a RangeError inside; the catch gives "".
  assert.equal(call('getTimezoneAbbreviation', 'Not/AZone', '2026-01-15'), '');
  // An empty date field: Date.UTC of undefined parts is NaN, formatToParts throws, "" again.
  assert.equal(call('getTimezoneAbbreviation', 'Europe/Oslo', ''), '');
  assert.equal(call('getTimezoneAbbreviation', 'Europe/Oslo', '2026'), '');
});

test('escapeHtml: the four characters a city name could break markup with', () => {
  assert.equal(call('escapeHtml', '& < > "'), '&amp; &lt; &gt; &quot;');
  assert.equal(call('escapeHtml', "Côte d'Ivoire"), "Côte d'Ivoire");
  assert.equal(call('escapeHtml', '<b>&amp;</b>'), '&lt;b&gt;&amp;amp;&lt;/b&gt;');
});

test('nearestCityTimezone: offline, at the poles and across the antimeridian', () => {
  const cases: Array<[number, number, string]> = [
    [60.39, 5.32, 'Europe/Oslo'],
    [78.2233, 15.6469, 'Arctic/Longyearbyen'],
    [90, 0, 'Arctic/Longyearbyen'],
    [-90, 0, 'Atlantic/South_Georgia'],
    [0, 180, 'Pacific/Tarawa'],
    [0, -180, 'Pacific/Tarawa'],
    [-18.14, 179.99, 'Pacific/Fiji'],
    [-13.83, -171.77, 'Pacific/Apia'],
    [1.8721, -157.4278, 'Pacific/Honolulu'],
  ];
  for (const [lat, lon, want] of cases) assert.equal(call('nearestCityTimezone', lat, lon), want, `${lat}, ${lon}`);
});

test('getMoonPhaseName: the eight bands, at and either side of every edge', () => {
  const cases: Array<[number, string]> = [
    [0, 'New Moon'], [0.0624999, 'New Moon'], [0.0625, 'Waxing Crescent'],
    [0.1874999, 'Waxing Crescent'], [0.1875, 'First Quarter'],
    [0.3124999, 'First Quarter'], [0.3125, 'Waxing Gibbous'],
    [0.4374999, 'Waxing Gibbous'], [0.4375, 'Full Moon'],
    [0.5, 'Full Moon'], [0.5624999, 'Full Moon'], [0.5625, 'Waning Gibbous'],
    [0.6874999, 'Waning Gibbous'], [0.6875, 'Last Quarter'],
    [0.8124999, 'Last Quarter'], [0.8125, 'Waning Crescent'],
    [0.9374999, 'Waning Crescent'], [0.9375, 'New Moon'], [0.9999999, 'New Moon'],
  ];
  for (const [phase, want] of cases) assert.equal(call('getMoonPhaseName', phase), want, String(phase));
});

const getNextLunations = page.fn('getNextLunations');
const formatDate = page.fn('formatDate');
const localYmd = page.fn('localYmd');
const lunarTransitFrom = page.fn('lunarTransitFrom');
const localDayWindow = page.fn('localDayWindow');
const SunCalc = page.get('SunCalc');
/** The noon UTC anchor onCalculate passes for a YYYY-MM-DD date (any year the field takes). */
const noonOf = (date: string): Date => {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number];
  return new Date(Date.UTC(y, m - 1, d, 12));
};
/** A local date as a number that orders as dates do (the page's dayKey). */
const key = ([y, m, d]: [number, number, number]): number => (y * 100 + m) * 100 + d;

/** getNextLunations for date in zone tz: both dates as the page shows them, and both instants. */
const lunations = (date: string, tz: string): string => {
  const r = getNextLunations(noonOf(date), tz);
  const iso = (t: Date | null): string => (t ? t.toISOString().slice(0, 19) + 'Z' : '-');
  return [r.nextNew ? formatDate(r.nextNew) : 'Not found', iso(r.newAt), r.nextFull ? formatDate(r.nextFull) : 'Not found', iso(r.fullAt)].join(' | ');
};

test('getNextLunations: the next new and full moon at or after the start of the day, dated in the zone (F-1)', () => {
  // Until fix F-1 the page sampled at 12:00 UTC once a day and showed the first noon past the
  // event, a UTC date: the full moon of 1 February 2026 (17:33 UTC) showed as the 2nd, and from
  // 1 January 2026 the next new moon showed as the 19th. Each row below was checked against the
  // moment itself (the last test of this file).
  const cases: Array<[string, string, string]> = [
    ['2026-01-01', 'Europe/Oslo', '18/January/2026 | 2026-01-18T20:16:48Z | 03/January/2026 | 2026-01-03T07:13:23Z'],
    // A full moon later on the selected day is today's (decision D-4).
    ['2026-02-01', 'Europe/Oslo', '17/February/2026 | 2026-02-17T13:18:47Z | 01/February/2026 | 2026-02-01T17:33:01Z'],
    ['2026-02-02', 'Europe/Oslo', '17/February/2026 | 2026-02-17T13:18:47Z | 03/March/2026 | 2026-03-03T07:36:04Z'],
    // 23:37 UTC on 29 June is 30 June in Oslo, still the 29th in UTC.
    ['2026-06-21', 'Europe/Oslo', '14/July/2026 | 2026-07-14T07:18:30Z | 30/June/2026 | 2026-06-29T23:37:23Z'],
    ['2026-06-21', 'UTC', '14/July/2026 | 2026-07-14T07:18:30Z | 29/June/2026 | 2026-06-29T23:37:23Z'],
    ['2026-10-22', 'Europe/Oslo', '09/November/2026 | 2026-11-09T07:06:13Z | 26/October/2026 | 2026-10-26T08:58:38Z'],
    // 07:13 UTC on 3 January is 23:13 on the 2nd in Los Angeles: from the 1st it is the 2nd...
    ['2026-01-01', 'America/Los_Angeles', '18/January/2026 | 2026-01-18T20:16:48Z | 02/January/2026 | 2026-01-03T07:13:23Z'],
    // ...and from the 3rd it is already past, so the next is February's.
    ['2026-01-03', 'America/Los_Angeles', '18/January/2026 | 2026-01-18T20:16:48Z | 01/February/2026 | 2026-02-01T17:33:01Z'],
    ['2026-02-01', 'Pacific/Kiritimati', '18/February/2026 | 2026-02-17T13:18:47Z | 02/February/2026 | 2026-02-01T17:33:01Z'],
    // Before fix F-1 an event between 00:00 and 12:00 UTC of the selected day came before the
    // first sample and was skipped: from 3 January 2026 the page showed February's full moon.
    ['2026-01-03', 'Arctic/Longyearbyen', '18/January/2026 | 2026-01-18T20:16:48Z | 03/January/2026 | 2026-01-03T07:13:23Z'],
    // Kiritimati (UTC+14) begins its day at 10:00 UTC the day before, so the samples start two
    // days early: 10:48 UTC on 18 April 2000 is 00:48 on the 19th there. With one day early the
    // build first found 18 May (caught in review on 23 October 2026), as again on 14 January 2002.
    ['2000-04-19', 'Pacific/Kiritimati', '04/May/2000 | 2000-05-04T03:00:29Z | 19/April/2000 | 2000-04-18T10:48:30Z'],
    ['2002-01-14', 'Pacific/Kiritimati', '14/January/2002 | 2002-01-13T10:04:18Z | 29/January/2002 | 2002-01-28T21:58:24Z'],
    ['2100-12-31', 'Pacific/Kiritimati', '31/December/2100 | 2100-12-30T19:03:31Z | 15/January/2101 | 2101-01-15T09:07:43Z'],
    ['1900-01-01', 'Europe/Oslo', '01/January/1900 | 1900-01-01T19:04:43Z | 15/January/1900 | 1900-01-15T21:36:18Z'],
    // An unknown zone falls back to UTC dates, as zoneOffsetMs falls back to a UTC day.
    ['2026-06-21', 'Not/AZone', '14/July/2026 | 2026-07-14T07:18:30Z | 29/June/2026 | 2026-06-29T23:37:23Z'],
  ];
  for (const [date, tz, want] of cases) assert.equal(lunations(date, tz), want, `${date} ${tz}`);
});

test('getNextLunations: each result is the moment the phase crosses, and none is skipped', () => {
  // Checked against the phase itself rather than frozen values: the phase is on either side of
  // the target a minute before and after the instant, the instant falls on or after the
  // selected day in the zone, and an hourly scan from the start of that day finds no earlier
  // crossing. Every third day of 2026 in three zones.
  const bad: string[] = [];
  const phase = (t: number): number => SunCalc.getMoonIllumination(new Date(t)).phase;
  type Check = [string, Date | null, Date | null, (p: number) => boolean, (p: number) => boolean];
  for (const tz of ['Europe/Oslo', 'America/Los_Angeles', 'Pacific/Kiritimati']) {
    for (let k = 0; k < 365; k += 3) {
      const noon = Date.UTC(2026, 0, 1 + k, 12);
      const day = new Date(noon).toISOString().slice(0, 10);
      const dayK = key([2026, new Date(noon).getUTCMonth() + 1, new Date(noon).getUTCDate()]);
      const r = getNextLunations(new Date(noon), tz);
      const checks: Check[] = [
        ['new', r.newAt, r.nextNew, (p) => p > 0.75, (p) => p < 0.25],
        ['full', r.fullAt, r.nextFull, (p) => p < 0.5, (p) => p >= 0.5],
      ];
      for (const [what, at, shown, before, after] of checks) {
        if (!at || !shown) { bad.push(`${day} ${tz} ${what}: none`); continue; }
        const t = at.getTime();
        if (!(before(phase(t - 60000)) && after(phase(t + 60000)))) bad.push(`${day} ${tz} ${what}: not a crossing`);
        const local = localYmd(at, tz);
        if (key(local) < dayK) bad.push(`${day} ${tz} ${what}: before the day`);
        if (shown.toISOString().slice(0, 10) !== local.map((n, i) => String(n).padStart(i ? 2 : 4, '0')).join('-')) bad.push(`${day} ${tz} ${what}: shown date`);
        for (let s = noon - 27 * 3600000; s + 3600000 < t - 3600000; s += 3600000) {
          if (before(phase(s)) && after(phase(s + 3600000)) && key(localYmd(new Date(s), tz)) >= dayK) { bad.push(`${day} ${tz} ${what}: skipped one at ${new Date(s).toISOString()}`); break; }
        }
      }
    }
  }
  assert.deepEqual(bad, []);
});

test('getNextLunations: years past 9999 order as dates, not as text', () => {
  // Until 23 October 2026 the local dates were compared as "YYYY-MM-DD" strings, and
  // "10000-01-02" sorts before "9999-12-31": Oslo on 31 December 9999 showed "Not found" for both
  // and on 20 January 10000 a new moon of the 18th, before the chosen day. The date field takes
  // years up to 275760.
  const full = (date: string, tz: string): string => {
    const r = getNextLunations(noonOf(date), tz);
    const iso = (t: Date | null): string => (t ? t.toISOString().replace(/\.\d{3}Z$/, 'Z') : '-');
    return [r.nextNew ? formatDate(r.nextNew) : 'Not found', iso(r.newAt), r.nextFull ? formatDate(r.nextFull) : 'Not found', iso(r.fullAt)].join(' | ');
  };
  const cases: Array<[string, string, string]> = [
    // 22:22 UTC is 23:22 in Oslo, still the 20th: today's (D-4).
    ['9999-11-20', 'Europe/Oslo', '20/November/9999 | 9999-11-20T22:22:22Z | 05/December/9999 | 9999-12-05T00:46:18Z'],
    ['9999-12-10', 'Europe/Oslo', '20/December/9999 | 9999-12-20T06:32:14Z | 03/January/10000 | +010000-01-03T18:10:00Z'],
    ['9999-12-31', 'Europe/Oslo', '18/January/10000 | +010000-01-18T12:55:00Z | 03/January/10000 | +010000-01-03T18:10:00Z'],
    ['10000-01-20', 'Europe/Oslo', '16/February/10000 | +010000-02-16T18:43:53Z | 02/February/10000 | +010000-02-02T10:19:49Z'],
    ['275760-08-01', 'UTC', '16/August/275760 | +275760-08-16T14:40:27Z | 02/August/275760 | +275760-08-02T07:43:38Z'],
  ];
  for (const [date, tz, want] of cases) assert.equal(full(date, tz), want, `${date} ${tz}`);
  // And each is on or after the chosen day in the zone, whatever the year's width.
  for (const date of ['9999-12-25', '9999-12-31', '10000-01-01', '10000-01-17', '99999-12-31', '100000-01-01']) {
    const r = getNextLunations(noonOf(date), 'Europe/Oslo');
    const [y, m, d] = date.split('-').map(Number) as [number, number, number];
    for (const at of [r.newAt, r.fullAt]) {
      assert.ok(at, `${date}: found`);
      assert.ok(key(localYmd(at, 'Europe/Oslo')) >= key([y, m, d]), `${date}: ${at.toISOString()} is not before the day`);
    }
  }
});

/** The moon's centre at 0.133 degrees, where the page calls it risen (MOON_RISE_ALTITUDE). */
const RISE = 0.133 * Math.PI / 180;

test('lunarTransitFrom: the culmination, refined, and none on a day without one (F-2)', () => {
  // Against a 20-second scan for local maxima of the altitude (above the 0.133 degree rise
  // threshold) inside each UTC day: the page finds a transit on exactly the days the scan does, within 90 seconds (the
  // vertex through 15-minute samples is least exact for a moon passing near the zenith). Until
  // F-2 the page printed the best 15-minute sample (median error 3.8 minutes) and named
  // midnight on the days with no culmination.
  const r = { days: 0, none: 0, worst: 0, bad: [] as string[] };
  const alt = (t: number, lat: number, lon: number): number => SunCalc.getMoonPosition(new Date(t), lat, lon).altitude;
  const places: Array<[string, number, number]> = [['Oslo', 59.9139, 10.7522], ['Sydney', -33.8688, 151.2093], ['Equator', 0, 0]];
  for (const [name, lat, lon] of places) {
    for (let k = 0; k < 365; k += 2) {
      const start = Date.UTC(2026, 0, 1 + k);
      const got = lunarTransitFrom(new Date(start), lat, lon);
      let truth: number | null = null, prev = alt(start - 20000, lat, lon), cur = alt(start, lat, lon);
      for (let t = start; t < start + 86400000; t += 20000) {
        const next = alt(t + 20000, lat, lon);
        if (cur >= prev && cur > next && cur > RISE) truth = t;
        prev = cur; cur = next;
      }
      r.days++;
      if (got === null) r.none++;
      const day = `${name} ${new Date(start).toISOString().slice(0, 10)}`;
      if (got === null || truth === null) {
        if ((got === null) !== (truth === null)) r.bad.push(`${day}: page ${got?.toISOString()}, scan ${truth === null ? null : new Date(truth).toISOString()}`);
        continue;
      }
      r.worst = Math.max(r.worst, Math.abs(got.getTime() - truth) / 1000);
    }
  }
  assert.deepEqual(r.bad, []);
  assert.equal(r.days, 549);
  assert.ok(r.worst <= 90, `worst error ${r.worst} s`);
  // About one day a lunar month at each place (3 places, every other day of a year).
  assert.ok(r.none >= 12 && r.none <= 24, `${r.none} days without a transit`);
});

test('lunarTransitFrom: each culmination falls in exactly one of two adjacent days', () => {
  // The samples run one step past each end of the window, so the windows on both sides of a
  // midnight see the same three samples near it: no transit is counted twice or lost. Every day of
  // 2026 at Bergen, in UTC days as the Android alarm engine scans them.
  const ts: number[] = [];
  for (let k = 0; k < 365; k++) {
    const t = lunarTransitFrom(new Date(Date.UTC(2026, 0, 1 + k)), 60.3913, 5.3221);
    if (t) ts.push(t.getTime());
  }
  const gaps: number[] = [];
  for (let i = 1; i < ts.length; i++) gaps.push((ts[i] as number - (ts[i - 1] as number)) / 3600000);
  assert.ok(gaps.length > 340);
  // Consecutive culminations are a lunar day apart: never under 24 hours, never two days.
  assert.ok(Math.min(...gaps) > 24 && Math.max(...gaps) < 26, `${Math.min(...gaps)} to ${Math.max(...gaps)} hours`);
});

test('lunarTransitFrom: a culmination at or below the rise threshold is no transit (F-3)', () => {
  // The moon's centre peaks between the horizon and 0.133 degrees: the card says Always Down, so
  // there is no transit to show or to ring an alarm for. Until 23 October 2026 these counted.
  for (const [lat, date] of [[69, '2026-02-09'], [-63, '2026-12-23'], [77, '2026-05-10']] as const) {
    assert.equal(lunarTransitFrom(new Date(`${date}T00:00:00Z`), lat, 0), null, `${lat} ${date}`);
  }
});

test('localDayWindow: the local day from its first instant to the next day\'s (F-5)', () => {
  const win = (date: string, tz: string): string => {
    const [y, m, d] = date.split('-').map(Number) as [number, number, number];
    const w = localDayWindow(y, m, d, tz);
    return `${w.start.toISOString().replace('.000Z', 'Z')} ${(w.end.getTime() - w.start.getTime()) / 3600000}`;
  };
  // An ordinary day, and the four offset changes of Oslo, Sydney and New York in 2026.
  assert.equal(win('2026-06-21', 'Europe/Oslo'), '2026-06-20T22:00:00Z 24');
  assert.equal(win('2026-03-29', 'Europe/Oslo'), '2026-03-28T23:00:00Z 23');
  assert.equal(win('2026-10-25', 'Europe/Oslo'), '2026-10-24T22:00:00Z 25');
  assert.equal(win('2026-04-05', 'Australia/Sydney'), '2026-04-04T13:00:00Z 25');
  assert.equal(win('2026-10-04', 'Australia/Sydney'), '2026-10-03T14:00:00Z 23');
  assert.equal(win('2026-03-08', 'America/New_York'), '2026-03-08T05:00:00Z 23');
  assert.equal(win('2026-11-01', 'America/New_York'), '2026-11-01T04:00:00Z 25');
  // Santiago moves its clocks forward at midnight, so 6 September 2026 starts at 01:00 (as
  // java.time's atStartOfDay says); half-hour steps at Lord Howe; India has none.
  assert.equal(win('2026-09-06', 'America/Santiago'), '2026-09-06T04:00:00Z 23');
  assert.equal(win('2026-10-04', 'Australia/Lord_Howe'), '2026-10-03T13:30:00Z 23.5');
  assert.equal(win('2026-06-01', 'Asia/Kolkata'), '2026-05-31T18:30:00Z 24');
  // An unknown zone gives the UTC day.
  assert.equal(win('2026-06-01', 'Not/AZone'), '2026-06-01T00:00:00Z 24');
  // Apia skipped 30 December 2011 (from UTC-10 to UTC+14): that day is empty, as java.time's
  // atStartOfDay makes it, and the days either side are 24 hours each.
  assert.equal(win('2011-12-29', 'Pacific/Apia'), '2011-12-29T10:00:00Z 24');
  assert.equal(win('2011-12-30', 'Pacific/Apia'), '2011-12-30T10:00:00Z 0');
  assert.equal(win('2011-12-31', 'Pacific/Apia'), '2011-12-30T10:00:00Z 24');
});

test('the day windows tile the calendar, so each culmination falls in exactly one local day (F-5)', () => {
  // Every day of 2026 and 2027 at three places whose zones change offset: each day's window
  // ends where the next one starts, and consecutive transits are a lunar day apart (no
  // culmination counted twice, none lost). Until 23 October 2026 the window started an hour off
  // on those days: Bergen on 25 October 2026 culminates at 00:20:13, which no day showed.
  const places: Array<[number, number, string]> = [[60.3913, 5.3221, 'Europe/Oslo'], [-33.8688, 151.2093, 'Australia/Sydney'], [40.7128, -74.006, 'America/New_York']];
  for (const [lat, lon, tz] of places) {
    const ts: number[] = [];
    let prevEnd: number | null = null;
    for (let k = 0; k < 730; k++) {
      const d = new Date(Date.UTC(2026, 0, 1 + k));
      const w = localDayWindow(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate(), tz);
      if (prevEnd !== null) assert.equal(w.start.getTime(), prevEnd, `${tz} ${d.toISOString().slice(0, 10)}`);
      prevEnd = w.end.getTime();
      const t = lunarTransitFrom(w.start, lat, lon, (w.end.getTime() - w.start.getTime()) / 3600000);
      if (t) {
        assert.ok(t.getTime() >= w.start.getTime() && t.getTime() < w.end.getTime(), `${tz} ${d.toISOString().slice(0, 10)}: inside the day`);
        ts.push(t.getTime());
      }
    }
    const gaps = ts.slice(1).map((t, i) => (t - (ts[i] as number)) / 3600000);
    assert.ok(Math.min(...gaps) > 24 && Math.max(...gaps) < 26, `${tz}: ${Math.min(...gaps)} to ${Math.max(...gaps)} hours`);
  }
});

const cardPage = loadPage();
/** Rise, transit and set as the results card shows them for a place in zone tz on date (its own
    page, so the init test below still sees the page as loaded). */
const card = async (lat: number, lon: number, date: string, tz = 'UTC'): Promise<string> => {
  const state = cardPage.get('state'), els = cardPage.get('els');
  state.tab = 'city';
  state.city = { name: 'Test', fold: '', country: 'Test', cfold: '', lat, lon, tz, alt: '' };
  els.dateInput.value = date;
  await cardPage.fn('onCalculate')();
  return [els.resMoonrise.textContent, els.resTransit.textContent, els.resMoonset.textContent].join(' | ');
};
const polar = (lat: number, lon: number, date: string): Promise<string> => card(lat, lon, date);

test('the card on the days a zone changes its offset (F-5)', async () => {
  // Bergen, 25 October 2026 (25 hours): the culmination at 00:20:13 is that day's, and the 24th
  // has none. Before F-5 both days showed the dash.
  assert.equal(await card(60.3913, 5.3221, '2026-10-24', 'Europe/Oslo'), '17:02:31 | \u2014 | 06:17:31');
  assert.equal(await card(60.3913, 5.3221, '2026-10-25', 'Europe/Oslo'), '16:03:04 | 00:20:13 | 06:54:00');
  // Los Angeles, 6 November 2033: 00:15 PDT, which also fell in neither window.
  assert.match(await card(34.0522, -118.2437, '2033-11-06', 'America/Los_Angeles'), /^\S+ \| 00:15:1\d \| \S+$/);
});

test('a date the zone skipped shows an error, not a card (fix round 2)', async () => {
  // Apia, 30 December 2011: the day has no instants, so there is nothing to search. Before this
  // the card said Always Up for both rows beside a dash for the transit.
  const els = cardPage.get('els');
  await card(-13.8333, -171.7667, '2011-12-29', 'Pacific/Apia');
  assert.equal(els.errorMsg.hidden, true);
  assert.equal(els.resultsCard.hidden, false);
  await card(-13.8333, -171.7667, '2011-12-30', 'Pacific/Apia');
  assert.equal(els.errorMsg.textContent, 'This date did not happen here: the clocks in Pacific/Apia skipped it. Please select another date.');
  assert.equal(els.errorMsg.hidden, false);
  assert.equal(els.resultsCard.hidden, true);
  // The next day is an ordinary day again: the error goes and the card comes back.
  assert.match(await card(-13.8333, -171.7667, '2011-12-31', 'Pacific/Apia'), /^\d\d:\d\d:\d\d \| \d\d:\d\d:\d\d \| /);
  assert.equal(els.errorMsg.hidden, true);
  assert.equal(els.resultsCard.hidden, false);
});

test('polar labels: "up" means above the rise threshold, and a moon that stays down has no transit (F-3)', async () => {
  const UP = String.fromCodePoint(0x1F315) + ' Always Up';
  const DOWN = String.fromCodePoint(0x1F311) + ' Always Down';
  const DASH = String.fromCharCode(0x2014);
  // The moon's centre peaks 0.09 degrees up at 69 N on 9 February 2026: under the 0.133 degree
  // rise threshold, so it never rose. 90d11dd said Always Up, and showed a transit.
  assert.equal(await polar(69, 0, '2026-02-09'), `${DOWN} | ${DASH} | ${DOWN}`);
  // The same at 63 S on 23 December 2026 (0.04 degrees).
  assert.equal(await polar(-63, 0, '2026-12-23'), `${DOWN} | ${DASH} | ${DOWN}`);
  // Plainly up all day, plainly down all day: unchanged.
  assert.match(await polar(78.2233, 15.6469, '2026-01-03'), new RegExp(`^${UP} \\| \\d\\d:\\d\\d:\\d\\d \\| ${UP}$`, 'u'));
  assert.equal(await polar(78.2233, 15.6469, '2026-01-17'), `${DOWN} | ${DASH} | ${DOWN}`);
});

test('init: the date field defaults to today, YYYY-MM-DD, and the city tab is active', () => {
  assert.match(page.run<string>('els.dateInput.value'), /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(page.run('els.dateInput.value === getTodayString()'), true);
  assert.equal(page.run('state.tab'), 'city');
});
