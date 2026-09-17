import { spawn } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = join(webRoot, '..', '..');

function loadEnvFile(path) {
  if (!existsSync(path)) {
    return;
  }
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }
    const eq = trimmed.indexOf('=');
    if (eq === -1) {
      continue;
    }
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!process.env[key]) {
      process.env[key] = value;
    }
  }
}

loadEnvFile(join(repoRoot, '.env'));
loadEnvFile(join(webRoot, '.env'));
loadEnvFile(join(process.cwd(), '.env'));

const license = process.env['PRIMENG_LICENSE'] ?? '';
if (!license) {
  console.warn('PRIMENG_LICENSE is empty; PrimeNG will run without a license key.');
}

writeFileSync(
  join(webRoot, 'src', 'primeng-license.ts'),
  `export const PRIMENG_LICENSE = ${JSON.stringify(license)};\n`,
);

const ngJs = join(webRoot, 'node_modules', '@angular', 'cli', 'bin', 'ng.js');
const child = spawn(process.execPath, [ngJs, ...process.argv.slice(2)], {
  stdio: 'inherit',
  cwd: webRoot,
});
child.on('exit', (code) => process.exit(code ?? 1));
child.on('error', (err) => {
  console.error(err);
  process.exit(1);
});
