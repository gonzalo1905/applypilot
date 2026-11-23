// Points the production build at the API: writes src/environments/environment.ts from the
// API_URL environment variable, for hosts (such as Vercel) where the API runs on another
// domain. Without API_URL the file is left alone, so the build expects the API at /api on
// the same origin.
//
//   API_URL=https://applypilot-api.onrender.com/api node scripts/set-api-url.mjs
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const apiUrl = process.env.API_URL?.trim();
if (!apiUrl) {
  console.log('API_URL is not set: the build will call the API at /api on the same origin.');
  process.exit(0);
}
let url;
try {
  url = new URL(apiUrl);
} catch {
  console.error(`API_URL is not a valid URL: ${apiUrl}`);
  process.exit(1);
}
if (url.protocol !== 'https:' && url.hostname !== 'localhost') {
  console.error('API_URL must use https (the token travels in every request).');
  process.exit(1);
}

const target = fileURLToPath(new URL('../src/environments/environment.ts', import.meta.url));
writeFileSync(
  target,
  `/** Production settings, written by scripts/set-api-url.mjs at build time. */
export const environment = {
  production: true,
  apiUrl: ${JSON.stringify(apiUrl.replace(/\/+$/, ''))},
};
`,
);
console.log(`The build will call the API at ${apiUrl}`);
