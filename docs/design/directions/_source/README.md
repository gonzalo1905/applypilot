# How these mockups are made

- `gen.py` writes the three `*/index.html` pages from one shared markup, so the directions
  differ only in tokens, fonts, logo and personality (`base.css` + one CSS file each).
- `capture-design.mjs` takes the screenshots with Playwright (deviceScaleFactor 2).

```bash
python gen.py ../           # from this folder
node capture-design.mjs <absolute path to docs/design/directions>   # needs playwright-core and Edge
```

The mockups load fonts from Google Fonts. The real app will self-host the chosen pair with
Fontsource.
