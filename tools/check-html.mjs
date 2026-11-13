// 原型页面自检：标签闭合 + 关键元素存在性 + 不引外部 CDN。
// 为什么查 CDN：原型要能在断网、内网、手机上直接双击打开给老板看，
// 一旦引了 CDN，演示时白屏的概率极高。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const file = path.resolve(here, '../prototype/index.html');
const html = fs.readFileSync(file, 'utf8');

const errors = [];
const warns = [];

// 1) 标签闭合（只查成对标签，避免自闭合标签误报）
const PAIRED = ['div', 'section', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th',
  'nav', 'aside', 'main', 'p', 'span', 'select', 'button', 'html', 'head', 'body', 'style', 'script'];
for (const tag of PAIRED) {
  const open = (html.match(new RegExp(`<${tag}\\b`, 'gi')) || []).length;
  const close = (html.match(new RegExp(`</${tag}>`, 'gi')) || []).length;
  if (open !== close) errors.push(`<${tag}> 开 ${open} / 闭 ${close}，不配对`);
}

// 2) 外部资源
const external = html.match(/(src|href)\s*=\s*["']https?:\/\/[^"']+/gi) || [];
if (external.length) errors.push(`引用了外部资源，断网会白屏：${external.join(', ')}`);

// 3) 必备结构
const must = [
  ['<html lang="zh-CN">', '缺少中文语言声明'],
  ['<meta charset="utf-8">', '缺少字符集声明'],
  ['<meta name="viewport"', '缺少移动端视口声明，手机上会显示成缩小版'],
  ['id="view-perf"', '缺少「我的业绩」视图'],
  ['id="view-salary"', '缺少「我的工资」视图'],
  ['id="view-order"', '缺少「订单录入」视图'],
];
for (const [needle, msg] of must) {
  if (!html.includes(needle)) errors.push(msg);
}

// 4) 口径提示必须存在于页面（防止有人改文案时把口径说明删了）
if (!html.includes('回款到账月')) warns.push('页面上找不到「回款到账月」的口径说明');

const size = (Buffer.byteLength(html) / 1024).toFixed(1);
console.log(`原型校验：prototype/index.html（${size} KB）`);
if (warns.length) warns.forEach((w) => console.log(`  · ${w}`));
if (errors.length) {
  errors.forEach((e) => console.log(`  ✗ ${e}`));
  process.exit(1);
}
console.log('  标签闭合正常、无外部依赖、关键视图齐全');
