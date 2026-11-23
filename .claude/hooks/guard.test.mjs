// Checks the guard hook against known commands: node .claude/hooks/guard.test.mjs
// File names are built from pieces so this file does not trip the guard itself.
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const guard = path.join(path.dirname(fileURLToPath(import.meta.url)), 'guard.mjs');
const E = '.e' + 'nv';
const BS = String.fromCharCode(92);
const cases = [
  [true, { tool_name: 'Read', tool_input: { file_path: `C:${BS}x${BS}${E}` } }],
  [false, { tool_name: 'Read', tool_input: { file_path: `C:${BS}x${BS}${E}.example` } }],
  [true, { tool_name: 'Bash', tool_input: { command: `cat ${E}*` } }],
  [true, { tool_name: 'Bash', tool_input: { command: 'cat .en?' } }],
  [true, { tool_name: 'PowerShell', tool_input: { command: `Get-Content ..${BS}${E}` } }],
  [false, { tool_name: 'Bash', tool_input: { command: `cp ${E}.example x` } }],
  [false, { tool_name: 'Bash', tool_input: { command: 'ls environment.ts' } }],
  [true, { tool_name: 'Grep', tool_input: { pattern: 'KEY', glob: `${E}*` } }],
  [true, { tool_name: 'Bash', tool_input: { command: 'git commit -m "x" -m "Co-Authored-By: Claude <a@b.c>"' } }],
  [false, { tool_name: 'Bash', tool_input: { command: 'git commit -m "docs: client generated with openapi-generator"' } }],
  [false, { tool_name: 'Bash', tool_input: { command: 'git commit -m "docs: explain Claude Code hooks"' } }],
  [true, { tool_name: 'Bash', tool_input: { command: 'git push -fu origin main' } }],
  [true, { tool_name: 'Bash', tool_input: { command: 'git push origin +main' } }],
  [false, { tool_name: 'Bash', tool_input: { command: 'git stash push -m wip' } }],
  [false, { tool_name: 'Bash', tool_input: { command: 'git push origin main' } }],
];
let failures = 0;
for (const [shouldBlock, payload] of cases) {
  const result = spawnSync(process.execPath, [guard], { input: JSON.stringify(payload) });
  const blocked = result.status === 2;
  const ok = blocked === shouldBlock;
  if (!ok) failures++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${shouldBlock ? 'block' : 'allow'} ${JSON.stringify(payload.tool_input)}`);
}
console.log(failures === 0 ? 'all guard cases pass' : `${failures} guard cases failed`);
process.exit(failures === 0 ? 0 : 1);
