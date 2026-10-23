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
/** getNextLunations from the noon UTC anchor of date, both results formatted as the page shows them. */
const lunations = (date: string): string => {
  const r = getNextLunations(new Date(`${date}T12:00:00Z`));
  return [r.nextNew ? formatDate(r.nextNew) : 'Not found', r.nextFull ? formatDate(r.nextFull) : 'Not found'].join(' | ');
};

test('getNextLunations: the next new and full moon, as 90d11dd gives them', () => {
  // These are the values the page showed before fix F-1 (daily noon UTC samples, so an event in
  // the UTC afternoon is reported a day late: the full moon of 1 February 2026 (17:33 UTC by suncalc) shows
  // as the 2nd). Frozen here so the TypeScript move is proven neutral; fix F-1 replaces them.
  const cases: Array<[string, string]> = [
    ['2026-01-01', '19/January/2026 | 03/January/2026'],
    ['2026-02-01', '18/February/2026 | 02/February/2026'],
    ['2026-02-17', '18/February/2026 | 03/March/2026'],
    ['2026-03-03', '19/March/2026 | 02/April/2026'],
    ['2026-06-21', '14/July/2026 | 30/June/2026'],
    ['2026-10-22', '09/November/2026 | 26/October/2026'],
    ['2026-12-31', '08/January/2027 | 22/January/2027'],
    ['1900-01-01', '02/January/1900 | 16/January/1900'],
    ['2100-12-31', '29/January/2101 | 15/January/2101'],
  ];
  for (const [date, want] of cases) assert.equal(lunations(date), want, date);
});

test('init: the date field defaults to today, YYYY-MM-DD, and the city tab is active', () => {
  assert.match(page.run<string>('els.dateInput.value'), /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(page.run('els.dateInput.value === getTodayString()'), true);
  assert.equal(page.run('state.tab'), 'city');
});
