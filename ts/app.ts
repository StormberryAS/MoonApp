/*! MoonApp: app.js is compiled from ts/app.ts by tools/build-ts.sh, which leaves out every comment
    but this one: the explanations are in the source. Edit the .ts, never the .js: the build refuses
    to overwrite a hand edit, and its check mode fails on one. */

/*
 * MoonApp: ts/app.ts
 * A fully client-side moon data calculator.
 * Libraries: SunCalc (local), Intl API (built-in).
 * Typed coordinates resolve their timezone offline, from the nearest known
 * city. Nothing hits the network.
 * There is no device-location option: the stormberry.as zone sends
 * Permissions-Policy geolocation=(), which blocks it on every Labs host,
 * so it was removed on 2026-10-02.
 *
 * TypeScript since 22 October 2026, on SunApp's model of 21 October. Until then this was app.js
 * itself, plain JavaScript with JSDoc types checked by tsc (14be0fa). The runtime code is the
 * same, line for line; the types moved from JSDoc into the language. index.html still loads
 * app.js as a classic script after suncalc.js and cities.js, all three sharing one global scope,
 * so this file has no import or export: tsc compiles it as a script, not a module. The 'use
 * strict' that opened app.js is now written by tsc itself ("strict" implies "alwaysStrict"),
 * which also keeps the notice above in app.js. City, SunCalc and foldQuery are declared in
 * ts/cities.d.ts and ts/suncalc.d.ts, and the dropdown rows' _city expando in ts/app-dom.d.ts.
 */

/* ================================================================
   TYPES
   For the checker only: tsc erases them, so app.js has none of this.
================================================================ */
/** The resolved zone for a calculation. */
interface TzInfo {
  /** IANA identifier, for example "Europe/Oslo". */
  ianaId: string;
  /** Short name such as "CET", or "" when Intl has none. */
  abbreviation: string;
}

/** Everything renderResults shows. */
interface MoonResults {
  lat: number;
  lon: number;
  tz: string;
  tzAbbr: string;
  dateStr: string;
  /** Computed by onCalculate but not shown. */
  locationLabel?: string;
  moonriseStr: string | null;
  moonsetStr: string | null;
  transitStr: string | null;
  alwaysUp: boolean;
  alwaysDown: boolean;
  phaseName: string;
  illumPct: number;
  phaseAngle: number;
  phaseFraction: number;
  phaseRaw: number;
  distanceKm: number;
  nextNew: Date | null;
  nextFull: Date | null;
}

/* ================================================================
   SECTION 1: CITY DATABASE
   Identical to SunApp: 2,000+ cities with IANA timezone IDs.
================================================================ */
// The city catalogue lives in the shared cities.js, loaded by index.html
// before this file. Regenerate every app's copy with GitHub/update_cities.py.


/* ================================================================
   SECTION 2: APPLICATION STATE
================================================================ */
const state: { tab: string, city: City | null } = {
  tab:       'city',   /* 'city' | 'gps' */
  city:      null,     /* selected city object from CITIES */
};

/* ================================================================
   SECTION 3: DOM ELEMENT CACHE
   Cache all DOM references once at start-up.
================================================================ */
const els = {
  /* Every id below is in index.html, which loads this script last, so each cast only drops
     the null that getElementById allows for a missing element. */
  tabs:            document.querySelectorAll('.tab') as NodeListOf<HTMLButtonElement>,
  panelCity:       document.getElementById('panel-city') as HTMLElement,
  panelGps:        document.getElementById('panel-gps') as HTMLElement,
  citySearch:      document.getElementById('city-search') as HTMLInputElement,
  cityDropdown:    document.getElementById('city-dropdown') as HTMLUListElement,
  citySelected:    document.getElementById('city-selected') as HTMLElement,
  citySelectedTxt: document.getElementById('city-selected-text') as HTMLElement,
  cityClearBtn:    document.getElementById('city-clear-btn') as HTMLButtonElement,
  latInput:        document.getElementById('lat-input') as HTMLInputElement,
  lonInput:        document.getElementById('lon-input') as HTMLInputElement,
  latError:        document.getElementById('lat-error') as HTMLElement,
  lonError:        document.getElementById('lon-error') as HTMLElement,
  gpsEcho:         document.getElementById('gps-echo') as HTMLElement,
  dateInput:       document.getElementById('date-input') as HTMLInputElement,
  calculateBtn:    document.getElementById('calculate-btn') as HTMLButtonElement,
  errorMsg:        document.getElementById('error-msg') as HTMLElement,
  resultsCard:     document.getElementById('results-card') as HTMLElement,
  resCoords:       document.getElementById('res-coords') as HTMLElement,
  resDate:         document.getElementById('res-date') as HTMLElement,
  resTz:           document.getElementById('res-tz') as HTMLElement,
  resMoonrise:     document.getElementById('res-moonrise') as HTMLElement,
  resTransit:      document.getElementById('res-transit') as HTMLElement,
  resMoonset:      document.getElementById('res-moonset') as HTMLElement,
  phaseCanvas:     document.getElementById('phase-canvas') as HTMLCanvasElement,
  resPhaseName:    document.getElementById('res-phase-name') as HTMLElement,
  resIllumination: document.getElementById('res-illumination') as HTMLElement,
  resDistance:     document.getElementById('res-distance') as HTMLElement,
  resNextNew:      document.getElementById('res-next-new') as HTMLElement,
  resNextFull:     document.getElementById('res-next-full') as HTMLElement,
  resIllumPct:     document.getElementById('res-illum-pct') as HTMLElement,
  illumBarFill:    document.getElementById('illum-bar-fill') as HTMLElement,
  loadingOverlay:  document.getElementById('loading-overlay') as HTMLElement,
};

/* ================================================================
   SECTION 4: INITIALISATION
================================================================ */
function init(): void {
  els.dateInput.value = getTodayString();
  els.tabs.forEach(btn => btn.addEventListener('click', onTabClick));
  els.citySearch.addEventListener('input', onCityInput);
  els.citySearch.addEventListener('keydown', onCityKeydown);
  els.cityDropdown.addEventListener('click', onDropdownClick);
  els.cityClearBtn.addEventListener('click', clearCitySelection);
  /* Editing a coordinate clears its error and the "Using ..." line, which
     described the previous value, until the next Calculate. */
  els.latInput.addEventListener('input', () => { setFieldError(els.latInput, els.latError, null); setEcho(null); });
  els.lonInput.addEventListener('input', () => { setFieldError(els.lonInput, els.lonError, null); setEcho(null); });
  els.calculateBtn.addEventListener('click', onCalculate);
  /* Close dropdown when clicking outside the search widget */
  document.addEventListener('click', e => {
    if (!(els.citySearch.closest('.search-wrapper') as Element).contains(e.target as Node | null)) closeDropdown();
  });

  // Last, and on its own: nothing the offline pilot does can reach the calculator.
  try { offlinePilot(); } catch { /* the page works the same without it */ }
}

