// PostToolUse: formats a frontend file with Prettier right after Claude edits it, so the
// formatting check in CI never fails on Claude's changes. Fast (runs the local Prettier
// binary, no npx) and silent; any problem is ignored, a formatter must never block work.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const frontend = path.join(root, 'frontend');
const prettier = path.join(frontend, 'node_modules', 'prettier', 'bin', 'prettier.cjs');

try {
  const input = JSON.parse(readFileSync(0, 'utf8'));
  const file = input.tool_input?.file_path;
  if (file && existsSync(prettier)) {
    const absolute = path.resolve(input.cwd || root, file);
    const relative = path.relative(path.join(frontend, 'src'), absolute);
    const insideSrc = !relative.startsWith('..') && !path.isAbsolute(relative);
    if (insideSrc && /\.(ts|html|scss)$/.test(absolute) && existsSync(absolute)) {
      execFileSync(process.execPath, [prettier, '--write', '--log-level', 'warn', absolute], {
        cwd: frontend,
        stdio: 'ignore',
        timeout: 20_000,
      });
    }
  }
} catch {
  // Formatting is a convenience; never fail the edit because of it.
}
