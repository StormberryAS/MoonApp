// The page and the publishing set-up: the TypeScript rule (guard S-1), the TypeScript tooling, the
// _config.yml exclusions that keep sources, tooling and tests off the site, the classic script
// tags and their cache stamps, and the house style of the page text. Added 22 October 2026 with the
// TypeScript move, on SunApp's tests/static.test.ts.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { defined } from './helpers.ts';

const read = (p: string): string => readFileSync(new URL(`../${p}`, import.meta.url), 'utf8');
const page = read('index.html');
const visible = page.replace(/<!--[\s\S]*?-->/g, '').replace(/<script[\s\S]*?<\/script>/g, '').replace(/<svg[\s\S]*?<\/svg>/g, '').replace(/<[^>]+>/g, ' ');

/** SunCalc 1.8.0 exactly as vendored in eight Labs repositories (sha256 of suncalc.js). */
const SUNCALC_SHA256 = 'e17fa013a5ab354c22844f3cebecd4a2b866518e5b63d132d537ea6578cb217b';
const EM_DASH = String.fromCharCode(0x2014);
const EN_DASH = String.fromCharCode(0x2013);

test('page text: no en or em dashes, no double hyphens, no exclamation marks; lang unchanged', () => {
  // The one dash on the page is not text: it is the placeholder the illumination line shows
  // before the first calculation, the same glyph the results use for "no event" (decision D-11).
  const placeholder = `${EM_DASH} % illuminated`;
  assert.equal(page.split(placeholder).length, 2, 'the placeholder is there once');
  const rest = visible.replace(placeholder, '');
  assert.ok(!rest.includes(EM_DASH) && !rest.includes(EN_DASH), 'no en or em dash in the page text');
  assert.doesNotMatch(rest, /--/);
  assert.doesNotMatch(rest, /!/);
  assert.match(page, /<html lang="en">/);
});

