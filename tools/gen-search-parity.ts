#!/usr/bin/env node
/**
 * gen-search-parity.ts: the city search corpus the Android port is held to, written from the page.
 * ================================================================
 * Types each query of tests/search-queries.ts into the page's own city search (app.js, loaded with
 * suncalc.js and cities.js by tests/load.ts, exactly as the browser runs them) and writes what the
 * dropdown lists to android/app/src/test/resources/citysearch-golden.csv, one query per line:
 *
 *   query <TAB> open (1 or 0) <TAB> name|country|zone <TAB> name|country|zone ...
 *
 * The query is written with backslash escapes for a backslash, a tab and a line break, so leading
 * and trailing spaces survive. CitySearchParityTest asserts the full ordered list for every query.
 * tests/search.test.ts already holds the page to the 90d11dd capture (tests/search-vectors.json),
 * so this corpus is that same behaviour, in a form a JVM test reads without a JSON library.
 *
 * Usage, from anywhere (Node 24 runs the TypeScript directly):
 *   node tools/gen-search-parity.ts           write the CSV
 *   node tools/gen-search-parity.ts --check   write nothing; exit 1 unless the committed CSV is
 *                                             byte for byte what this script writes
 * tests/golden.test.ts and tools/build-ts.sh --check run the --check mode.
 *
 * Added 23 October 2026 with MoonApp 1.0.4 (PLAN section 4.4).
 * ================================================================
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { loadPage } from '../tests/load.ts';
import { search } from '../tests/capture-search.ts';
import { SEARCH_QUERIES } from '../tests/search-queries.ts';

const REPO_ROOT = resolve(import.meta.dirname, '..');
const OUT = join(REPO_ROOT, 'android', 'app', 'src', 'test', 'resources', 'citysearch-golden.csv');
const page = loadPage({ root: pathToFileURL(REPO_ROOT + '/') });

const escape = (s: string): string => s.replace(/\\/g, '\\\\').replace(/\t/g, '\\t').replace(/\n/g, '\\n');
const lines = [
  '# The city search as the web page answers it: for each query of tests/search-queries.ts, whether',
  '# the dropdown opens and the cities it lists, in order. Columns are separated by tabs; the query',
  '# keeps its spaces and escapes a backslash, a tab and a line break; each city is name|country|zone.',
  '# Written from the compiled app.js by tools/gen-search-parity.ts; do not edit by hand.',
  '# query\topen\tcity...',
];
for (const q of SEARCH_QUERIES) {
  const v = search(page, q);
  for (const r of v.rows) for (const f of r) if (/[|\t\n]/.test(f)) throw new Error(`a catalogue field holds a separator: ${JSON.stringify(f)}`);
  lines.push([escape(q), v.open ? '1' : '0', ...v.rows.map((r) => r.join('|'))].join('\t'));
}
const csv = lines.join('\n') + '\n';

if (process.argv.includes('--check')) {
  let committed = '';
  try { committed = readFileSync(OUT, 'utf8'); } catch { committed = ''; }
  if (committed !== csv) {
    process.stderr.write(`${OUT} is NOT what tools/gen-search-parity.ts writes: run it and review the diff\n`);
    process.exit(1);
  }
  process.stdout.write(`golden check OK: ${OUT} matches, ${SEARCH_QUERIES.length} data rows\n`);
} else {
  mkdirSync(dirname(OUT), { recursive: true });
  writeFileSync(OUT, csv, 'utf8');
  process.stdout.write(`wrote ${OUT}: ${SEARCH_QUERIES.length} rows\n`);
}