/* ================================================================
   SECTION 5: TAB SWITCHING
================================================================ */
function onTabClick(e: MouseEvent): void {
  const btn = e.currentTarget as HTMLButtonElement;
  /* Both .tab buttons in index.html carry data-tab ("city" or "gps"). */
  const tab = btn.dataset.tab as string;
  state.tab = tab;
  els.tabs.forEach(t => {
    const active = t.dataset.tab === tab;
    t.classList.toggle('active', active);
    t.setAttribute('aria-selected', String(active));
  });
  [els.panelCity, els.panelGps].forEach(p => {
    p.hidden = true;
    p.classList.remove('active');
  });
  const panel = document.getElementById('panel-' + tab) as HTMLElement;
  panel.hidden = false;
  panel.classList.add('active');
}

/* ================================================================
   SECTION 6: CITY AUTOCOMPLETE (fully offline fuzzy search)
================================================================ */
let highlightedIndex = -1;

function onCityInput(): void {
  const query = els.citySearch.value.trim().toLowerCase();
  if (state.city) { state.city = null; els.citySelected.hidden = true; }
  if (query.length < 1) { closeDropdown(); return; }
  const qf = foldQuery(query);
  // Prefix matches first. With 25,000 cities a bare substring filter
  // buries the obvious answer: "erdal" returned Cloverdale, South
  // Riverdale and Terdal ahead of Erdal, and with only 8 rows shown the
  // city being typed could fall off the list entirely.
  const startsWith: City[] = [], contains: City[] = [];
  for (const c of CITIES) {
    // c.alt is the folded English exonym where GeoNames stores the local
    // name, so "gothenburg" finds Goteborg and "cologne" finds Koeln.
    if (c.fold.startsWith(qf) || c.alt.startsWith(qf)) startsWith.push(c);
    else if (c.fold.includes(qf) || c.alt.includes(qf) || c.cfold.includes(qf)) contains.push(c);
  }
  const matches = startsWith.concat(contains).slice(0, 8);
  if (!matches.length) { closeDropdown(); return; }
  renderDropdown(matches);
}

function renderDropdown(cities: City[]): void {
  els.cityDropdown.innerHTML = '';
  highlightedIndex = -1;
  cities.forEach((city, idx) => {
    const li = document.createElement('li');
    li.setAttribute('role', 'option');
    li.setAttribute('id', 'city-option-' + idx);
    li.dataset.idx = String(idx);
    li.innerHTML = '<span class="city-name">' + escapeHtml(city.name) + '</span>'
                 + '<span class="city-country">' + escapeHtml(city.country) + '</span>';
    li._city = city;
    els.cityDropdown.appendChild(li);
  });
  els.cityDropdown.hidden = false;
  els.citySearch.setAttribute('aria-expanded', 'true');
}

function onCityKeydown(e: KeyboardEvent): void {
  if (els.cityDropdown.hidden) return;
  const items = els.cityDropdown.querySelectorAll('li');
  if (!items.length) return;
  if (e.key === 'ArrowDown') {
    e.preventDefault();
    highlightedIndex = Math.min(highlightedIndex + 1, items.length - 1);
    updateHighlight(items);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    highlightedIndex = Math.max(highlightedIndex - 1, 0);
    updateHighlight(items);
  } else if (e.key === 'Enter') {
    e.preventDefault();
    /* Every row was built by renderDropdown, which always sets _city, and highlightedIndex is
       either -1 or a row index (the two arrow keys clamp it), so the row exists. The casts emit
       nothing: the JavaScript is the 90d11dd code. */
    if (highlightedIndex >= 0) selectCity((items[highlightedIndex] as HTMLLIElement)._city as City);
  } else if (e.key === 'Escape') {
    closeDropdown();
  }
}

function updateHighlight(items: NodeListOf<HTMLLIElement>): void {
  items.forEach((li, i) => li.classList.toggle('highlighted', i === highlightedIndex));
}

function onDropdownClick(e: MouseEvent): void {
  const li = (e.target as Element).closest('li');
  if (li && li._city) selectCity(li._city);
}

function selectCity(city: City): void {
  state.city = city;
  els.citySearch.value = '';
  els.citySelectedTxt.textContent = city.name + ', ' + city.country;
  els.citySelected.hidden = false;
  closeDropdown();
}

function clearCitySelection(): void {
  state.city = null;
  els.citySelected.hidden = true;
  els.citySearch.value = '';
  els.citySearch.focus();
}

function closeDropdown(): void {
  els.cityDropdown.hidden = true;
  els.cityDropdown.innerHTML = '';
  els.citySearch.setAttribute('aria-expanded', 'false');
  highlightedIndex = -1;
}

/* ================================================================
   SECTION 7: TYPED NUMBERS
================================================================ */
// Shared Labs parser: accepts "60,39" and a Unicode minus, rejects anything else or out of range (null).
function parseDecimal(text: string | null | undefined, min: number, max: number): number | null {
  if (text == null) return null;
  let s = String(text).trim().replace(/[\u2212\u2012\u2013\u2014\uFE63\uFF0D]/g, '-').replace(/\s+/g, '');
  if (/^[+-]?\d+,\d+$/.test(s)) s = s.replace(',', '.');
  if (!/^[+-]?(\d+\.?\d*|\.\d+)$/.test(s)) return null;
  const n = Number(s);
  return Number.isFinite(n) && n >= min && n <= max ? n : null;
}

/** Show (msg) or clear (null) the inline message under one coordinate field. */
function setFieldError(input: HTMLInputElement, errorEl: HTMLElement, msg: string | null): void {
  if (msg) {
    errorEl.textContent = msg;
    errorEl.hidden = false;
    input.setAttribute('aria-invalid', 'true');
  } else {
    errorEl.textContent = '';
    errorEl.hidden = true;
    input.removeAttribute('aria-invalid');
  }
}

/** Show (text) or clear (null) the "Using ..." line. Cleared text, not just
 *  hidden: aria-describedby still reads a hidden element it points at. */
