// Loads the page's three scripts into a Node vm context the way a browser does: as classic
// scripts sharing one global scope, suncalc.js, then cities.js, then the compiled app.js. Used by
// the Node tests (tests/*.test.ts) to call the page's own functions with no change to the page
// code. Not a test file itself. Copied on 22 October 2026 from SunApp's (added there on 21 October
// 2026 with its TypeScript move), with two changes for MoonApp: app.js reaches its two tab buttons
// through document.querySelectorAll('.tab'), so the stub document answers that one selector (and
// classList has add and remove); and the phase disc is drawn on a canvas, so an element hands out
// a 2D context that records each call by name and draws nothing.
//
// The document is a stub, just enough for app.js: getElementById hands out one inert element
// per id, listeners are recorded and fired only when a test asks (dom.fire), and
// requestAnimationFrame runs its callback at once so the illumination bar's width is set before a
// test reads it. setTimeout callbacks (only the smooth scroll) are dropped.
//
// Two traps, written down so nobody has to find them again:
//  * Everything the page code creates (a Date, an array, a promise) belongs to the context's
//    realm, so `instanceof Date` in this realm is false for a page Date. Tests compare only
//    primitives: strings, numbers, booleans, and JSON. To call a page function, take it with
//    fn('name'), typed by PageFunctions below, and call it from TypeScript: code inside a string
//    passed to run() is JavaScript the strict check never reads, so run() is for reading a value.
//  * Top-level const and let of a classic script are not properties of the global object.
//    run('els.resMoonrise.textContent') reaches them, because the code runs as a script in that
//    same global scope; ctx.els does not.

import vm from 'node:vm';
import { readFileSync } from 'node:fs';

const REPO = new URL('../', import.meta.url);
/**
 * The checkout whose page the tests load: this one, unless MOONAPP_PAGE_ROOT names another (an
 * absolute directory). The build used that once, to run the helper and search tests against the
 * 90d11dd tree and show their expected values are what the hand-written app.js gave.
 */
const PAGE_ROOT = process.env.MOONAPP_PAGE_ROOT ? new URL(`file://${process.env.MOONAPP_PAGE_ROOT.replace(/\/?$/, '/')}`) : REPO;

/** A stand-in for one DOM element: the properties and methods app.js touches. */
export class FakeElement {
  readonly id: string;
  readonly tagName: string;
  value = '';
  className = '';
  hidden = false;
  readonly dataset: Record<string, string> = {};
  readonly style: Record<string, string> = {};
  readonly attributes = new Map<string, string>();
  readonly listeners = new Map<string, Array<(e: unknown) => unknown>>();
  readonly children: FakeElement[] = [];
  private html = '';
  private readonly dom: FakeDom;

  constructor(dom: FakeDom, id: string, tagName = 'DIV') {
    this.dom = dom;
    this.id = id;
    this.tagName = tagName;
  }

