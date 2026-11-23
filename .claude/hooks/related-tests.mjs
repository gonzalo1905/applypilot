// PostToolUse: after Claude edits a source file, tells it which fast test covers that file
// and the exact command to run it. It does not run the tests itself: a Maven run takes tens
// of seconds, and running one after every edit would make each edit that slow. Claude gets
// the hint as additional context and decides when to run it (usually once per change).
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function hint(file) {
  const normalized = file.split(path.sep).join('/');

  // backend/src/main/java/.../Foo.java -> FooTest (unit) and FooIT / *FlowIT (integration)
  const main = normalized.match(/backend\/src\/main\/java\/(.+)\/(\w+)\.java$/);
  if (main) {
    const [, pkg, name] = main;
    const unit = path.join(root, 'backend', 'src', 'test', 'java', pkg, `${name}Test.java`);
    if (existsSync(unit)) {
      return `Related unit test: ${name}Test. Run it from backend/ with JDK 21: ./mvnw -q test -Dtest=${name}Test`;
    }
    // Records, DTOs, entities and exceptions are covered through the classes that use them;
    // only classes with behaviour get the "no test" hint, so it is not noise.
    if (/(Service|Agent|Controller|Orchestrator|Scoring|Matcher|Tools|Recorder|Limiter|Extractor|Filter)$/.test(name)) {
      return `No ${name}Test exists for ${name}.java. Consider adding one (the test-writer subagent can draft it).`;
    }
    return null;
  }
  const test = normalized.match(/backend\/src\/test\/java\/.+\/(\w+(Test|IT))\.java$/);
  if (test) {
    const [, name, kind] = test;
    return kind === 'IT'
      ? `Run this integration test from backend/ (Docker needed): ./mvnw -q verify -Dit.test=${name} -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`
      : `Run this test from backend/: ./mvnw -q test -Dtest=${name}`;
  }

  // frontend/src/app/.../foo.ts -> foo.spec.ts
  const front = normalized.match(/frontend\/(src\/app\/.+?)(\.spec)?\.(ts|html|scss)$/);
  if (front) {
    const spec = `${front[1]}.spec.ts`;
    if (existsSync(path.join(root, 'frontend', spec))) {
      return `Related spec: ${spec}. Run it from frontend/: npx ng test --watch=false --include=${spec}`;
    }
  }
  return null;
}

try {
  const input = JSON.parse(readFileSync(0, 'utf8'));
  const file = input.tool_input?.file_path;
  const text = file ? hint(path.resolve(input.cwd || root, file)) : null;
  if (text) {
    process.stdout.write(
      JSON.stringify({ hookSpecificOutput: { hookEventName: 'PostToolUse', additionalContext: text } }),
    );
  }
} catch {
  // A hint is optional; never fail the edit because of it.
}