function setEcho(text: string | null): void {
  els.gpsEcho.textContent = text || '';
  els.gpsEcho.hidden = !text;
}

/* ================================================================
   SECTION 8: TIMEZONE RESOLVER (fully offline)
   Timezone resolution never touches the network. City-tab zones come
   straight from the bundled city database, and typed coordinates resolve
   to the nearest known city's zone. Nothing hits the network.
================================================================ */
/** The IANA zone of the nearest catalogue city. */
function nearestCityTimezone(lat: number, lon: number): string {
  // Timezones are large political regions and the bundled city list is dense
  // near populated areas, so the nearest city's zone is the correct one in
  // practice. Equirectangular distance is plenty for a nearest-neighbour pick.
  let best: City | null = null, bestDist = Infinity;
  for (const c of CITIES) {
    let dLon = Math.abs(c.lon - lon);
    if (dLon > 180) dLon = 360 - dLon;
    const dLat = c.lat - lat;
    const x = dLon * Math.cos(((lat + c.lat) / 2) * Math.PI / 180);
    const dist = x * x + dLat * dLat;
    if (dist < bestDist) { bestDist = dist; best = c; }
  }
  return best ? best.tz : (Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
}

function resolveTimezone(lat: number, lon: number): TzInfo {
  const ianaId = nearestCityTimezone(lat, lon);
  return { ianaId, abbreviation: getTimezoneAbbreviation(ianaId, els.dateInput.value) };
}

/* ================================================================
   SECTION 9: MAIN CALCULATE HANDLER
================================================================ */
async function onCalculate(): Promise<void> {
  clearError();
  /* Both tabs below assign lat, lon and tzInfo or return early, so the NaN start values are
     never read; they exist so the checker can see lat and lon are always numbers. */
  let lat = NaN, lon = NaN;
  let tzInfo: TzInfo | undefined;

  if (state.tab === 'city') {
    if (!state.city) { showError('Please select a city from the search list first.'); return; }
    lat = state.city.lat; lon = state.city.lon;
    tzInfo = { ianaId: state.city.tz, abbreviation: getTimezoneAbbreviation(state.city.tz, els.dateInput.value) };

  } else if (state.tab === 'gps') {
    const latVal = parseDecimal(els.latInput.value, -90, 90);
    const lonVal = parseDecimal(els.lonInput.value, -180, 180);

    setFieldError(els.latInput, els.latError,
      latVal === null ? 'Enter a latitude between -90 and 90, such as 60.39 or 60,39.' : null);
    setFieldError(els.lonInput, els.lonError,
      lonVal === null ? 'Enter a longitude between -180 and 180, such as 5.32 or 5,32.' : null);

    if (latVal === null || lonVal === null) {
      /* Do not compute, and do not leave an older result showing under the error. */
      setEcho(null);
      els.resultsCard.setAttribute('hidden', '');
      (latVal === null ? els.latInput : els.lonInput).focus();
      return;
    }

    lat = latVal; lon = lonVal;
    /* Echo the numbers actually used, so "6,5" visibly became 6.5. */
    setEcho('Using ' + lat + ', ' + lon);
    showLoading(true);
    /* resolveTimezone is synchronous; the await is kept from the JavaScript, because removing
       it would change when the rest of this function runs (one microtask later, as before). */
    try { tzInfo = await resolveTimezone(lat, lon); }
    catch (err) { showLoading(false); showError('Could not resolve timezone: ' + (err as Error).message); return; }
    showLoading(false);
  }

  /* Parse date: use UTC noon so SunCalc gets the correct calendar day */
  const [year, month, day] = els.dateInput.value.split('-').map(Number);
  if (!year || !month || !day) { showError('Please select a valid date.'); return; }
  const dateForCalc = new Date(Date.UTC(year, month - 1, day, 12, 0, 0));
  /* Both tabs set tzInfo or return above; state.tab is only ever 'city' or 'gps'. */
  const tz = (tzInfo as TzInfo).ianaId;

  /* Moon rise / set.
     THE DAY WINDOW IS THE CITY'S LOCAL DAY, and it is passed in UTC mode.
     Without the fourth argument SunCalc calls setHours(0,0,0,0), which is midnight in the
     VISITOR'S browser timezone, while the results are formatted in the city's. The two
     disagreed: with a browser on Europe/Oslo, 96 of 561 moonrise rows differed from the
     Android app and 22 differed by a full calendar day, and a Bergen date could show no
     moonrise at all to a visitor on UTC. Anchor on the selected city's midnight instead,
     expressed as an instant, so the answer matches the label. Fixed 2026-09-04.
     Until 23 October 2026 (F-5) that midnight was found with the zone's offset at 12:00 UTC,
     and the window was always 24 hours long. On a day the zone changes its offset the window
     therefore started an hour off (at 23:00 the evening before on a spring-forward day, at 01:00
     on a fall-back day) and did not end at the next midnight: in Bergen on 25 October 2026 the
     moon culminates at 00:20:13, which fell in neither day's window, so both days showed the
     dash, and moonrise and moonset were searched over the wrong hours. Now the window is the
     local day itself, from its first instant to the first instant of the next day (23 or 25
     hours on those days), as the Android app's date.atStartOfDay(zone) gives it. */
  const dayWindow = localDayWindow(year, month, day, tz);
  const cityMidnightUtc = dayWindow.start;
  const windowHours = (dayWindow.end.getTime() - cityMidnightUtc.getTime()) / 3600000;
  /* A date the zone skipped altogether has an empty window: Pacific/Apia went from 29 to
     31 December 2011, so the 30th starts and ends at the same instant (as java.time's
     atStartOfDay gives it). Searching nothing would show Always Up or Always Down beside a
     dash, a card that contradicts itself; 90d11dd searched 24 hours and showed another day's
     times under this label. Say what happened instead, as the Android app does (23 October
     2026, fix round 2). */
  if (windowHours <= 0) {
    showError('This date did not happen here: the clocks in ' + tz + ' skipped it. Please select another date.');
    return;
  }
  /* NOT SunCalc.getMoonTimes. The 2026-09-04 fix above anchored the window on the city's
     local midnight and then handed that instant to getMoonTimes, which opens with

         var a = new Date(n); r ? a.setUTCHours(0,0,0,0) : a.setHours(0,0,0,0);

     and so FLOORS it back to UTC midnight. East of Greenwich the city's local midnight falls
     on the previous UTC day, so this page reported the PREVIOUS DAY'S moonrise and moonset
     under the selected date's label: Oslo on 2026-01-15 showed the 14th. The Android app was
     the one that was right. moonTimesFrom is getMoonTimes' own hourly search with the
     truncation removed, so the window really does start where it is told to. Fixed
     2026-09-05. */
  const moonTimes    = moonTimesFrom(cityMidnightUtc, lat, lon, windowHours);
  const moonriseValid = moonTimes.rise instanceof Date && !isNaN(moonTimes.rise.getTime());
  const moonsetValid  = moonTimes.set  instanceof Date && !isNaN(moonTimes.set.getTime());
  /* DO NOT TRUST suncalc's alwaysUp/alwaysDown flags. Its sign test reads a parabola
     evaluated at a vertex that can lie outside the sampled window, and a sweep of latitudes
     -89 to +89 across 2026 found 1,485 of 12,217 flagged days labelled the wrong way round,
     12.2 per cent, worst at Tromso and Longyearbyen. Telling a Norwegian visitor the moon is
     down all day when it is up all day is the opposite of the truth, not a rounding error.
     Decide from the moon's own altitude instead, which is a direct measurement. The Android
     app does the same in MoonCalc.dayKind. Fixed 2026-09-04.
     "Up" means above the same threshold moonTimesFrom calls a rise: the centre 0.133 degrees
     above the horizon (MOON_RISE_ALTITUDE). Until 22 October 2026 this tested peak > 0, so a moon
     whose centre peaked between 0 and 0.133 degrees was labelled Always Up although by this
     page's own definition it never rose: 22 of 6,274 such days in a 2026 sweep (F-3). */
  let alwaysUp = false, alwaysDown = false;
  if (!moonriseValid && !moonsetValid) {
    let peak = -Infinity;
    for (let i = 0; i <= windowHours * 4; i++) {
      const a = SunCalc.getMoonPosition(new Date(cityMidnightUtc.getTime() + i * 900000), lat, lon).altitude;
      if (a > peak) peak = a;
    }
    alwaysUp = peak > MOON_RISE_ALTITUDE;
    alwaysDown = !alwaysUp;
  }
  const moonriseStr   = moonriseValid ? formatTime(moonTimes.rise as Date, tz) : null;
  const moonsetStr    = moonsetValid  ? formatTime(moonTimes.set as Date,  tz) : null;

  /* Lunar transit (highest point in sky). None on a day the moon stays down: lunarTransitFrom
     only counts a culmination above MOON_RISE_ALTITUDE, on the same 15-minute samples as the
     peak above, so on an Always Down day it finds none and the row shows the dash, as the
     Android app hides it (F-3). */
  const transitStr = findLunarTransit(cityMidnightUtc, lat, lon, tz, windowHours);

  /* Phase + illumination */
  const illum      = SunCalc.getMoonIllumination(dateForCalc);
  const phaseName  = getMoonPhaseName(illum.phase);
  const illumPct   = Math.round(illum.fraction * 100);

  /* Distance */
  const pos        = SunCalc.getMoonPosition(dateForCalc, lat, lon);
  const distanceKm = Math.round(pos.distance);

  /* Next lunations: the first at or after the start of the selected day, dated in the place's
     zone (F-1, 22 October 2026). */
  const { nextNew, nextFull } = getNextLunations(dateForCalc, tz);

  /* Location label */
  /* The city tab returned above unless a city is selected, and nothing in between awaits. */
  const city = state.city as City;
  const locationLabel = state.tab === 'city'
    ? city.name + ', ' + city.country
    : lat.toFixed(4) + '\u00b0, ' + lon.toFixed(4) + '\u00b0';

  renderResults({
    lat, lon, tz, tzAbbr: (tzInfo as TzInfo).abbreviation,
    dateStr: formatDate(dateForCalc), locationLabel,
    moonriseStr, moonsetStr, transitStr, alwaysUp, alwaysDown,
    phaseName, illumPct,
    phaseAngle: illum.angle, phaseFraction: illum.fraction, phaseRaw: illum.phase,
    distanceKm, nextNew, nextFull,
  });
}

/* ================================================================
   SECTION 10: RESULTS RENDERING
================================================================ */
function renderResults({
  lat, lon, tz, tzAbbr, dateStr,
  moonriseStr, moonsetStr, transitStr, alwaysUp, alwaysDown,
  phaseName, illumPct, phaseAngle, phaseRaw,
  distanceKm, nextNew, nextFull,
}: MoonResults): void {
  /* Meta pills */
  els.resCoords.textContent = lat.toFixed(4) + '\u00b0, ' + lon.toFixed(4) + '\u00b0';
  els.resDate.textContent   = dateStr;
  els.resTz.textContent     = tzAbbr ? tzAbbr + ' / ' + tz : tz;

  /* Moonrise */
  if (alwaysUp) {
    els.resMoonrise.innerHTML = '<span class="moon-time-value polar-always-up">\uD83C\uDF15 Always Up</span>';
    els.resMoonrise.className = '';
  } else if (alwaysDown) {
    els.resMoonrise.innerHTML = '<span class="moon-time-value polar-always-down">\uD83C\uDF11 Always Down</span>';
    els.resMoonrise.className = '';
  } else {
    els.resMoonrise.textContent = moonriseStr || '\u2014';
    els.resMoonrise.className = 'moon-time-value mono';
  }

  /* Lunar transit */
  els.resTransit.textContent = transitStr || '\u2014';
  els.resTransit.className = 'moon-time-value mono';

  /* Moonset */
  if (alwaysUp) {
    els.resMoonset.innerHTML = '<span class="moon-time-value polar-always-up">\uD83C\uDF15 Always Up</span>';
    els.resMoonset.className = '';
  } else if (alwaysDown) {
    els.resMoonset.innerHTML = '<span class="moon-time-value polar-always-down">\uD83C\uDF11 Always Down</span>';
    els.resMoonset.className = '';
  } else {
    els.resMoonset.textContent = moonsetStr || '\u2014';
    els.resMoonset.className = 'moon-time-value mono';
  }

  /* Phase */
  els.resPhaseName.textContent    = phaseName;
  els.resIllumination.textContent = illumPct + '% illuminated';

  /* Phase disc canvas */
  drawPhaseDisc(els.phaseCanvas, phaseRaw, phaseAngle);

  /* Distance */
  els.resDistance.textContent = distanceKm.toLocaleString('en-GB');

  /* Next lunations */
  els.resNextNew.textContent  = nextNew  ? formatDate(nextNew)  : 'Not found';
  els.resNextFull.textContent = nextFull ? formatDate(nextFull) : 'Not found';

  /* Illumination bar */
  els.resIllumPct.textContent = illumPct + '%';
  requestAnimationFrame(() => { els.illumBarFill.style.width = illumPct + '%'; });

  /* Reveal results and scroll */
  els.resultsCard.removeAttribute('hidden');
  setTimeout(() => els.resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100);
}

/* ================================================================
   SECTION 11: MOON PHASE HELPERS
================================================================ */

/**
 * Maps a SunCalc phase fraction (0..1) to one of the 8 named phases.
 * 0 = New Moon, 0.25 = First Quarter, 0.5 = Full Moon, 0.75 = Last Quarter.
 */
function getMoonPhaseName(phase: number): string {
  if (phase < 0.0625 || phase >= 0.9375) return 'New Moon';
  if (phase < 0.1875)                    return 'Waxing Crescent';
  if (phase < 0.3125)                    return 'First Quarter';
  if (phase < 0.4375)                    return 'Waxing Gibbous';
  if (phase < 0.5625)                    return 'Full Moon';
  if (phase < 0.6875)                    return 'Waning Gibbous';
  if (phase < 0.8125)                    return 'Last Quarter';
  return 'Waning Crescent';
}

/**
 * The next New Moon and Full Moon: the first of each at or after the start of the selected day
 * in the place's zone, dated in that zone (decision D-4). fromDate is 12:00 UTC of the selected
 * date, tz the place's IANA zone. nextNew and nextFull are 12:00 UTC of the local date the event
 * falls on, so formatDate prints that date; newAt and fullAt are the events themselves.
 *
 * Until 22 October 2026 (F-1) this sampled the phase once a day at 12:00 UTC and reported the
 * first noon past the crossing, a UTC date: any lunation between noon and midnight UTC showed
 * a day late, for about half of all start dates (the full moon of 1 February 2026, 17:33 UTC,
 * showed as 2 February). Now the daily samples only bracket each crossing, phaseCrossing finds
 * the instant, and localYmd dates it in the place's zone. The samples start two days
 * early, at 12:00 UTC, because a zone up to 14 hours ahead of UTC (Kiritimati) begins the
 * selected day at 10:00 UTC the day before; so an event earlier on the selected local day is
 * found too. One that falls before that day is skipped and the search goes on to the next. The
 * Android app does the same in MoonPhase.nextLunations, held to it by lunation-parity.csv
 * (tools/gen-lunar-parity.ts).
 *
 * The dates are compared as numbers (dayKey). Until 23 October 2026 they were compared as
 * "YYYY-MM-DD" strings, which stop sorting by date at year 10000 ("10000-01-02" sorts before
 * "9999-12-31"), and the date field takes years up to 275760: Oslo on 31 December 9999 showed
 * "Not found" for both, and on 20 January 10000 a new moon before the chosen day.
 */
function getNextLunations(fromDate: Date, tz: string): { nextNew: Date | null, nextFull: Date | null, newAt: Date | null, fullAt: Date | null } {
  const DAY = 86400000;
  const selected = dayKey(fromDate.getUTCFullYear(), fromDate.getUTCMonth() + 1, fromDate.getUTCDate());
  const onOrAfter = (at: number): boolean => dayKey(...localYmd(new Date(at), tz)) >= selected;
  const start = fromDate.getTime() - 2 * DAY;
  let newAt: number | null = null;
  let fullAt: number | null = null;
  let prevPhase = SunCalc.getMoonIllumination(new Date(start)).phase;
  for (let d = 1; d <= LUNATION_SEARCH_DAYS; d++) {
    const t1 = start + d * DAY;
    const currPhase = SunCalc.getMoonIllumination(new Date(t1)).phase;
    /* New Moon: phase wraps from >0.75 back to <0.25 */
    if (newAt === null && prevPhase > 0.75 && currPhase < 0.25) {
      const at = phaseCrossing(t1 - DAY, t1, 1);
      if (onOrAfter(at)) newAt = at;
    }
    /* Full Moon: phase crosses 0.5 going upward */
    if (fullAt === null && prevPhase < 0.5 && currPhase >= 0.5) {
      const at = phaseCrossing(t1 - DAY, t1, 0.5);
      if (onOrAfter(at)) fullAt = at;
    }
    if (newAt !== null && fullAt !== null) break;
    prevPhase = currPhase;
  }
  const noonOf = (at: number | null): Date | null => {
    if (at === null) return null;
    const [y, m, d] = localYmd(new Date(at), tz);
    return new Date(Date.UTC(y, m - 1, d, 12, 0, 0));
  };
  return {
    nextNew: noonOf(newAt), nextFull: noonOf(fullAt),
    newAt: newAt === null ? null : new Date(newAt), fullAt: fullAt === null ? null : new Date(fullAt),
  };
}

/** Daily samples getNextLunations takes, from two days before the selected one: enough for both
    events, since a lunation is 29.53 days. */
const LUNATION_SEARCH_DAYS = 38;

/**
 * The instant (epoch milliseconds) at which the moon's phase reaches target inside [t0, t1],
 * by bisection: 0.5 for a full moon, 1 for a new moon (where the phase wraps from 1 to 0, so a
 * phase below 0.5 counts as past 1). Thirty-two halvings of one day leave 0.02 ms. The Android
 * app runs the same bisection in MoonPhase.crossing.
 */
function phaseCrossing(t0: number, t1: number, target: number): number {
  const f = (t: number): number => {
    let p = SunCalc.getMoonIllumination(new Date(t)).phase;
    if (target === 1 && p < 0.5) p += 1;
    return p - target;
  };
  let a = t0, b = t1;
  for (let k = 0; k < 32; k++) {
    const m = (a + b) / 2;
    if (f(a) * f(m) <= 0) b = m; else a = m;
  }
  return Math.round((a + b) / 2);
}

/** The date of an instant in an IANA zone, as [year, month 1 to 12, day]; the UTC date if Intl
    does not know the zone. */
function localYmd(at: Date, tz: string): [number, number, number] {
  try {
    const p: Record<string, string> = {};
    for (const { type, value } of new Intl.DateTimeFormat('en-US', {
      timeZone: tz, year: 'numeric', month: 'numeric', day: 'numeric',
    }).formatToParts(at)) p[type] = value;
    return [Number(p.year), Number(p.month), Number(p.day)];
  } catch {
    return [at.getUTCFullYear(), at.getUTCMonth() + 1, at.getUTCDate()];
  }
}

/** A calendar date as one number that orders as the dates do, for every year the date field
    takes (1 to 275760). */
function dayKey(year: number, month: number, day: number): number {
  return (year * 100 + month) * 100 + day;
}

/** Offset of an IANA zone from UTC at an instant, in milliseconds (local time minus UTC); 0 when
    Intl does not know the zone, so the day falls back to the UTC one. hourCycle h23 because
    hour12: false gives "24" for midnight in some older browsers. */
function zoneOffsetMs(tz: string, at: number): number {
  try {
    const p: Record<string, string> = {};
    for (const { type, value } of new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric',
      hour: 'numeric', minute: 'numeric', second: 'numeric',
    }).formatToParts(new Date(at))) p[type] = value;
    const wall = new Date(0);
    wall.setUTCFullYear(Number(p.year), Number(p.month) - 1, Number(p.day));
    wall.setUTCHours(Number(p.hour), Number(p.minute), Number(p.second), 0);
    return wall.getTime() - Math.floor(at / 1000) * 1000;
  } catch {
    return 0;
  }
}