test('the page loads the three classic scripts in the 90d11dd order, each stamped', () => {
  const scripts = [...page.matchAll(/<script\b[^>]*>/g)].map((m) => m[0]).filter((s) => !/type="application\/ld\+json"/.test(s));
  assert.deepEqual(scripts.map((s) => s.replace(/\?v=[0-9a-f]{8}/, '?v=STAMP')), [
    '<script src="suncalc.js?v=STAMP">',
    '<script src="cities.js?v=STAMP">',
    '<script src="app.js?v=STAMP">',
  ]);
  // suncalc.js in the head, cities.js and app.js at the end of the body.
  assert.ok(page.indexOf('src="suncalc.js') < page.indexOf('</head>'));
  assert.ok(page.indexOf('src="cities.js') > page.indexOf('<body'));
  assert.ok(page.indexOf('src="app.js') > page.indexOf('src="cities.js'));
  assert.ok(page.indexOf('src="app.js') < page.indexOf('</body>'));
  assert.doesNotMatch(page, /type="module"/);
  assert.doesNotMatch(page, /src="ts\//);
});

test('every local script and stylesheet carries the ?v= stamp of its current bytes', () => {
  const refs = [...page.matchAll(/(?:src|href)="((?!https?:|\/\/|data:|#|mailto:)[^"?#]+\.(?:js|css))(\?v=[^"]*)?"/g)];
  assert.ok(refs.length >= 5);
  for (const m of refs) {
    const path = defined(m[1], 'the path');
    if (path.startsWith('switcher-icons/')) continue;
    const want = createHash('sha256').update(readFileSync(new URL(`../${path}`, import.meta.url))).digest('hex').slice(0, 8);
    assert.equal(m[2], `?v=${want}`, `${path} is stamped with its own hash`);
  }
});

test('_config.yml keeps sources, tooling and tests off the site, and publishes the app', () => {
  const cfg = read('_config.yml');
  const excluded = [...cfg.matchAll(/^ {2}- (\S+)$/gm)].map((m) => m[1]);
  // data/ since fix F-4: data/cities.tsv is the Android build input, which the page never loads.
  for (const x of ['ts/', 'tools/', 'tests/', 'node_modules/', 'package.json', 'package-lock.json', 'tsconfig.json', 'tsconfig.sw.json', '.npmrc', 'bump_assets.py', 'README.md', 'CHANGELOG.md', 'DISCLAIMER.md', 'LICENSE', 'NOTICE', 'android/', 'fastlane/', 'zapstore.yaml', 'data/']) {
    assert.ok(excluded.includes(x), `${x} is excluded`);
  }
  for (const x of ['app.js', 'sw.js', 'cities.js', 'suncalc.js', 'index.html', 'style.css', 'disclaimer.html', 'fonts/', 'switcher-icons/']) assert.ok(!excluded.includes(x), `${x} is published`);
  // Gone with the move: nothing may still point at them.
  for (const x of ['jsconfig.json', 'types/']) assert.ok(!excluded.includes(x), `${x} no longer exists`);
});

test('the TypeScript set-up follows SunApp: TypeScript 6.0.3 pinned, strict, a classic script', () => {
  const pkg = JSON.parse(read('package.json'));
  assert.deepEqual(pkg.devDependencies, { '@types/node': '24.19.2', typescript: '6.0.3' });
  assert.equal(pkg.dependencies, undefined, 'no runtime dependency');
  assert.deepEqual(pkg.engines, { node: '>=24' });
  assert.equal(pkg.type, 'module');
  const lock = JSON.parse(read('package-lock.json'));
  assert.equal(lock.packages['node_modules/typescript'].version, '6.0.3');
  assert.equal(lock.packages['node_modules/@types/node'].version, '24.19.2');
  assert.deepEqual(Object.keys(lock.packages).filter((k) => k !== '').sort(), ['node_modules/@types/node', 'node_modules/typescript', 'node_modules/undici-types']);
  for (const [k, v] of Object.entries(lock.packages as Record<string, { integrity?: string; hasInstallScript?: boolean }>)) {
    if (k === '') continue;
    assert.match(v.integrity ?? '', /^sha512-/, `${k} is pinned by its integrity hash`);
    assert.notEqual(v.hasInstallScript, true, `${k} has no install script`);
  }
  assert.match(read('.npmrc'), /^ignore-scripts=true$/m);
  assert.match(read('.npmrc'), /^save-exact=true$/m);
  const ts = JSON.parse(read('tsconfig.json'));
  for (const k of ['strict', 'noUnusedLocals', 'noUnusedParameters', 'noImplicitReturns', 'noFallthroughCasesInSwitch', 'noUncheckedIndexedAccess', 'verbatimModuleSyntax', 'removeComments', 'noEmitOnError']) {
    assert.equal(ts.compilerOptions[k], true, `tsconfig.json ${k}`);
  }
  assert.equal(ts.compilerOptions.rootDir, 'ts');
  assert.equal(ts.compilerOptions.outDir, '.');
  assert.equal(ts.compilerOptions.module, 'ES2020');
  assert.equal(ts.compilerOptions.target, 'ES2020');
  assert.deepEqual(ts.include, ['ts/**/*.ts']);
  assert.match(read('.gitignore'), /^node_modules\/$/m);
  // app.ts is a classic script: no import or export, so tsc emits a script and index.html keeps
  // loading app.js without type="module" (decision D-1).
  const app = read('ts/app.ts');
  assert.doesNotMatch(app, /^\s*(import|export)\b/m);
  const js = read('app.js');
  assert.match(js, /^"use strict";\n\/\*! MoonApp: app\.js is compiled from ts\/app\.ts/);
  assert.doesNotMatch(js, /^\s*(import|export)\b/m);
  // The tests and the Node tools are TypeScript, run by Node 24 directly and type-checked strictly.
  const tests = JSON.parse(read('tests/tsconfig.json'));
  for (const k of ['strict', 'noUncheckedIndexedAccess', 'erasableSyntaxOnly', 'verbatimModuleSyntax', 'noEmit']) assert.equal(tests.compilerOptions[k], true, `tests/tsconfig.json ${k}`);
  assert.deepEqual(tests.compilerOptions.types, ['node'], 'Node is typed by @types/node');
  assert.deepEqual(tests.compilerOptions.lib, ['ES2023'], 'no DOM library: the tests run in Node, not a browser');
  assert.deepEqual(tests.include, ['*.ts', '../tools/*.ts']);
  assert.match(read('tools/build-ts.sh'), /tests\/tsconfig\.json/);
  assert.match(read('tools/build-ts.sh'), /for gen in "\$ROOT"\/tools\/gen-\*\.ts; do\n\s+node "\$gen" --check/, 'build-ts.sh --check runs the corpus generators');
  assert.deepEqual(readdirSync(new URL('./', import.meta.url)).filter((n) => /\.(m?js|cjs)$/.test(n)), [], 'no JavaScript left in tests/');
  assert.deepEqual(readdirSync(new URL('../tools/', import.meta.url)).filter((n) => /\.(m?js|cjs)$/.test(n)), [], 'no JavaScript left in tools/');
});

test('the vendored SunCalc and the generated catalogue are untouched', () => {
  const suncalc = readFileSync(new URL('../suncalc.js', import.meta.url));
  assert.equal(createHash('sha256').update(suncalc).digest('hex'), SUNCALC_SHA256);
  assert.match(read('cities.js'), /^\/\/ Stormberry Labs shared city catalogue\.\n\/\/ GENERATED FILE, DO NOT EDIT BY HAND\.\n/);
});

test('the generators work from the repository, not from a path under $HOME', () => {
  for (const name of ['gen-golden', 'gen-moontimes-golden', 'gen-webparity-golden', 'gen-lunar-parity', 'gen-search-parity']) {
    const src = read(`tools/${name}.ts`);
    assert.doesNotMatch(src, /process\.env\.HOME/, `${name}.ts reads no home path`);
    assert.match(src, /resolve\(import\.meta\.dirname, '\.\.'\)/, `${name}.ts works relative to the repository`);
    assert.doesNotMatch(src, /\beval\(/, `${name}.ts does not eval suncalc.js`);
    assert.match(src, /'--check'/, `${name}.ts has a --check mode`);
  }
});

// S-1 (22 October 2026, as SunApp's of 21 October): the only JavaScript in the repository is
// compiled output, plus the generated city catalogue and the vendored SunCalc. Every .js, .mjs and
// .cjs that git tracks, or would pick up (untracked and not ignored), is one of those. Each
// compiled file and cities.js is marked linguist-generated and suncalc.js linguist-vendored, so
// GitHub's language bar counts none of them. A hand-written script anywhere else fails here.
test('the only JavaScript is compiled, generated or vendored, and marked so (S-1)', (t) => {
  const root = new URL('../', import.meta.url);
  const git = (...args: string[]): string => execFileSync('git', ['-C', root.pathname, ...args], { encoding: 'utf8' });
  let inGit = false;
  try { inGit = git('rev-parse', '--is-inside-work-tree').trim() === 'true'; } catch { inGit = false; }
  if (!inGit) { t.skip('not a git checkout'); return; }
  const walk = (dir: string): string[] => readdirSync(new URL(dir, root)).flatMap((n) => (n.endsWith('.ts') ? [`${dir}${n}`] : /\./.test(n) ? [] : walk(`${dir}${n}/`)));
  const compiled = walk('ts/').filter((f) => !f.endsWith('.d.ts')).map((f) => f.replace(/^ts\//, '').replace(/\.ts$/, '.js')).sort();
  assert.ok(compiled.includes('app.js'), 'ts/ compiles to app.js');
  const allowed = [...compiled, 'cities.js', 'suncalc.js'].sort();
  const js = git('ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', '*.js', '*.mjs', '*.cjs').split('\0').filter(Boolean);
  assert.deepEqual([...new Set(js)].sort(), allowed, 'JavaScript in the repository other than the compiled, generated and vendored files');
  const attr = (name: string, files: string[]): Map<string, string> => {
    const out = git('check-attr', '-z', name, '--', ...files).split('\0');
    const m = new Map<string, string>();
    for (let i = 0; i + 2 < out.length; i += 3) m.set(defined(out[i], 'path'), defined(out[i + 2], 'value'));
    return m;
  };
  const generated = attr('linguist-generated', [...compiled, 'cities.js']);
  for (const f of [...compiled, 'cities.js']) assert.equal(generated.get(f), 'true', `${f} is linguist-generated`);
  assert.equal(attr('linguist-vendored', ['suncalc.js']).get('suncalc.js'), 'true', 'suncalc.js is linguist-vendored');
  const corpora = ['mooncalc-golden.csv', 'moontimes-golden.csv', 'webparity-golden.csv', 'lunation-parity.csv', 'transit-parity.csv', 'lunar-daykind-parity.csv', 'citysearch-golden.csv'].map((f) => `android/app/src/test/resources/${f}`);
  const genCsv = attr('linguist-generated', [...corpora, 'tests/page-vectors.json', 'tests/search-vectors.json']);
  for (const f of [...corpora, 'tests/page-vectors.json', 'tests/search-vectors.json']) assert.equal(genCsv.get(f), 'true', `${f} is linguist-generated`);
});

test('no page code is written inside a string: tests and tools call the page through fn()', () => {
  // A string handed to the page's vm (tests/load.ts run()) is JavaScript that the strict check
  // never reads. run() is kept for reading a value or a one-call expression; anything with a
  // statement, a block or a function in it belongs in TypeScript, calling page.fn('name').
  const files = [...readdirSync(new URL('./', import.meta.url)).map((n) => `tests/${n}`), ...readdirSync(new URL('../tools/', import.meta.url)).map((n) => `tools/${n}`)]
    .filter((f) => f.endsWith('.ts') && f !== 'tests/load.ts');
  const bad: string[] = [];
  for (const f of files) {
    for (const m of read(f).matchAll(/\.run(?:<[^()]*?>)?\((`[^`]*`|'[^']*'|"[^"]*")/g)) {
      const code = defined(m[1], 'the string').replace(/\$\{[^}]*\}/g, '0');
      if (/=>|;|[{}]|\bfunction\b/.test(code)) bad.push(`${f}: ${code.slice(0, 60)}`);
    }
  }
  assert.deepEqual(bad, []);
});

// The offline pilot (ported 23 October 2026 from SunApp 88f59d4): ts/sw.ts, compiled to sw.js, registered by offlinePilot() in
// ts/app.ts on https://moon.stormberry.as only. tests/sw-smoke.ts runs it in Chromium.
const listed = (src: string): string[] => {
  const m = /const ASSETS(?:: readonly string\[\])? = \[([\s\S]*?)\];/.exec(src);
  return [...defined(m?.[1], 'the ASSETS list').matchAll(/'([^']*)'/g)].map((x) => defined(x[1], 'an entry'));
};
const stampOfFile = (path: string): string => createHash('sha256').update(readFileSync(new URL(`../${path}`, import.meta.url))).digest('hex').slice(0, 8);

test('offline pilot: sw.js stores exactly what the pages load, under the stamps the pages use', () => {
  const assets = listed(read('sw.js'));
  // ts/sw.ts holds the same list without stamps; bump_assets.py writes them into sw.js.
  assert.deepEqual(listed(read('ts/sw.ts')), assets.map((a) => a.replace(/\?v=[0-9a-f]{8}$/, '')));
  assert.equal(new Set(assets).size, assets.length, 'no entry twice');
  // Every script and stylesheet the two pages load, with the stamp they load it under.
  const pageRefs = new Set<string>();
  for (const html of ['index.html', 'disclaimer.html']) {
    for (const m of read(html).matchAll(/(?:src|href)="((?!https?:|\/\/|data:|#|mailto:)[^"?#]+\.(?:js|css)\?v=[0-9a-f]{8})"/g)) pageRefs.add(`./${m[1]}`);
  }
  assert.deepEqual(assets.filter((a) => /\?v=/.test(a)).sort(), [...pageRefs].sort());
  for (const a of assets) {
    const m = /^\.\/([^?]*)(?:\?v=([0-9a-f]{8}))?$/.exec(a);
    assert.ok(m, `${a} is a relative path`);
    const path = defined(m[1], 'path') || 'index.html';
    assert.ok(existsSync(new URL(`../${path}`, import.meta.url)), `${a} exists`);
    if (m[2]) assert.equal(m[2], stampOfFile(path), `${a} carries the hash of ${path}`);
    if (/\.(js|css)$/.test(path)) assert.ok(m[2], `${a} is stamped`);
  }
  // The rest: the page itself, the disclaimer, the font the page preloads and its italic (the
  // disclaimer's last paragraph), the favicon and every carousel icon.
  const icons = readdirSync(new URL('../switcher-icons/', import.meta.url)).filter((n) => n.endsWith('.svg')).sort().map((n) => `./switcher-icons/${n}`);
  assert.deepEqual(assets.filter((a) => !/\?v=/.test(a)), ['./', './fonts/inter/InterVariable.woff2', './fonts/inter/InterVariable-Italic.woff2', './favicon.svg', './disclaimer.html', ...icons]);
  assert.match(page, /<link rel="preload" href="fonts\/inter\/InterVariable\.woff2" as="font"/);
  // The Home Screen icon (90d11dd) is linked by both pages but deliberately not stored: iOS fetches
  // it online when the page is added, and precaching is all or nothing, so a listed file that went
  // missing would fail every install.
  for (const html of ['index.html', 'disclaimer.html']) {
    assert.match(read(html), /<link rel="apple-touch-icon" href="apple-touch-icon\.png" \/>/, `${html} links the Home Screen icon`);
  }
  assert.ok(!assets.some((a) => a.includes('apple-touch-icon')), 'the Home Screen icon is not precached');
});

// The worker's precache and keepPage() treat every ?v= reference in a stored page as a same-origin
// script or stylesheet it must hold (fix round 2, verify 1 offline C-4): an absolute or other kind
// of ?v= reference would make every install fail without a word, so it fails here instead.
test('offline pilot: every ?v= reference in the stored pages is a relative .js or .css path', () => {
  for (const html of ['index.html', 'disclaimer.html']) {
    const refs = [...read(html).matchAll(/(?:src|href)="([^"]*\?v=[^"]*)"/g)].map((m) => defined(m[1], 'a reference'));
    assert.ok(refs.length > 0, `${html} has stamped references`);
    for (const r of refs) assert.match(r, /^(?![a-z][a-z0-9+.-]*:|\/)[^"?#]+\.(?:js|css)\?v=[0-9a-f]{8}$/, `${html}: ${r}`);
  }
});

test('offline pilot: registered only on https://moon.stormberry.as; no manifest, no theme colour', () => {
  const app = read('ts/app.ts');
  assert.match(app, /if \(location\.protocol !== 'https:' \|\| location\.hostname !== 'moon\.stormberry\.as'\) return;/);
  // A bare 'sw.js', never './sw.js': the worker's address has to stay the same for the browser to
  // update it in place, and the './x.js' form is the one bump_assets.py stamps in sw.js, so keeping
  // the register call to the bare form keeps that convention unambiguous should app.js ever be
  // stamped the same way. Today bump_assets.py stamps literals in sw.js only.
  assert.match(app, /sw\.register\('sw\.js', \{ scope: '\.\/', updateViaCache: 'none' \}\)/);
  assert.match(app, /^const OFFLINE_PILOT = '(off|opt-in|on)' as 'off' \| 'opt-in' \| 'on';$/m);
  assert.match(app, /^  try \{ offlinePilot\(\); \} catch \{/m);
  assert.match(read('ts/sw.ts'), /^const KILL = (true|false) as boolean;$/m);
  for (const html of ['index.html', 'disclaimer.html']) {
    const text = read(html);
    assert.doesNotMatch(text, /rel="manifest"/, `${html} has no manifest (it would let Chrome on Android offer an install that competes with Zapstore)`);
    assert.doesNotMatch(text, /name="theme-color"/, `${html} has no theme colour`);
    assert.doesNotMatch(text, /sw\.js/, `${html} does not load the worker as a script`);
  }
  // The worker is checked as a worker: no DOM, the WebWorker library, and kept out of the page program.
  const swc = JSON.parse(read('tsconfig.sw.json'));
  assert.equal(swc.extends, './tsconfig.json');
  assert.deepEqual(swc.compilerOptions.lib, ['ES2020', 'WebWorker']);
  assert.deepEqual(swc.include, ['ts/sw.ts']);
  assert.deepEqual(JSON.parse(read('tsconfig.json')).exclude, ['ts/sw.ts']);
  assert.match(read('tools/build-ts.sh'), /tsconfig\.sw\.json/);
});

// The website footer and the Android footer carry the same texts (Marcos, 2026-10-24: "keep web
// versions and APKs consistent"). The app's wording is the approved one, so this reads it from the
// Kotlin source (FooterContent.kt and Chrome.kt) and fails the moment either side is edited alone.
// Layout may differ; the texts must not, bar the two lines that have a true web version (below).
{
  const kt = (p: string): string => read(`android/app/src/main/kotlin/no/stormberry/moonapp/ui/components/${p}`);
  const unescape = (s: string): string => s.replace(/\\u([0-9A-Fa-f]{4})/g, (_m, h: string) => String.fromCharCode(parseInt(h, 16))).replace(/\\"/g, '"').replace(/\\\\/g, '\\');
  const source = kt('FooterContent.kt');
  const objectsAt = source.indexOf('object FooterText');
  const constsOf = (part: string): Map<string, string> => new Map([...part.matchAll(/const val (\w+) = "((?:[^"\\]|\\.)*)"$/gm)].map((m) => [defined(m[1], 'name'), unescape(defined(m[2], 'value'))] as const));
  const links = constsOf(source.slice(0, objectsAt));
  const texts = constsOf(source.slice(objectsAt));
  const text = (name: string): string => defined(texts.get(name), `FooterText.${name}`).replace(/\$(\w+)/g, (_m, n: string) => text(n));
  const link = (name: string): string => defined(links.get(name), `FooterLinks.${name}`);
  const chrome = kt('Chrome.kt');

  /** The text of the annotated string that contains `anchor` in Chrome.kt, and the target of each footerLink in it. */
  const annotated = (anchor: string): { text: string; urls: string[] } => {
    const from = chrome.lastIndexOf('buildAnnotatedString {', chrome.indexOf(anchor));
    assert.ok(from >= 0, `${anchor} is in a buildAnnotatedString`);
    const block = chrome.slice(from, chrome.indexOf('\n                },', from));
    let out = '';
    const urls: string[] = [];
    for (const m of block.matchAll(/append\("((?:[^"\\]|\\.)*)"\)|footerLink\(([^,]+), (?:FooterLinks\.(\w+)),/g)) {
      if (m[1] !== undefined) { out += unescape(m[1]); continue; }
      const label = defined(m[2], 'label').trim();
      out += label.startsWith('FooterText.') ? text(label.slice('FooterText.'.length)) : unescape(label.replace(/^"|"$/g, ''));
      urls.push(link(defined(m[3], 'link')));
    }
    return { text: out, urls };
  };

  const entities: Record<string, string> = { nbsp: ' ', copy: '©', middot: '·', oslash: 'ø', amp: '&', lt: '<', gt: '>', quot: '"' };
  const footerHtml = defined(page.match(/<footer class="site-footer">[\s\S]*?<\/footer>/), 'the site footer')[0];
  const domText = (html: string): string => html.replace(/<svg[\s\S]*?<\/svg>/g, '').replace(/<[^>]+>/g, '').replace(/&(\w+);/g, (_m, n: string) => defined(entities[n], `entity &${n};`)).replace(/[ \t\r\n]+/g, ' ').trim();
  const para = (cls: string | null): { text: string; urls: string[] } => {
    const html = cls ? defined(footerHtml.match(new RegExp(`<p class="${cls}">([\\s\\S]*?)</p>`)), cls)[1] : defined([...footerHtml.matchAll(/<p>([\s\S]*?)<\/p>/g)].find((m) => (m[1] ?? '').includes('&copy;')), 'the company line')[1];
    return { text: domText(defined(html, 'paragraph')), urls: [...defined(html, 'paragraph').matchAll(/<a [^>]*href="([^"]+)"/g)].map((m) => defined(m[1], 'href')) };
  };

  test('footer texts: the website carries the app\'s footer texts word for word', () => {
    assert.equal(para('footer-tagline').text, text('TAGLINE'));
    assert.equal(para('footer-privacy').text, text('PRIVACY_FIRST'));
    assert.equal(para('footer-ai').text, text('AI'));
    assert.equal(para('footer-data').text, text('CITY_LIST_DATED'));
    assert.equal(domText(defined(footerHtml.match(/<a [^>]*class="footer-logo"[^>]*>([\s\S]*?)<\/a>/), 'the wordmark')[1] ?? ''), 'Stormberry.');
  });

  // Two lines are true differently on the web (Marcos, 2026-10-25, option B): the page has no alarms, no
  // permissions and no device to disconnect, so it carries its own approved wording. The app's two lines
  // stay pinned as they are, and the web lines must be the approved ones and must not be the app's.
  test('footer: the two lines with a true web version are the approved web wording, and the app keeps its own', () => {
    assert.equal(text('OFFLINE'), 'Your places and alarms never leave this device. Disconnect and try it.');
    assert.equal(text('PERMISSIONS'), 'Alarm permissions only. No internet. No tracking.');
    assert.equal(para('footer-offline').text, 'Your places never leave this browser.');
    assert.equal(para('footer-permissions').text, 'No cookies. No tracking. Every calculation runs in your browser.');
    assert.notEqual(para('footer-offline').text, text('OFFLINE'));
    assert.notEqual(para('footer-permissions').text, text('PERMISSIONS'));
    for (const cls of ['footer-offline', 'footer-permissions']) assert.deepEqual(para(cls).urls, [], `${cls} carries no link`);
  });

  test('footer credits: the same sentence as the app, lead-in included, with the same links', () => {
    const app = annotated('Runs entirely on your device');
    const web = domText(defined(footerHtml.match(/<div class="footer-powered">\s*<p>([\s\S]*?)<\/p>/), 'the credits')[1] ?? '');
    assert.equal(web, app.text);
    assert.equal(app.text, 'Runs entirely on your device: our own code, SunCalc\'s formulae (BSD 2-Clause) and city data from GeoNames and Kartverket (CC BY 4.0). Nothing is sent.');
    const links = [...defined(footerHtml.match(/<div class="footer-powered">[\s\S]*?<\/div>/), 'the credits')[0].matchAll(/href="([^"]+)"/g)].map((m) => defined(m[1], 'href'));
    assert.deepEqual(links, app.urls);
    // Only the projects and data the function is built on: no icons, languages, libraries or fonts.
    for (const word of ['Inter', 'Lucide', 'Kotlin', 'Jetpack', 'desugaring', 'Material', 'Intl', 'OFL']) assert.ok(!web.includes(word), `the credits do not name ${word}`);
  });

  test('footer company line: the same text as the app, and the same pages behind Privacy and Disclaimer', () => {
    const app = annotated('\\u00A9 2026 Stormberry AS');
    const web = para(null);
    assert.equal(web.text, app.text);
    assert.ok(app.text.includes(text('ORG_NR')), 'the app line carries the unbreakable Org.nr.');
    assert.equal(web.urls[0], 'https://virksomhet.brreg.no/nb/oppslag/enheter/937751249');
    assert.equal(web.urls[0], app.urls[0]);
    assert.equal(web.urls[1]?.replace(/#labs$/, ''), app.urls[1]);
    // The website opens its own disclaimer page; the app opens the same page by its address.
    assert.equal(new URL(defined(web.urls[2], 'disclaimer'), 'https://moon.stormberry.as/').href, app.urls[2]);
  });
}
