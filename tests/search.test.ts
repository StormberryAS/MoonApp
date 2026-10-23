// The city search, replayed in Node against tests/search-vectors.json: for each of the 162
// queries, the page lists the same cities in the same order (name, country and zone), or keeps the
// list closed, exactly as the 90d11dd page did. Added 22 October 2026 with the TypeScript move:
// until then the web search had no test at all, and the Android side checked only the top hit of
// four queries.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { loadPage } from './load.ts';
import { search } from './capture-search.ts';
import type { SearchVector } from './capture-search.ts';
import { SEARCH_QUERIES } from './search-queries.ts';

const frozen = JSON.parse(readFileSync(new URL('./search-vectors.json', import.meta.url), 'utf8')) as { capturedFrom: string; vectors: SearchVector[] };

test('the vectors were captured from the 90d11dd page, one per query, in order', () => {
  assert.match(frozen.capturedFrom, /^MoonApp 90d11dd /);
  assert.deepEqual(frozen.vectors.map((v) => v.q), [...SEARCH_QUERIES]);
  assert.equal(frozen.vectors.length, 162);
});

test('every query lists the same cities in the same order as 90d11dd', () => {
  const page = loadPage();
  const diffs: string[] = [];
  for (const want of frozen.vectors) {
    const got = search(page, want.q);
    if (JSON.stringify(got) !== JSON.stringify(want)) diffs.push(`${JSON.stringify(want.q)}\n  90d11dd: ${JSON.stringify(want.rows)}\n  now:     ${JSON.stringify(got.rows)}`);
  }
  assert.deepEqual(diffs, []);
});

test('the list shows at most 8 rows, and prefix matches come before substring matches', () => {
  const page = loadPage();
  for (const q of SEARCH_QUERIES) assert.ok(search(page, q).rows.length <= 8, q);
  // "erdal": Erdal first, not Cloverdale, South Riverdale or Terdal (the reason for the ordering).
  const erdal = search(page, 'erdal').rows;
  assert.equal(erdal[0]?.[0], 'Erdal');
});

test('picking a row selects that city and closes the list', () => {
  const page = loadPage();
  const els = page.get('els');
  search(page, 'tromso');
  // The click lands on the first row: closest('li') from the event's target finds it.
  page.fn('onDropdownClick')({ target: { closest: () => els.cityDropdown.children[0] } });
  assert.equal(page.get('state').city?.name, 'Tromsø');
  assert.equal(els.citySelectedTxt.textContent, 'Tromsø, Norway');
  assert.equal(els.cityDropdown.hidden, true);
  // Typing again drops the selection.
  search(page, 'b');
  assert.equal(page.get('state').city, null);
});
