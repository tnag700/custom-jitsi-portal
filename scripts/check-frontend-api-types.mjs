import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const frontendDir = path.join(repoRoot, 'frontend-qwik');
const generatedTypesPath = path.join(frontendDir, 'src', 'lib', 'shared', 'api', 'generated', 'api-types.ts');

const before = readFileSync(generatedTypesPath, 'utf8');
const result = process.platform === 'win32'
  ? spawnSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', 'npm run generate:api'], {
      cwd: frontendDir,
      stdio: 'inherit',
    })
  : spawnSync('npm', ['run', 'generate:api'], {
      cwd: frontendDir,
      stdio: 'inherit',
    });

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

const after = readFileSync(generatedTypesPath, 'utf8');
if (before !== after) {
  console.error('Frontend API types drift detected. Run npm --prefix frontend-qwik run generate:api and commit api-types.ts.');
  process.exit(1);
}