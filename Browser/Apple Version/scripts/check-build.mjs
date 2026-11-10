// Build check for the browser version, which has no compiler: it catches the mistakes a compiler would.
//  1. Every file must be valid JavaScript (the same check as `node --check`).
//  2. Every name a file imports must really be exported by the file it comes from, and every imported file must exist
//     (a typo in an import only shows when the page is opened, and then it shows as a blank page).
//  3. index.html must point at files that exist (scripts, styles, icons).
// Run it with `npm run check`.
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const files = [...readdirSync(join(root, 'js')).map(f => `js/${f}`), ...readdirSync(join(root, 'test')).map(f => `test/${f}`)]
  .filter(f => f.endsWith('.js'));
const problems = [];

for (const file of files) {
  const check = spawnSync(process.execPath, ['--check', join(root, file)], { encoding: 'utf8' });
  if (check.status !== 0) {
    problems.push(`${file}: not valid JavaScript\n${check.stderr.split('\n').slice(0, 4).join('\n')}`);
  }
}

const exportsOf = new Map();
async function exportedNames(path) {
  if (!exportsOf.has(path)) {
    try {
      exportsOf.set(path, new Set(Object.keys(await import(pathToFileURL(path).href))));
    } catch (problem) {
      // A file that cannot even be loaded (a bad import inside it) is reported once, and its own imports are skipped.
      problems.push(`${relative(root, path)}: cannot be loaded: ${problem.message.split('\n')[0]}`);
      exportsOf.set(path, null);
    }
  }
  return exportsOf.get(path);
}

const IMPORT = /import\s+(?:(\w+)\s*,?\s*)?(?:\{([^}]*)\})?\s*(?:\*\s+as\s+\w+\s*)?from\s+'(\.[^']+)'/g;
for (const file of files) {
  const text = readFileSync(join(root, file), 'utf8');
  for (const [, , names, source] of text.matchAll(IMPORT)) {
    const target = join(dirname(join(root, file)), source);
    if (!existsSync(target)) {
      problems.push(`${file}: imports ${source}, which does not exist`);
      continue;
    }
    if (!names) {
      continue;
    }
    const available = await exportedNames(target);
    if (available === null) {
      continue;
    }
    for (const name of names.split(',').map(n => n.trim().split(/\s+as\s+/)[0]).filter(Boolean)) {
      if (!available.has(name)) {
        problems.push(`${file}: imports ${name} from ${source}, which does not export it`);
      }
    }
  }
}

const page = readFileSync(join(root, 'index.html'), 'utf8');
for (const [, , link] of page.matchAll(/(src|href)="([^"#]+)"/g)) {
  if (!/^https?:/.test(link) && !existsSync(join(root, link))) {
    problems.push(`index.html: points at ${link}, which does not exist`);
  }
}

if (problems.length === 0) {
  console.log(`Build check passed: ${files.length} files are valid JavaScript, every import resolves, and index.html's links exist.`);
} else {
  console.log(problems.join('\n'));
  console.log(`\nBuild check failed: ${problems.length} problem(s).`);
  process.exit(1);
}