/**
 * The first instant of a local date in an IANA zone, as java.time's date.atStartOfDay(zone) gives
 * it, which is where the Android app starts the same day (F-5, 23 October 2026). Normally the
 * zone's midnight. The offset in force at midnight is one of the offsets a day either side; each
 * is tried, and of those that put the instant at exactly local midnight the earlier wins (a
 * midnight that occurs twice). When neither does, midnight is skipped by a change of offset
 * (Santiago, Asuncion, Havana, Beirut and Cairo move their clocks at 00:00) and the day starts at
 * the change itself, 01:00 local. day may run past the month's end; Date.UTC carries it over.
 */
function startOfLocalDay(year: number, month: number, day: number, tz: string): number {
  const DAY = 86400000;
  const wall = Date.UTC(year, month - 1, day);
  const before = zoneOffsetMs(tz, wall - DAY), after = zoneOffsetMs(tz, wall + DAY);
  const valid = [wall - before, wall - after].filter((t) => zoneOffsetMs(tz, t) === wall - t);
  return valid.length ? Math.min(...valid) : wall - before;
}

/**
 * The selected local day as a window of instants, from its first instant to the first instant of
 * the next day: 24 hours long, except 23 on the day a zone moves its clocks forward and 25 on the
 * day it moves them back. moonTimesFrom, lunarTransitFrom and the peak scan in onCalculate all
 * search this window, and each day's ends where the next one's starts, so no hour is searched
 * twice or left out.
 */
