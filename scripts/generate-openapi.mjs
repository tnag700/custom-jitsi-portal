import { copyFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '..');
const backendDir = path.join(repoRoot, 'backend');
const generatedFile = path.join(backendDir, 'build', 'openapi', 'openapi-generated.json');
const committedBaseline = path.join(repoRoot, 'openapi.generated.json');

const result = process.platform === 'win32'
  ? spawnSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', 'gradlew.bat --no-daemon generateOpenApiSpec'], {
      cwd: backendDir,
      stdio: 'inherit',
    })
  : spawnSync('./gradlew', ['--no-daemon', 'generateOpenApiSpec'], {
      cwd: backendDir,
      stdio: 'inherit',
    });

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

mkdirSync(path.dirname(committedBaseline), { recursive: true });
copyFileSync(generatedFile, committedBaseline);