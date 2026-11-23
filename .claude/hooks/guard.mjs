// PreToolUse guard for Claude Code in this repository.
//
// Blocks, before the tool runs:
//   - reading or editing .env files (real secrets live there; .env.example is fine)
//   - shell commands that name a .env file, globs included (`cat .env*`)
//   - git commits whose message carries AI attribution (Co-Authored-By an AI, "Generated with Claude")
//   - git pushes while a commit to be pushed carries such a line
//   - force pushes
//
// Protocol: Claude Code sends the tool call as JSON on stdin. Exit code 2 blocks the call and
// sends the text on stderr back to Claude as the reason; exit code 0 lets it run.
//
// It fails open: if the input cannot be read or parsed, the call is allowed, so a bug here
// never stops all work. It reads command text, so it is a seatbelt against mistakes, not a
// sandbox against someone determined to get around it (a commit message read from a file
// with `git commit -F` is only caught later, by the push check).
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const ATTRIBUTION = [
  /co-authored-by:[^\n]*(claude|anthropic|copilot|chatgpt|openai|\bai\b)/i,
  /generated (with|by)[^\n]*(claude|copilot|chatgpt|\bai\b)/i,
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

/** A shell command that names a .env file, or a glob that could match one. */
function commandTouchesEnv(command) {
  const withoutTemplate = command.replace(/\.env\.example\b/gi, '');
  return /(^|[\s"'=/\\])\.en[v?*[]/i.test(withoutTemplate);
}

function hasAttribution(text) {
  return ATTRIBUTION.some((pattern) => pattern.test(text));
}

function block(reason) {
  process.stderr.write(`Blocked by .claude/hooks/guard.mjs: ${reason}\n`);
  process.exit(2);
}

function isForcePush(command) {
  return (
    /\s(--force|--force-with-lease|--force-if-includes)(\s|=|$)/.test(command) ||
    /\s-[a-zA-Z]*f[a-zA-Z]*(\s|$)/.test(command) || // -f, -fu, -uf
    /\s\+\S+/.test(command) // +refspec
  );
}

function checkPush(command, cwd) {
  if (isForcePush(command)) {
    block('force pushes are not allowed in this repository.');
  }
  let messages = '';
  try {
    // Commits that the push would send: those not yet on any remote branch.
    messages = execFileSync('git', ['log', '--format=%B', 'HEAD', '--not', '--remotes'], {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
      timeout: 10_000,
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
    return;
  }
  const tool = input.tool_name ?? '';
  const args = input.tool_input ?? {};

  if (['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(tool)) {
    if (isEnvFile(args.file_path ?? args.notebook_path)) {
      block('.env files hold real secrets and are off limits. Use .env.example for documentation.');
    }
    return;
  }
  if (tool === 'Grep') {
    if (isEnvFile(args.path) || commandTouchesEnv(` ${args.glob ?? ''}`)) {
      block('.env files hold real secrets and are off limits.');
    }
    return;
  }
  if (tool === 'Bash' || tool === 'PowerShell') {
    const command = String(args.command ?? '');
    if (commandTouchesEnv(command)) {
      block('this command names a .env file, which holds real secrets.');
    }
    if (/\bgit\b[^\n|;&]*\bcommit\b/.test(command) && hasAttribution(command)) {
      block('commit messages in this repository carry no AI attribution (no Co-Authored-By, no "Generated with").');
    }
    if (/\bgit\b[^\n|;&]*\bpush\b/.test(command) && !/\bstash\s+push\b/.test(command)) {
      checkPush(command, input.cwd || process.cwd());
    }
  }
}

main();