  get innerHTML(): string { return this.html; }
  set innerHTML(v: string) { this.html = String(v); if (this.html === '') this.children.length = 0; }
  /** Text, as a browser would give it back: the markup with its tags removed. */
  get textContent(): string { return this.html.replace(/<[^>]*>/g, ''); }
  set textContent(v: string | null) {
    this.html = String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  setAttribute(name: string, v: string): void {
    this.attributes.set(name, String(v));
    if (name === 'hidden') this.hidden = true;
  }
  removeAttribute(name: string): void {
    this.attributes.delete(name);
    if (name === 'hidden') this.hidden = false;
  }
  getAttribute(name: string): string | null {
    if (name === 'hidden') return this.hidden ? '' : null;
    return this.attributes.get(name) ?? null;
  }
  readonly classList = {
    set: new Set<string>(),
    toggle(name: string, on?: boolean): boolean {
      const want = on ?? !this.set.has(name);
      if (want) this.set.add(name); else this.set.delete(name);
      return want;
    },
    contains(name: string): boolean { return this.set.has(name); },
    add(name: string): void { this.set.add(name); },
    remove(name: string): void { this.set.delete(name); },
  };
  /** The phase canvas: a 2D context that records each call by name and draws nothing. */
  width = 90;
  height = 90;
  readonly drawn: string[] = [];
  getContext(): Record<string, unknown> {
    const calls = this.drawn;
    const gradient = { addColorStop: (): void => { calls.push('addColorStop'); } };
    return new Proxy({} as Record<string, unknown>, {
      get: (target, key) => {
        if (typeof key !== 'string') return undefined;
        if (key in target) return target[key];
        return (..._args: unknown[]): unknown => { calls.push(key); return key.startsWith('create') ? gradient : undefined; };
      },
      set: (target, key, v) => { if (typeof key === 'string') target[key] = v; return true; },
    });
  }
  addEventListener(type: string, fn: (e: unknown) => unknown): void {
    const list = this.listeners.get(type) ?? [];
    list.push(fn);
    this.listeners.set(type, list);
  }
  appendChild(child: FakeElement): FakeElement { this.children.push(child); return child; }
  querySelectorAll(selector: string): FakeElement[] {
    return selector === 'li' ? this.children.filter((c) => c.tagName === 'LI') : [];
  }
  focus(): void { this.dom.activeElement = this; }
  scrollIntoView(): void { /* nothing to scroll */ }
  closest(): null { return null; }
}

/** The stub document, and what the page did with it. */
export class FakeDom {
  readonly elements = new Map<string, FakeElement>();
  readonly listeners = new Map<string, Array<(e: unknown) => unknown>>();
  activeElement: FakeElement | null = null;