function localDayWindow(year: number, month: number, day: number, tz: string): { start: Date, end: Date } {
  return { start: new Date(startOfLocalDay(year, month, day, tz)), end: new Date(startOfLocalDay(year, month, day + 1, tz)) };
}

/**
 * SunCalc.getMoonTimes with its day truncation removed.
 *
 * getMoonTimes begins `r ? a.setUTCHours(0,0,0,0) : a.setHours(0,0,0,0)`, which throws away
 * the time-of-day of the instant it is given. That makes it impossible to search a window
 * starting at a city's local midnight, which is exactly what this page needs. Everything
 * below is its own hourly search with quadratic interpolation and its horizon offset of 0.133
 * degrees (MOON_RISE_ALTITUDE), lifted unchanged except that the window starts at `start`.
 *
 * Kept in step with MoonCalc.times on the Android side and with
 * tools/gen-webparity-golden.ts, which generates the corpus the Kotlin is tested against
 * (tests/golden.test.ts holds this function to that corpus as well).
 *
 * start is the first instant of the window and hours its length: 24 except on a day the zone
 * changes its offset (localDayWindow). A crossing after the window's end is not counted. With
 * 24 hours that never happens, since the last parabola ends at hour 24, so the search is
 * getMoonTimes' own; on a 25-hour day one more parabola covers hours 24 to 26 (F-5).
 */
