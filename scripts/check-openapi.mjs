import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const baselinePath = path.join(repoRoot, 'openapi.generated.json');
const nodeCommand = process.execPath;
const generateScript = path.join(scriptDir, 'generate-openapi.mjs');

const before = existsSync(baselinePath) ? readFileSync(baselinePath, 'utf8') : null;
const result = spawnSync(nodeCommand, [generateScript], { cwd: repoRoot, stdio: 'inherit' });

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

const after = readFileSync(baselinePath, 'utf8');
if (before !== after) {
  console.error('OpenAPI contract drift detected. Run npm run openapi:generate and commit openapi.generated.json.');
  process.exit(1);
}