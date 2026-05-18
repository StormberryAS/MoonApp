# MoonApp

Sovereign, privacy-first lunar calculator. A sibling to [SunApp](https://sun.stormberry.as), covering moonrise, moon phase, lunar distance, upcoming lunations and global city lookups.

**Live:** [moon.stormberry.as](https://moon.stormberry.as)

## Features
- **Moonrise, lunar transit, moonset**: times in the correct local timezone.
- **Phase disc**: rendered canvas graphic showing the exact illuminated face.
- **Moon distance**: kilometres from Earth at the selected date.
- **Next new and full moon**: upcoming lunation dates calculated client-side.
- **City search**: offline autocomplete for 2,000+ cities worldwide.
- **Device geolocation**: one-click GPS coordinates from your device.
- **Manual GPS input**: any arbitrary latitude or longitude on Earth.
- **Time travel**: calculate lunar data for any past or future date.
- **Polar edge cases**: "Always Up" or "Always Down" handling for high-Arctic locations.

## Architecture
- **Vanilla HTML/CSS/JS**, no frameworks, no build step.
- **Privacy first**, no cookies, no tracking. Only one anonymous external call (Open-Meteo) to resolve raw GPS coordinates to an IANA timezone. City lookups use a bundled database.
- Dark-mode glassmorphism Stormberry design system, lunar silver and indigo palette, animated moon disc in the header, fully responsive on mobile and desktop.
- **Sovereign AI**, built and maintained using high-speed agentic workflows.

## Stack
| Dependency | Purpose |
|---|---|
| [SunCalc 1.8.0](https://github.com/mourner/suncalc) | Astronomical moon calculations, bundled locally |
| Browser [`Intl` API](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl) | Timezone-aware time formatting, built into the browser |
| [Open-Meteo](https://open-meteo.com) | Timezone resolution for raw GPS coordinates only |
| [Inter](https://rsms.me/inter/) | Typography, locally hosted |

## Credits
Built by [Stormberry AS](https://stormberry.as). Proudly powered by sovereign AI agents.
