// Captures tests/search-vectors.json: types each query of tests/search-queries.ts into the city
// search of a page loaded in Node (tests/load.ts), exactly as the input event would (set the
// field, call onCityInput), and records the rows the dropdown then lists, in order, as
// [name, country, tz]. Run once on 22 October 2026 against the MoonApp 90d11dd tree (the last
// hand-written app.js), so tests/search.test.ts can prove the TypeScript page searches as it did:
//
//   node tests/capture-search.ts --root /path/to/90d11dd/checkout --label "MoonApp 90d11dd" [--out FILE]
//
// --root is required on purpose: capturing from the current tree would only prove the page agrees
// with itself. Recapture only for a deliberate change of behaviour, and review the JSON diff.

import { writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { loadPage } from './load.ts';
import type { FakeElement, LoadedPage, PageCity } from './load.ts';
import { SEARCH_QUERIES } from './search-queries.ts';

/** One captured query: what was typed, whether the list opened, and the rows it listed. */
export interface SearchVector { q: string; open: boolean; rows: Array<[string, string, string]> }

/** Types q into the page's city search and reads back the dropdown. */
export function search(page: LoadedPage, q: string): SearchVector {
  const els = page.get('els');
  els.citySearch.value = q;
  page.fn('onCityInput')();
  const open = !els.cityDropdown.hidden;
  const rows = open ? els.cityDropdown.children.map((li): [string, string, string] => {
    const city = (li as FakeElement & { _city: PageCity })._city;
    return [city.name, city.country, city.tz];
  }) : [];
  return { q, open, rows };
}

if (import.meta.main) {
  const arg = (name: string): string | null => {
    const i = process.argv.indexOf(name);
    return i > 0 ? (process.argv[i + 1] ?? null) : null;
  };
  const root = arg('--root');
  const label = arg('--label');
  if (!root || !label) {
    console.error('usage: node tests/capture-search.ts --root DIR --label TEXT [--out FILE]');
    process.exit(2);
  }
  const out = arg('--out') ?? new URL('./search-vectors.json', import.meta.url).pathname;
  const page = loadPage({ root: pathToFileURL(resolve(root) + '/') });
  const vectors = SEARCH_QUERIES.map((q) => search(page, q));
  const head = {
    about: 'City search results captured by tests/capture-search.ts and replayed by tests/search.test.ts. Generated: do not edit by hand.',
    capturedFrom: label,
    capturedAt: new Date().toISOString().slice(0, 10),
  };
  const body = vectors.map((v) => `  ${JSON.stringify(v)}`).join(',\n');
  writeFileSync(out, `${JSON.stringify(head, null, 1).replace(/\n}$/, ',')}\n "vectors": [\n${body}\n ]\n}\n`);
  console.log(`wrote ${out}: ${vectors.length} queries from ${label}`);
}