  getElementById(id: string): FakeElement {
    let el = this.elements.get(id);
    if (!el) {
      el = new FakeElement(this, id);
      // The two tab buttons carry data-tab in index.html.
      if (id === 'tab-city') el.dataset.tab = 'city';
      if (id === 'tab-gps') el.dataset.tab = 'gps';
      this.elements.set(id, el);
    }
    return el;
  }
  /** The one selector app.js asks the document for: '.tab', the two tab buttons in page order. */
  querySelectorAll(selector: string): FakeElement[] {
    if (selector !== '.tab') throw new Error(`the stub document does not answer ${selector}`);
    return [this.getElementById('tab-city'), this.getElementById('tab-gps')];
  }
  createElement(tag: string): FakeElement { return new FakeElement(this, '', tag.toUpperCase()); }
  addEventListener(type: string, fn: (e: unknown) => unknown): void {
    const list = this.listeners.get(type) ?? [];
    list.push(fn);
    this.listeners.set(type, list);
  }
}

/** A city as cities.js holds it (ts/cities.d.ts), the shape onCalculate reads from state.city. */
export interface PageCity {
  name: string; fold: string; country: string; cfold: string;
  lat: number; lon: number; tz: string; alt: string;
}

/**
 * The page functions the tests and tools call directly, typed as ts/app.ts declares them, so the
 * calls are TypeScript the strict check reads rather than JavaScript inside a string. A page
 * function runs in the page's realm whoever calls it; Dates go in and come out as ordinary Dates
 * (read them with getTime() or toISOString(), never instanceof). This is written by hand: when a
 * signature in ts/app.ts changes, change it here too. PAGE_ARITY below is checked on every
 * lookup, so a parameter added or removed on one side only fails at once.
 */
export interface PageFunctions {
  getNextLunations(fromDate: Date, tz: string): { nextNew: Date | null; nextFull: Date | null; newAt: Date | null; fullAt: Date | null };
  phaseCrossing(t0: number, t1: number, target: number): number;
  moonTimesFrom(start: Date, lat: number, lon: number, hours?: number): { rise?: Date; set?: Date };
  lunarTransitFrom(dayStart: Date, lat: number, lon: number, hours?: number): Date | null;
  localYmd(at: Date, tz: string): [number, number, number];
  localDayWindow(year: number, month: number, day: number, tz: string): { start: Date; end: Date };
  formatDate(d: Date): string;
  onCalculate(): Promise<void>;
  onCityInput(): void;
  /** Takes the click event; only target.closest is read. */
  onDropdownClick(e: { target: { closest(selector: string): FakeElement | undefined } }): void;
}

/** Each page function's declared parameter count (Function.length), checked by fn(). */
const PAGE_ARITY: { readonly [K in keyof PageFunctions]: number } = {
  getNextLunations: 2,
  phaseCrossing: 3,
  moonTimesFrom: 3,
  lunarTransitFrom: 3,
  localYmd: 2,
  localDayWindow: 4,
  formatDate: 1,
  onCalculate: 0,
  onCityInput: 0,
  onDropdownClick: 1,
};

/** The page's top-level objects the tests use: the vendored SunCalc (the parts read), its state
    and its element cache. */
export interface PageGlobals {
  SunCalc: {
    getMoonPosition(date: Date, lat: number, lng: number): { altitude: number; azimuth: number; distance: number };
    getMoonIllumination(date: Date): { fraction: number; phase: number; angle: number };
  };
  state: { tab: string; city: PageCity | null };
  /** The entries the tests read or write, each a stub element; add a name here to use another. */
  els: Record<'dateInput' | 'resMoonrise' | 'resTransit' | 'resMoonset' | 'citySearch' | 'cityDropdown' | 'citySelectedTxt' | 'errorMsg' | 'resultsCard', FakeElement>;
}

/** A loaded page: run code in its global scope, and reach its stub document. */
export interface LoadedPage {
  /** A page function by name, typed by PageFunctions; it throws if the page has no such function
      or its parameter count is not the one PageFunctions declares. */
  fn<K extends keyof PageFunctions>(name: K): PageFunctions[K];
  /** The page's state or els (top-level consts, which are not properties of the global object). */
  get<K extends keyof PageGlobals>(name: K): PageGlobals[K];
  /** Runs code as a script in the page's global scope and returns its value (from the page's realm). */
  run<T = unknown>(code: string): T;
  /** Puts a value on the page's global object under name, for run() to use. */
  set(name: string, value: unknown): void;
  /** Fires every document listener of this type, as the browser would. */
  fire(type: string): void;
  readonly dom: FakeDom;
}

/**
 * Loads suncalc.js, cities.js and app.js from root (the repository unless given) into a fresh
 * context, then fires DOMContentLoaded so init() runs. With init: false it stops before that.
 */
export function loadPage({ root = PAGE_ROOT, init = true }: { root?: URL; init?: boolean } = {}): LoadedPage {
  const dom = new FakeDom();
  const sandbox: Record<string, unknown> = {
    document: dom,
    console,
    requestAnimationFrame: (fn: (t: number) => void): number => { fn(0); return 0; },
    setTimeout: (): number => 0,
  };
  const ctx = vm.createContext(sandbox);
  // window is the global object itself, as in a browser: SunCalc publishes itself on it.
  vm.runInContext('globalThis.window = globalThis;', ctx);
  for (const file of ['suncalc.js', 'cities.js', 'app.js']) {
    vm.runInContext(readFileSync(new URL(file, root), 'utf8'), ctx, { filename: file });
  }
  const page: LoadedPage = {
    fn: <K extends keyof PageFunctions>(name: K): PageFunctions[K] => {
      const f: unknown = vm.runInContext(`typeof ${name} === 'function' ? ${name} : undefined`, ctx);
      if (typeof f !== 'function') throw new Error(`the page has no function ${name}`);
      if (f.length !== PAGE_ARITY[name]) throw new Error(`${name} takes ${f.length} parameters in the page, ${PAGE_ARITY[name]} in PageFunctions`);
      return f as PageFunctions[K];
    },
    get: <K extends keyof PageGlobals>(name: K): PageGlobals[K] => vm.runInContext(name, ctx) as PageGlobals[K],
    run: <T>(code: string): T => vm.runInContext(code, ctx) as T,
    set: (name, value) => { (ctx as Record<string, unknown>)[name] = value; },
    fire: (type) => { for (const fn of dom.listeners.get(type) ?? []) fn({ type }); },
    dom,
  };
  if (init) page.fire('DOMContentLoaded');
  return page;
}
