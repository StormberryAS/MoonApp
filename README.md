# MoonApp 🌕

> Sovereign, privacy-first lunar calculator — a sibling to [SunApp](https://sun.stormberry.as).

**Live:** [moon.stormberry.as](https://moon.stormberry.as)

## Features

- 🌛 **Moonrise / Lunar Transit / Moonset** — times in the correct local timezone
- 🌕 **Phase disc** — rendered canvas graphic showing the exact illuminated face
- 📏 **Moon Distance** — kilometres from Earth at the selected date
- 🌑🌕 **Next New & Full Moon** — upcoming lunation dates calculated client-side
- 🔍 **City Search** — offline autocomplete for 2,000+ cities worldwide
- 📡 **Device Geolocation** — one-click GPS coordinates from your device
- 🗺️ **Manual GPS Input** — any arbitrary latitude/longitude on Earth
- 📅 **Time-Travel** — calculate lunar data for any past or future date
- 🌑 **Polar Edge Cases** — "Always Up" / "Always Down" for High Arctic locations

## Design

- Dark-mode, glassmorphism-inspired UI
- Lunar silver/indigo colour palette
- Animated moon disc in the header
- Fluid micro-animations throughout
- Fully responsive (mobile & desktop)

## Privacy

No cookies. No tracking. All calculations run in your browser.  
The only external call is to [Open-Meteo](https://open-meteo.com) to resolve the  
timezone when you enter raw GPS coordinates (cities use a bundled database).

## Stack

| Dependency | Purpose |
|---|---|
| [SunCalc 1.8.0](https://github.com/mourner/suncalc) | Astronomical moon calculations (local copy) |
| [Intl API](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl) | Timezone-aware time formatting (built-in) |
| [Open-Meteo](https://open-meteo.com) | Timezone resolution for raw coordinates only |
| [Inter](https://rsms.me/inter/) | Typography (locally hosted) |

## Credits

Built by [Stormberry A.S.](https://stormberry.as) · Proudly powered by sovereign AI agents.
