const fs = await import('node:fs');
const src = fs.readFileSync(process.env.HOME + '/ThomassenPovoaHoldingAS/StormberryAS/GitHub/MoonApp/suncalc.js','utf8');
global.window = {}; eval(src); const SunCalc = global.window.SunCalc;
const rows = ['# Golden vectors for MoonCalc.illumination, generated from SunCalc 1.8.0 (suncalc.js),',
  '# the exact library the web app ships. Regenerate with tools/gen-golden.mjs.',
  '# iso_utc,fraction,phase'];
// spread across a full year, all hours, including the times of day the old bug broke
const stamps = [];
for (let m = 0; m < 12; m++)
  for (const [d, h] of [[3,0],[9,6],[16,12],[23,18],[27,3]])
    stamps.push(new Date(Date.UTC(2026, m, d, h, 17, 0)));
for (const dt of stamps) {
  const i = SunCalc.getMoonIllumination(dt);
  rows.push(`${dt.toISOString().replace('.000','')},${i.fraction.toFixed(9)},${i.phase.toFixed(9)}`);
}
fs.writeFileSync(process.env.HOME + '/ThomassenPovoaHoldingAS/StormberryAS/GitHub/MoonApp/android/app/src/test/resources/mooncalc-golden.csv', rows.join('\n') + '\n');
console.log('  golden vectors written:', stamps.length);
