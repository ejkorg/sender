const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const url = process.argv[2] || 'http://127.0.0.1:4210';
  console.log(`Running axe-core accessibility scan against ${url}`);
  const browser = await chromium.launch({ args: ['--no-sandbox'] });
  const page = await browser.newPage();
  try {
    await page.goto(url, { waitUntil: 'networkidle' });

    // inject axe-core from installed package
    const axePath = require.resolve('axe-core');
    const axe = require('fs').readFileSync(axePath, 'utf8');
    // axe-core package exports UMD; the full file contains `axe` global
    await page.addScriptTag({ content: axe });

    // run axe
    const result = await page.evaluate(async () => {
      // eslint-disable-next-line no-undef
      return await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] } });
    });

    const outDir = path.join(__dirname, '..', 'accessibility');
    if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });
    const outFile = path.join(outDir, `axe-report-${Date.now()}.json`);
    fs.writeFileSync(outFile, JSON.stringify(result, null, 2));
    console.log(`Axe results saved to ${outFile}`);

    const violations = result.violations || [];
    console.log(`Found ${violations.length} violations.`);
    for (const v of violations) {
      console.log(`- ${v.id} (${v.impact}): ${v.help} — ${v.nodes.length} nodes`);
    }
  } catch (err) {
    console.error('Accessibility scan failed:', err);
    process.exitCode = 2;
  } finally {
    try { await browser.close(); } catch {}
  }
})();
