"use strict";
/*! MoonApp: app.js is compiled from ts/app.ts by tools/build-ts.sh, which leaves out every comment
    but this one: the explanations are in the source. Edit the .ts, never the .js: the build refuses
    to overwrite a hand edit, and its check mode fails on one. */
const state = {
    tab: 'city',
    city: null,
};
const els = {
    tabs: document.querySelectorAll('.tab'),
    panelCity: document.getElementById('panel-city'),
    panelGps: document.getElementById('panel-gps'),
    citySearch: document.getElementById('city-search'),
    cityDropdown: document.getElementById('city-dropdown'),
    citySelected: document.getElementById('city-selected'),
    citySelectedTxt: document.getElementById('city-selected-text'),
    cityClearBtn: document.getElementById('city-clear-btn'),
    latInput: document.getElementById('lat-input'),
    lonInput: document.getElementById('lon-input'),
    latError: document.getElementById('lat-error'),
    lonError: document.getElementById('lon-error'),
    gpsEcho: document.getElementById('gps-echo'),
    dateInput: document.getElementById('date-input'),
    calculateBtn: document.getElementById('calculate-btn'),
    errorMsg: document.getElementById('error-msg'),
    resultsCard: document.getElementById('results-card'),
    resCoords: document.getElementById('res-coords'),
    resDate: document.getElementById('res-date'),
    resTz: document.getElementById('res-tz'),
    resMoonrise: document.getElementById('res-moonrise'),
    resTransit: document.getElementById('res-transit'),
    resMoonset: document.getElementById('res-moonset'),
    phaseCanvas: document.getElementById('phase-canvas'),
    resPhaseName: document.getElementById('res-phase-name'),
    resIllumination: document.getElementById('res-illumination'),
    resDistance: document.getElementById('res-distance'),
    resNextNew: document.getElementById('res-next-new'),
    resNextFull: document.getElementById('res-next-full'),
    resIllumPct: document.getElementById('res-illum-pct'),
    illumBarFill: document.getElementById('illum-bar-fill'),
    loadingOverlay: document.getElementById('loading-overlay'),
};
function init() {
    els.dateInput.value = getTodayString();
    els.tabs.forEach(btn => btn.addEventListener('click', onTabClick));
    els.citySearch.addEventListener('input', onCityInput);
    els.citySearch.addEventListener('keydown', onCityKeydown);
    els.cityDropdown.addEventListener('click', onDropdownClick);
    els.cityClearBtn.addEventListener('click', clearCitySelection);
    els.latInput.addEventListener('input', () => { setFieldError(els.latInput, els.latError, null); setEcho(null); });
    els.lonInput.addEventListener('input', () => { setFieldError(els.lonInput, els.lonError, null); setEcho(null); });
    els.calculateBtn.addEventListener('click', onCalculate);
    document.addEventListener('click', e => {
        if (!els.citySearch.closest('.search-wrapper').contains(e.target))
            closeDropdown();
    });
}
function onTabClick(e) {
    const btn = e.currentTarget;
    const tab = btn.dataset.tab;
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
    const panel = document.getElementById('panel-' + tab);
    panel.hidden = false;
    panel.classList.add('active');
}
let highlightedIndex = -1;
function onCityInput() {
    const query = els.citySearch.value.trim().toLowerCase();
    if (state.city) {
        state.city = null;
        els.citySelected.hidden = true;
    }
    if (query.length < 1) {
        closeDropdown();
        return;
    }
    const qf = foldQuery(query);
    const startsWith = [], contains = [];
    for (const c of CITIES) {
        if (c.fold.startsWith(qf) || c.alt.startsWith(qf))
            startsWith.push(c);
        else if (c.fold.includes(qf) || c.alt.includes(qf) || c.cfold.includes(qf))
            contains.push(c);
    }
    const matches = startsWith.concat(contains).slice(0, 8);
    if (!matches.length) {
        closeDropdown();
        return;
    }
    renderDropdown(matches);
}
function renderDropdown(cities) {
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
function onCityKeydown(e) {
    if (els.cityDropdown.hidden)
        return;
    const items = els.cityDropdown.querySelectorAll('li');
    if (!items.length)
        return;
    if (e.key === 'ArrowDown') {
        e.preventDefault();
        highlightedIndex = Math.min(highlightedIndex + 1, items.length - 1);
        updateHighlight(items);
    }
    else if (e.key === 'ArrowUp') {
        e.preventDefault();
        highlightedIndex = Math.max(highlightedIndex - 1, 0);
        updateHighlight(items);
    }
    else if (e.key === 'Enter') {
        e.preventDefault();
        if (highlightedIndex >= 0)
            selectCity(items[highlightedIndex]._city);
    }
    else if (e.key === 'Escape') {
        closeDropdown();
    }
}
function updateHighlight(items) {
    items.forEach((li, i) => li.classList.toggle('highlighted', i === highlightedIndex));
}
function onDropdownClick(e) {
    const li = e.target.closest('li');
    if (li && li._city)
        selectCity(li._city);
}
function selectCity(city) {
    state.city = city;
    els.citySearch.value = '';
    els.citySelectedTxt.textContent = city.name + ', ' + city.country;
    els.citySelected.hidden = false;
    closeDropdown();
}
function clearCitySelection() {
    state.city = null;
    els.citySelected.hidden = true;
    els.citySearch.value = '';
    els.citySearch.focus();
}
function closeDropdown() {
    els.cityDropdown.hidden = true;
    els.cityDropdown.innerHTML = '';
    els.citySearch.setAttribute('aria-expanded', 'false');
    highlightedIndex = -1;
}
function parseDecimal(text, min, max) {
    if (text == null)
        return null;
    let s = String(text).trim().replace(/[\u2212\u2012\u2013\u2014\uFE63\uFF0D]/g, '-').replace(/\s+/g, '');
    if (/^[+-]?\d+,\d+$/.test(s))
        s = s.replace(',', '.');
    if (!/^[+-]?(\d+\.?\d*|\.\d+)$/.test(s))
        return null;
    const n = Number(s);
    return Number.isFinite(n) && n >= min && n <= max ? n : null;
}
function setFieldError(input, errorEl, msg) {
    if (msg) {
        errorEl.textContent = msg;
        errorEl.hidden = false;
        input.setAttribute('aria-invalid', 'true');
    }
    else {
        errorEl.textContent = '';
        errorEl.hidden = true;
        input.removeAttribute('aria-invalid');
    }
}
function setEcho(text) {
    els.gpsEcho.textContent = text || '';
    els.gpsEcho.hidden = !text;
}
function nearestCityTimezone(lat, lon) {
    let best = null, bestDist = Infinity;
    for (const c of CITIES) {
        let dLon = Math.abs(c.lon - lon);
        if (dLon > 180)
            dLon = 360 - dLon;
        const dLat = c.lat - lat;
        const x = dLon * Math.cos(((lat + c.lat) / 2) * Math.PI / 180);
        const dist = x * x + dLat * dLat;
        if (dist < bestDist) {
            bestDist = dist;
            best = c;
        }
    }
    return best ? best.tz : (Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
}
function resolveTimezone(lat, lon) {
    const ianaId = nearestCityTimezone(lat, lon);
    return { ianaId, abbreviation: getTimezoneAbbreviation(ianaId, els.dateInput.value) };
}
async function onCalculate() {
    clearError();
    let lat = NaN, lon = NaN;
    let tzInfo;
    if (state.tab === 'city') {
        if (!state.city) {
            showError('Please select a city from the search list first.');
            return;
        }
        lat = state.city.lat;
        lon = state.city.lon;
        tzInfo = { ianaId: state.city.tz, abbreviation: getTimezoneAbbreviation(state.city.tz, els.dateInput.value) };
    }
    else if (state.tab === 'gps') {
        const latVal = parseDecimal(els.latInput.value, -90, 90);
        const lonVal = parseDecimal(els.lonInput.value, -180, 180);
        setFieldError(els.latInput, els.latError, latVal === null ? 'Enter a latitude between -90 and 90, such as 60.39 or 60,39.' : null);
        setFieldError(els.lonInput, els.lonError, lonVal === null ? 'Enter a longitude between -180 and 180, such as 5.32 or 5,32.' : null);
        if (latVal === null || lonVal === null) {
            setEcho(null);
            els.resultsCard.setAttribute('hidden', '');
            (latVal === null ? els.latInput : els.lonInput).focus();
            return;
        }
        lat = latVal;
        lon = lonVal;
        setEcho('Using ' + lat + ', ' + lon);
        showLoading(true);
        try {
            tzInfo = await resolveTimezone(lat, lon);
        }
        catch (err) {
            showLoading(false);
            showError('Could not resolve timezone: ' + err.message);
            return;
        }
        showLoading(false);
    }
    function cityUtcOffsetMs(ianaId, at) {
        try {
            const dtf = new Intl.DateTimeFormat('en-US', {
                timeZone: ianaId, hour12: false,
                year: 'numeric', month: '2-digit', day: '2-digit',
                hour: '2-digit', minute: '2-digit', second: '2-digit',
            });
            const p = {};
            for (const { type, value } of dtf.formatToParts(at))
                p[type] = value;
            const asUtc = Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute, +p.second);
            return asUtc - Math.floor(at.getTime() / 1000) * 1000;
        }
        catch (e) {
            return 0;
        }
    }
    const [year, month, day] = els.dateInput.value.split('-').map(Number);
    if (!year || !month || !day) {
        showError('Please select a valid date.');
        return;
    }
    const dateForCalc = new Date(Date.UTC(year, month - 1, day, 12, 0, 0));
    const tz = tzInfo.ianaId;
    const tzOffsetMs = cityUtcOffsetMs(tz, dateForCalc);
    const cityMidnightUtc = new Date(Date.UTC(year, month - 1, day) - tzOffsetMs);
    const moonTimes = moonTimesFrom(cityMidnightUtc, lat, lon);
    const moonriseValid = moonTimes.rise instanceof Date && !isNaN(moonTimes.rise.getTime());
    const moonsetValid = moonTimes.set instanceof Date && !isNaN(moonTimes.set.getTime());
    let alwaysUp = false, alwaysDown = false;
    if (!moonriseValid && !moonsetValid) {
        let peak = -Infinity;
        for (let i = 0; i <= 96; i++) {
            const a = SunCalc.getMoonPosition(new Date(cityMidnightUtc.getTime() + i * 900000), lat, lon).altitude;
            if (a > peak)
                peak = a;
        }
        alwaysUp = peak > 0;
        alwaysDown = !alwaysUp;
    }
    const moonriseStr = moonriseValid ? formatTime(moonTimes.rise, tz) : null;
    const moonsetStr = moonsetValid ? formatTime(moonTimes.set, tz) : null;
    const transitStr = findLunarTransit(cityMidnightUtc, lat, lon, tz);
    const illum = SunCalc.getMoonIllumination(dateForCalc);
    const phaseName = getMoonPhaseName(illum.phase);
    const illumPct = Math.round(illum.fraction * 100);
    const pos = SunCalc.getMoonPosition(dateForCalc, lat, lon);
    const distanceKm = Math.round(pos.distance);
    const { nextNew, nextFull } = getNextLunations(dateForCalc);
    const city = state.city;
    const locationLabel = state.tab === 'city'
        ? city.name + ', ' + city.country
        : lat.toFixed(4) + '\u00b0, ' + lon.toFixed(4) + '\u00b0';
    renderResults({
        lat, lon, tz, tzAbbr: tzInfo.abbreviation,
        dateStr: formatDate(dateForCalc), locationLabel,
        moonriseStr, moonsetStr, transitStr, alwaysUp, alwaysDown,
        phaseName, illumPct,
        phaseAngle: illum.angle, phaseFraction: illum.fraction, phaseRaw: illum.phase,
        distanceKm, nextNew, nextFull,
    });
}
function renderResults({ lat, lon, tz, tzAbbr, dateStr, moonriseStr, moonsetStr, transitStr, alwaysUp, alwaysDown, phaseName, illumPct, phaseAngle, phaseRaw, distanceKm, nextNew, nextFull, }) {
    els.resCoords.textContent = lat.toFixed(4) + '\u00b0, ' + lon.toFixed(4) + '\u00b0';
    els.resDate.textContent = dateStr;
    els.resTz.textContent = tzAbbr ? tzAbbr + ' / ' + tz : tz;
    if (alwaysUp) {
        els.resMoonrise.innerHTML = '<span class="moon-time-value polar-always-up">\uD83C\uDF15 Always Up</span>';
        els.resMoonrise.className = '';
    }
    else if (alwaysDown) {
        els.resMoonrise.innerHTML = '<span class="moon-time-value polar-always-down">\uD83C\uDF11 Always Down</span>';
        els.resMoonrise.className = '';
    }
    else {
        els.resMoonrise.textContent = moonriseStr || '\u2014';
        els.resMoonrise.className = 'moon-time-value mono';
    }
    els.resTransit.textContent = transitStr || '\u2014';
    els.resTransit.className = 'moon-time-value mono';
    if (alwaysUp) {
        els.resMoonset.innerHTML = '<span class="moon-time-value polar-always-up">\uD83C\uDF15 Always Up</span>';
        els.resMoonset.className = '';
    }
    else if (alwaysDown) {
        els.resMoonset.innerHTML = '<span class="moon-time-value polar-always-down">\uD83C\uDF11 Always Down</span>';
        els.resMoonset.className = '';
    }
    else {
        els.resMoonset.textContent = moonsetStr || '\u2014';
        els.resMoonset.className = 'moon-time-value mono';
    }
    els.resPhaseName.textContent = phaseName;
    els.resIllumination.textContent = illumPct + '% illuminated';
    drawPhaseDisc(els.phaseCanvas, phaseRaw, phaseAngle);
    els.resDistance.textContent = distanceKm.toLocaleString('en-GB');
    els.resNextNew.textContent = nextNew ? formatDate(nextNew) : 'Not found';
    els.resNextFull.textContent = nextFull ? formatDate(nextFull) : 'Not found';
    els.resIllumPct.textContent = illumPct + '%';
    requestAnimationFrame(() => { els.illumBarFill.style.width = illumPct + '%'; });
    els.resultsCard.removeAttribute('hidden');
    setTimeout(() => els.resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100);
}
function getMoonPhaseName(phase) {
    if (phase < 0.0625 || phase >= 0.9375)
        return 'New Moon';
    if (phase < 0.1875)
        return 'Waxing Crescent';
    if (phase < 0.3125)
        return 'First Quarter';
    if (phase < 0.4375)
        return 'Waxing Gibbous';
    if (phase < 0.5625)
        return 'Full Moon';
    if (phase < 0.6875)
        return 'Waning Gibbous';
    if (phase < 0.8125)
        return 'Last Quarter';
    return 'Waning Crescent';
}
function getNextLunations(fromDate) {
    let nextNew = null;
    let nextFull = null;
    let prevPhase = SunCalc.getMoonIllumination(fromDate).phase;
    for (let d = 1; d <= 35; d++) {
        const candidate = new Date(fromDate.getTime() + d * 86400000);
        const currPhase = SunCalc.getMoonIllumination(candidate).phase;
        if (!nextNew && prevPhase > 0.75 && currPhase < 0.25)
            nextNew = candidate;
        if (!nextFull && prevPhase < 0.5 && currPhase >= 0.5)
            nextFull = candidate;
        if (nextNew && nextFull)
            break;
        prevPhase = currPhase;
    }
    return { nextNew, nextFull };
}
function moonTimesFrom(start, lat, lon) {
    const hoursLater = (d, h) => new Date(d.valueOf() + h * 3600000);
    const H0 = 0.133 * Math.PI / 180;
    const alt = (h) => SunCalc.getMoonPosition(hoursLater(start, h), lat, lon).altitude - H0;
    let rise;
    let set;
    let h0 = alt(0), ye;
    for (let i = 1; i <= 24; i += 2) {
        const h1 = alt(i), h2 = alt(i + 1);
        const a = (h0 + h2) / 2 - h1;
        const b = (h2 - h0) / 2;
        const xe = -b / (2 * a);
        ye = (a * xe + b) * xe + h1;
        const d = b * b - 4 * a * h1;
        let roots = 0, x1 = 0, x2 = 0;
        if (d >= 0) {
            const dx = Math.sqrt(d) / (Math.abs(a) * 2);
            x1 = xe - dx;
            x2 = xe + dx;
            if (Math.abs(x1) <= 1)
                roots++;
            if (Math.abs(x2) <= 1)
                roots++;
            if (x1 < -1)
                x1 = x2;
        }
        if (roots === 1) {
            if (h0 < 0)
                rise = i + x1;
            else
                set = i + x1;
        }
        else if (roots === 2) {
            rise = i + (ye < 0 ? x2 : x1);
            set = i + (ye < 0 ? x1 : x2);
        }
        if (rise && set)
            break;
        h0 = h2;
    }
    const out = {};
    if (rise)
        out.rise = hoursLater(start, rise);
    if (set)
        out.set = hoursLater(start, set);
    return out;
}
function findLunarTransit(dayStart, lat, lon, tz) {
    let peakTime = null;
    let peakAlt = -Infinity;
    const STEP_MS = 15 * 60 * 1000;
    for (let i = 0; i <= 96; i++) {
        const t = new Date(dayStart.getTime() + i * STEP_MS);
        const pos = SunCalc.getMoonPosition(t, lat, lon);
        if (pos.altitude > peakAlt) {
            peakAlt = pos.altitude;
            peakTime = t;
        }
    }
    if (!peakTime || peakAlt < 0)
        return null;
    return formatTime(peakTime, tz);
}
function drawPhaseDisc(canvas, phaseRaw, _phaseAngle) {
    const ctx = canvas.getContext('2d');
    const W = canvas.width, H = canvas.height;
    const cx = W / 2, cy = H / 2;
    const r = W / 2 - 2;
    ctx.clearRect(0, 0, W, H);
    const litGrad = ctx.createRadialGradient(cx - r * 0.2, cy - r * 0.25, 0, cx, cy, r);
    litGrad.addColorStop(0, '#f4f6ff');
    litGrad.addColorStop(0.55, '#c8d8f0');
    litGrad.addColorStop(1, '#7b9fd4');
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, 2 * Math.PI);
    ctx.fillStyle = litGrad;
    ctx.fill();
    const shadowFraction = phaseRaw <= 0.5
        ? 1 - phaseRaw * 2
        : (phaseRaw - 0.5) * 2;
    const shadowXRadius = r * shadowFraction;
    const shadowOnRight = phaseRaw < 0.5;
    const shadowGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    shadowGrad.addColorStop(0, 'rgba(8,10,22,0.93)');
    shadowGrad.addColorStop(0.7, 'rgba(6,8,18,0.97)');
    shadowGrad.addColorStop(1, 'rgba(4,6,14,0.99)');
    ctx.save();
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, 2 * Math.PI);
    ctx.clip();
    if (shadowOnRight) {
        ctx.beginPath();
        ctx.moveTo(cx, cy - r);
        ctx.arc(cx, cy, r, -Math.PI / 2, Math.PI / 2);
        ctx.closePath();
        ctx.fillStyle = shadowGrad;
        ctx.fill();
        if (shadowXRadius > 1) {
            ctx.beginPath();
            ctx.ellipse(cx, cy, shadowXRadius, r, 0, -Math.PI / 2, Math.PI / 2);
            ctx.closePath();
            ctx.fillStyle = litGrad;
            ctx.fill();
        }
    }
    else {
        ctx.beginPath();
        ctx.moveTo(cx, cy - r);
        ctx.arc(cx, cy, r, Math.PI / 2, Math.PI * 1.5);
        ctx.closePath();
        ctx.fillStyle = shadowGrad;
        ctx.fill();
        if (shadowXRadius > 1) {
            ctx.beginPath();
            ctx.ellipse(cx, cy, shadowXRadius, r, 0, Math.PI / 2, Math.PI * 1.5);
            ctx.closePath();
            ctx.fillStyle = litGrad;
            ctx.fill();
        }
    }
    ctx.restore();
    const glowGrad = ctx.createRadialGradient(cx, cy, r - 1, cx, cy, r + 3);
    glowGrad.addColorStop(0, 'rgba(200,216,240,0.28)');
    glowGrad.addColorStop(1, 'rgba(200,216,240,0)');
    ctx.beginPath();
    ctx.arc(cx, cy, r + 2, 0, 2 * Math.PI);
    ctx.fillStyle = glowGrad;
    ctx.fill();
}
function getTodayString() {
    const now = new Date();
    return now.getFullYear() + '-'
        + String(now.getMonth() + 1).padStart(2, '0') + '-'
        + String(now.getDate()).padStart(2, '0');
}
function formatTime(date, tzId) {
    return new Intl.DateTimeFormat('en-GB', {
        hour: '2-digit', minute: '2-digit', second: '2-digit',
        hour12: false, timeZone: tzId,
    }).format(date);
}
function formatDate(date) {
    const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
        'July', 'August', 'September', 'October', 'November', 'December'];
    return String(date.getUTCDate()).padStart(2, '0') + '/'
        + MONTHS[date.getUTCMonth()] + '/' + date.getUTCFullYear();
}
function getTimezoneAbbreviation(ianaId, dateStr) {
    try {
        const [y, mo, d] = dateStr.split('-').map(Number);
        const date = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
        const parts = new Intl.DateTimeFormat('en-US', {
            timeZone: ianaId, timeZoneName: 'short',
        }).formatToParts(date);
        const p = parts.find(x => x.type === 'timeZoneName');
        return p ? p.value : '';
    }
    catch {
        return '';
    }
}
function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function showError(msg) { els.errorMsg.textContent = msg; els.errorMsg.removeAttribute('hidden'); els.resultsCard.setAttribute('hidden', ''); }
function clearError() { els.errorMsg.setAttribute('hidden', ''); els.errorMsg.textContent = ''; }
function showLoading(on) { els.loadingOverlay.hidden = !on; }
document.addEventListener('DOMContentLoaded', init);