function moonTimesFrom(start: Date, lat: number, lon: number, hours = 24): { rise?: Date, set?: Date } {
  const hoursLater = (d: Date, h: number): Date => new Date(d.valueOf() + h * 3600000);
  const H0 = MOON_RISE_ALTITUDE;
  const alt = (h: number): number => SunCalc.getMoonPosition(hoursLater(start, h), lat, lon).altitude - H0;

  let rise: number | undefined;
  let set: number | undefined;
  let h0 = alt(0), ye;
  for (let i = 1; i - 1 < hours; i += 2) {
    const h1 = alt(i), h2 = alt(i + 1);
    const a = (h0 + h2) / 2 - h1;
    const b = (h2 - h0) / 2;
    const xe = -b / (2 * a);
    ye = (a * xe + b) * xe + h1;
    const d = b * b - 4 * a * h1;
    /* x1 and x2 are only read when roots > 0, which needs d >= 0, where both are assigned.
       The 0 start values exist for the checker and are never used. */
    let roots = 0, x1 = 0, x2 = 0;
    if (d >= 0) {
      const dx = Math.sqrt(d) / (Math.abs(a) * 2);
      x1 = xe - dx; x2 = xe + dx;
      if (Math.abs(x1) <= 1) roots++;
      if (Math.abs(x2) <= 1) roots++;
      if (x1 < -1) x1 = x2;
    }
    const inWindow = (x: number): boolean => i + x <= hours;
    if (roots === 1) { if (inWindow(x1)) { if (h0 < 0) rise = i + x1; else set = i + x1; } }
    else if (roots === 2) {
      const r = ye < 0 ? x2 : x1, s = ye < 0 ? x1 : x2;
      if (inWindow(r)) rise = i + r;
      if (inWindow(s)) set = i + s;
    }
    if (rise && set) break;
    h0 = h2;
  }
  const out: { rise?: Date, set?: Date } = {};
  if (rise) out.rise = hoursLater(start, rise);
  if (set) out.set = hoursLater(start, set);
  return out;
}

/**
 * The altitude of the moon's centre, in radians, at which suncalc's getMoonTimes (and so
 * moonTimesFrom) calls it risen: 0.133 degrees, for the moon's apparent radius and refraction.
 */
const MOON_RISE_ALTITUDE = 0.133 * Math.PI / 180;

