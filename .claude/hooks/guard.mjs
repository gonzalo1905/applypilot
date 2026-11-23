// PreToolUse guard for Claude Code in this repository.
//
// Blocks, before the tool runs:
//   - reading or editing .env files (real secrets live there; .env.example is fine)
//   - shell commands that print or copy a .env file
//   - git commits whose message carries AI attribution (Co-Authored-By an AI, "Generated with")
//   - git pushes while a commit to be pushed carries such a line
//   - force pushes
//
// Protocol: Claude Code sends the tool call as JSON on stdin. Exit code 2 blocks the call and
// sends the text on stderr back to Claude as the reason; exit code 0 lets it run.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const ATTRIBUTION = [
  /^\s*co-authored-by:.*(claude|anthropic|copilot|chatgpt|openai|\bai\b)/im,
  /generated with/i,
  /claude\.ai\/code/i,
  /noreply@anthropic\.com/i,
  /\u{1F916}/u,
];

/** A .env file, but not the committed template. */
function isEnvFile(filePath) {
  if (!filePath) return false;
  const name = path.basename(String(filePath)).toLowerCase();
  return (name === '.env' || name.startsWith('.env.')) && name !== '.env.example';
}

/** A shell command that names a .env file (other than .env.example). */
function commandTouchesEnv(command) {
  return /(^|[\s"'=/\\])\.env(?!\.example)(\.[\w-]+)?(?=$|[\s"';|&)>])/i.test(command);
}

function hasAttribution(text) {
  return ATTRIBUTION.some((pattern) => pattern.test(text));
}

function block(reason) {
  process.stderr.write(`Blocked by .claude/hooks/guard.mjs: ${reason}\n`);
  process.exit(2);
}

function checkPush(command, cwd) {
  if (/\s(--force|-f|--force-with-lease)(\s|=|$)/.test(command) || /\s\+\S+/.test(command)) {
    block('force pushes are not allowed in this repository.');
  }
  let messages = '';
  try {
    // Commits that the push would send: those not yet on any remote branch.
    messages = execFileSync('git', ['log', '--format=%B', 'HEAD', '--not', '--remotes'], {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    });
  } catch {
    return; // Not a git repository or no remote yet: nothing to check.
  }
  if (hasAttribution(messages)) {
    block('a commit to be pushed has an AI attribution line. Reword it (git commit --amend or rebase) first.');
  }
}

function main() {
  let input;
  try {
    input = JSON.parse(readFileSync(0, 'utf8'));
  } catch {
    return; // Not a tool call we understand: never block on our own bug.
  }
  const tool = input.tool_name ?? '';
  const args = input.tool_input ?? {};

  if (['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(tool)) {
    if (isEnvFile(args.file_path ?? args.notebook_path)) {
      block('.env files hold real secrets and are off limits. Use .env.example for documentation.');
    }
    return;
  }
  if (tool === 'Grep' && isEnvFile(args.path)) {
    block('.env files hold real secrets and are off limits.');
  }
  if (tool === 'Bash' || tool === 'PowerShell') {
    const command = String(args.command ?? '');
    if (commandTouchesEnv(command)) {
      block('this command names a .env file, which holds real secrets.');
    }
    if (/\bgit\b[^\n|;&]*\bcommit\b/.test(command) && hasAttribution(command)) {
      block('commit messages in this repository carry no AI attribution (no Co-Authored-By, no "Generated with").');
    }
    if (/\bgit\b[^\n|;&]*\bpush\b/.test(command)) {
      checkPush(command, input.cwd || process.cwd());
    }
  }
}

main();
