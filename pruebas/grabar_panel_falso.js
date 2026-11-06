const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const rec = fs.readFileSync(process.argv[2], 'utf8');
  const b = await chromium.launch(); const page = await b.newPage();
  await page.addInitScript(() => { window.__ev = []; window.WtcRec = { record: s => window.__ev.push(s) }; });
  page.on('load', () => {});
  await page.goto('http://127.0.0.1:8790/index.html');
  await page.evaluate(rec);            // como onPageFinished en Android
  await page.fill('#pwd', 'Secreta#2026');
  await page.dispatchEvent('#pwd', 'change');
  await page.click('#entrar');
  await page.click('#bloquear');
  await page.waitForTimeout(2500);
  let all = await page.evaluate(() => window.__ev);
  for (const f of page.frames()) if (f !== page.mainFrame()) all = all.concat(await f.evaluate(() => window.__ev || []));
  fs.writeFileSync(process.argv[3], all.join('\n'));
  console.log('eventos:', all.length, [...new Set(all.map(e => JSON.parse(e).type))].join(','));
  await b.close();
})();