/**
 * The Lunar Transit (highest altitude) in the hours from dayStart, formatted "HH:MM:SS" in the
 * target zone; null if the moon does not culminate above MOON_RISE_ALTITUDE inside the window.
 */
function findLunarTransit(dayStart: Date, lat: number, lon: number, tz: string, hours: number): string | null {
  const at = lunarTransitFrom(dayStart, lat, lon, hours);
  return at ? formatTime(at, tz) : null;
}

/**
 * The instant of the Lunar Transit in the hours (24 unless given) from dayStart, or null.
 *
 * dayStart is the CITY'S local midnight as an instant, the same window the rise and set search
 * uses. It used to rebuild UTC midnight from the passed date, so the transit was computed over
 * a different 24 hours than the two rows beside it and could name a peak belonging to the wrong
 * day. Fixed 2026-09-05.
 *
 * F-2 (22 October 2026). Until then the page printed the highest of the 97 samples, to the
 * second: up to 7.5 minutes off, and up to 7.5 minutes from the Android app, which refines. And
 * the highest sample is not always the culmination: a lunar day is about 24 h 50 min, so about
 * once a month the moon does not culminate inside a given day at all, and on other days the
 * declination moves enough that the moon is higher at one end of the window than at its
 * culmination; on all those days the page named midnight as the transit.
 *
 * Now the transit is the culmination itself: a sample higher than the one before it (or equal)
 * and the one after it, refined with the vertex of the parabola through the three. The samples
 * run one step past each end of the window, so a culmination within minutes of midnight is
 * found from the same three instants by the windows on both sides of it, and the half-open
 * window [start, end) gives it to exactly one of them. That holds because each day's window ends
 * where the next one starts, which is true since F-5 (23 October 2026) also on the days a zone
 * changes its offset (localDayWindow); before, those days' windows overlapped or left an hour
 * out. A day with no culmination inside it has no transit and the row shows the dash (decision
 * D-5). So does a culmination at or below MOON_RISE_ALTITUDE, the height the page calls a rise:
 * until 23 October 2026 any culmination above 0 counted, so on the few days the moon peaked
 * between 0 and 0.133 degrees the card said Always Down and the Android alarm still rang for a
 * transit (69 N, 0 E on 9 February 2026). The result is truncated to whole seconds.
 * MoonCalc.transitFrom on Android is the same algorithm, held to this one by
 * transit-parity.csv.
 */
function lunarTransitFrom(dayStart: Date, lat: number, lon: number, hours = 24): Date | null {
  const STEP_MS = 15 * 60 * 1000; /* 15-minute intervals */
  const STEPS = hours * 4;        /* samples per window: 96 for 24 hours */
  const LAST = Math.ceil(STEPS);
  /* alts[i + 1] is the altitude at sample i, for i from -1 to LAST + 1. */
  const alts: number[] = [];
  for (let i = -1; i <= LAST + 1; i++) {
    alts.push(SunCalc.getMoonPosition(new Date(dayStart.getTime() + i * STEP_MS), lat, lon).altitude);
  }
  const at = (i: number): number => alts[i + 1] as number;
  let peak = NaN, peakAlt = -Infinity;
  for (let i = 0; i <= LAST; i++) {
    if (!(at(i) >= at(i - 1) && at(i) > at(i + 1)) || at(i) <= MOON_RISE_ALTITUDE || at(i) <= peakAlt) continue;
    const denom = at(i - 1) - 2 * at(i) + at(i + 1);
    const off = denom < 0 ? 0.5 * (at(i - 1) - at(i + 1)) / denom : 0;
    const v = off > -1 && off < 1 ? i + off : i;
    if (v < 0 || v >= STEPS) continue;
    peak = v; peakAlt = at(i);
  }
  if (Number.isNaN(peak)) return null;
  return new Date(dayStart.getTime() + Math.trunc(peak * 15 * 60) * 1000);
}

/* ================================================================
   SECTION 12: PHASE DISC RENDERER
   Draws an accurate lunar phase disc on a <canvas> element.
   Algorithm: fill lit circle, then overlay shadow hemisphere using a
   clipped ellipse whose width scales with the shadow fraction.
================================================================ */
/**
 * phaseRaw is SunCalc's phase, 0 to 1. _phaseAngle is SunCalc's limb angle in radians, which the
 * drawing does not use (the underscore tells the checker so; the name is not visible at run time).
 */
function drawPhaseDisc(canvas: HTMLCanvasElement, phaseRaw: number, _phaseAngle: number): void {
  /* getContext('2d') is null only when the canvas already holds another kind of context,
     and nothing else draws on this one. */
  const ctx = canvas.getContext('2d') as CanvasRenderingContext2D;
  const W   = canvas.width,  H = canvas.height;
  const cx  = W / 2,        cy = H / 2;
  const r   = W / 2 - 2;   /* 2px outer padding */

  ctx.clearRect(0, 0, W, H);

  /* 1. Full lit disc with radial gradient for 3D feel */
  const litGrad = ctx.createRadialGradient(cx - r * 0.2, cy - r * 0.25, 0, cx, cy, r);
  litGrad.addColorStop(0,   '#f4f6ff');
  litGrad.addColorStop(0.55,'#c8d8f0');
  litGrad.addColorStop(1,   '#7b9fd4');
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, 2 * Math.PI);
  ctx.fillStyle = litGrad;
  ctx.fill();

  /* 2. Compute shadow geometry
     shadowFraction: 1 at new moon (all dark), 0 at full moon (all lit)
     Waxing (phase < 0.5): shadow on the RIGHT side
     Waning (phase >= 0.5): shadow on the LEFT side               */
  const shadowFraction = phaseRaw <= 0.5
    ? 1 - phaseRaw * 2          /* 1 -> 0 from new to full       */
    : (phaseRaw - 0.5) * 2;     /* 0 -> 1 from full to next new  */
  const shadowXRadius = r * shadowFraction;
  const shadowOnRight = phaseRaw < 0.5;

  /* 3. Draw shadow clipped to the disc */
  const shadowGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
  shadowGrad.addColorStop(0,   'rgba(8,10,22,0.93)');
  shadowGrad.addColorStop(0.7, 'rgba(6,8,18,0.97)');
  shadowGrad.addColorStop(1,   'rgba(4,6,14,0.99)');

  ctx.save();
  /* Clip everything to the disc boundary */
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, 2 * Math.PI);
  ctx.clip();

  if (shadowOnRight) {
    /* Fill the right half dark */
    ctx.beginPath();
    ctx.moveTo(cx, cy - r);
    ctx.arc(cx, cy, r, -Math.PI / 2, Math.PI / 2);
    ctx.closePath();
    ctx.fillStyle = shadowGrad;
    ctx.fill();
    /* Paint lit ellipse back on the right to form the crescent edge */
    if (shadowXRadius > 1) {
      ctx.beginPath();
      ctx.ellipse(cx, cy, shadowXRadius, r, 0, -Math.PI / 2, Math.PI / 2);
      ctx.closePath();
      ctx.fillStyle = litGrad;
      ctx.fill();
    }
  } else {
    /* Fill the left half dark */
    ctx.beginPath();
    ctx.moveTo(cx, cy - r);
    ctx.arc(cx, cy, r, Math.PI / 2, Math.PI * 1.5);
    ctx.closePath();
    ctx.fillStyle = shadowGrad;
    ctx.fill();
    /* Paint lit ellipse back on the left */
    if (shadowXRadius > 1) {
      ctx.beginPath();
      ctx.ellipse(cx, cy, shadowXRadius, r, 0, Math.PI / 2, Math.PI * 1.5);
      ctx.closePath();
      ctx.fillStyle = litGrad;
      ctx.fill();
    }
  }

  ctx.restore();

  /* 4. Subtle silver rim glow */
  const glowGrad = ctx.createRadialGradient(cx, cy, r - 1, cx, cy, r + 3);
  glowGrad.addColorStop(0, 'rgba(200,216,240,0.28)');
  glowGrad.addColorStop(1, 'rgba(200,216,240,0)');
  ctx.beginPath();
  ctx.arc(cx, cy, r + 2, 0, 2 * Math.PI);
  ctx.fillStyle = glowGrad;
  ctx.fill();
}

