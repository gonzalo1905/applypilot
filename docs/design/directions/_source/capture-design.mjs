import { chromium } from 'playwright-core';
import { pathToFileURL } from 'node:url';

const dir = process.argv[2];
const dirs = [
  ['flight-deck', 'dark', 'light'],
  ['editorial', 'light', 'dark'],
  ['aurora', 'dark', 'light'],
];
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
for (const [slug, main, alt] of dirs) {
  await page.goto(pathToFileURL(`${dir}/${slug}/index.html`).href);
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => document.fonts.ready);
  for (const theme of [main, alt]) {
    await page.evaluate((t) => (document.documentElement.dataset.theme = t), theme);
    await page.waitForTimeout(150);
    const sections = theme === main ? ['login', 'app', 'match'] : ['app'];
    for (const s of sections) {
      await page.locator(`.screen-${s}`).screenshot({ path: `${dir}/${slug}-${s}-${theme}.png` });
    }
  }
}
// The marks at real favicon sizes, side by side.
await page.setContent(`<body style="margin:0;padding:24px;background:#fff;font-family:sans-serif;display:flex;gap:40px">
${dirs.map(([s]) => `<div style="text-align:center"><div style="display:flex;gap:12px;align-items:center;justify-content:center">
<img src="${pathToFileURL(`${dir}/${s}/favicon.svg`).href}" width="16" height="16">
<img src="${pathToFileURL(`${dir}/${s}/favicon.svg`).href}" width="32" height="32">
<img src="${pathToFileURL(`${dir}/${s}/favicon.svg`).href}" width="64" height="64"></div><p style="font-size:12px">${s}</p></div>`).join('')}</body>`);
await page.waitForTimeout(300);
await page.screenshot({ path: `${dir}/favicons.png`, clip: { x: 0, y: 0, width: 760, height: 140 } });
await browser.close();
console.log('captured');
