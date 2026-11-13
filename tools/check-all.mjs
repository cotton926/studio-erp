// 一把梭：串行跑全部自检，任一失败即整体退出非 0，可直接挂到 CI 或 git pre-push。
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

const tasks = [
  ['SQL 结构与语法', 'check-sql.mjs'],
  ['Java 源码语法', 'check-java.mjs'],
  ['业绩分摊算法', 'check-allocator.mjs'],
  ['原型页面', 'check-html.mjs'],
];

let failed = 0;
for (const [label, script] of tasks) {
  const r = spawnSync(process.execPath, [path.join(here, script)], { encoding: 'utf8' });
  const out = (r.stdout || '').trim();
  if (out) console.log(out);
  if (r.status !== 0) {
    failed++;
    if (r.stderr) console.log(r.stderr.trim());
  }
  console.log('');
}

console.log('='.repeat(74));
if (failed === 0) {
  console.log(`全部 ${tasks.length} 项自检通过`);
  process.exit(0);
}
console.log(`${failed} / ${tasks.length} 项自检失败`);
process.exit(1);