/* ================================================================
   SECTION 13: HELPER FUNCTIONS
================================================================ */

/** Returns today as "YYYY-MM-DD" in browser local time (for date-picker default). */
function getTodayString(): string {
  const now = new Date();
  return now.getFullYear() + '-'
    + String(now.getMonth() + 1).padStart(2, '0') + '-'
    + String(now.getDate()).padStart(2, '0');
}

/**
 * Formats a UTC Date as "HH:MM:SS" in the specified IANA timezone.
 * This ensures times are always shown in the target location's time, not the browser's.
 */
function formatTime(date: Date, tzId: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false, timeZone: tzId,
  }).format(date);
}

/**
 * Formats a Date as "DD/MonthName/YYYY" using UTC parts (avoids timezone day shift).
 */
function formatDate(date: Date): string {
  const MONTHS = ['January','February','March','April','May','June',
                  'July','August','September','October','November','December'];
  /* getUTCMonth() is 0 to 11 for a valid Date, so the element exists; for an invalid one it is
     NaN and the JavaScript printed "undefined" here, which this still does (string + undefined). */
  return String(date.getUTCDate()).padStart(2, '0') + '/'
    + MONTHS[date.getUTCMonth()] + '/' + date.getUTCFullYear();
}

/**
 * Returns the short timezone abbreviation (e.g. "CET") via the Intl API.
 * dateStr is "YYYY-MM-DD".
 */
function getTimezoneAbbreviation(ianaId: string, dateStr: string): string {
  try {
    const [y, mo, d] = dateStr.split('-').map(Number);
    /* y, mo and d are undefined when dateStr has fewer parts (an empty date field). The casts
       emit nothing, so the JavaScript is the 90d11dd code: Date.UTC then gives NaN, and
       formatToParts below throws the RangeError that the catch turns into "". */
    const date  = new Date(Date.UTC(y as number, (mo as number) - 1, d, 12, 0, 0));
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: ianaId, timeZoneName: 'short',
    }).formatToParts(date);
    const p = parts.find(x => x.type === 'timeZoneName');
    return p ? p.value : '';
  } catch { return ''; }
}

/** Escapes HTML special chars to prevent XSS in city name display. */
function escapeHtml(str: string): string {
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

/* ================================================================
   SECTION 14: UI STATE HELPERS
================================================================ */
/* An error also hides any earlier result, so it cannot be read as the answer to the new input. */
function showError(msg: string): void { els.errorMsg.textContent = msg; els.errorMsg.removeAttribute('hidden'); els.resultsCard.setAttribute('hidden', ''); }
function clearError(): void           { els.errorMsg.setAttribute('hidden',''); els.errorMsg.textContent = ''; }
function showLoading(on: boolean): void { els.loadingOverlay.hidden = !on; }

/* ================================================================
   SECTION 15: OFFLINE PILOT (ported 23 October 2026 from SunApp 88f59d4)
   Registers the service worker (ts/sw.ts, compiled to sw.js) that lets
   the page open with no network after one visit, for the Home Screen
   web app on iPhone. Only on https://moon.stormberry.as: never on the
   onion mirror (another host, plain http), on file://, on localhost or
   on any other host. The Android app never loads this page at all.
     'opt-in'  only a visit to https://moon.stormberry.as/?offline=1
               registers the worker; a page it already controls keeps
               it up to date. Nobody else is affected.
     'on'      every visit registers it.
     'off'     kill switch: every visit unregisters it and deletes its
               caches. Pages are network first, so this reaches every
               device on its next online visit.
   The page keeps no storage key of its own for this.
================================================================ */
// A cast, not an annotation, so the checker keeps all three values in play whichever is chosen.
const OFFLINE_PILOT = 'opt-in' as 'off' | 'opt-in' | 'on';

function offlinePilot(): void {
  if (!('serviceWorker' in navigator)) return;
  if (location.protocol !== 'https:' || location.hostname !== 'moon.stormberry.as') return;
  const sw = navigator.serviceWorker;
  if (OFFLINE_PILOT === 'off') {
    void sw.getRegistrations().then((rs) => Promise.all(rs.map((r) => r.unregister())))
      .then(() => caches.keys())
      .then((ks) => Promise.all(ks.filter((k) => k.startsWith('moonapp-')).map((k) => caches.delete(k))))
      .catch(() => undefined);
    return;
  }
  if (OFFLINE_PILOT === 'opt-in' && new URLSearchParams(location.search).get('offline') !== '1' && !sw.controller) return;
  void sw.register('sw.js', { scope: './', updateViaCache: 'none' }).catch(() => undefined);
}

/* ================================================================
   SECTION 16: BOOTSTRAP
================================================================ */
document.addEventListener('DOMContentLoaded', init);
