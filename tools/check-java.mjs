// Java 源码语法校验：用 Java 官方语法实现逐个解析 .java 文件。
// 本机没有 JDK 时这是最接近编译的替代检查，能抓出：
// 括号/大括号不配对、方法体缺失、泛型写错、注解语法错、record 紧凑构造器写错。
// 它抓不出"类型不存在""方法签名不匹配"这类语义错误 —— 那些必须靠 mvn test。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'java-parser';

const here = path.dirname(fileURLToPath(import.meta.url));
const srcRoot = path.resolve(here, '../src');

const files = [];
const walk = (dir) => {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) walk(p);
    else if (f.endsWith('.java')) files.push(p);
  }
};
walk(srcRoot);
files.sort();

let ok = 0;
const bad = [];
for (const f of files) {
  const rel = path.relative(srcRoot, f).replace(/\\/g, '/');
  try {
    parse(fs.readFileSync(f, 'utf8'));
    ok++;
  } catch (e) {
    bad.push({ rel, msg: e.message.split('\n')[0] });
  }
}

console.log(`Java 语法校验：${files.length} 个文件，通过 ${ok} 个`);
if (bad.length) {
  console.log('─'.repeat(74));
  bad.forEach((b) => console.log(`  ✗ ${b.rel}\n    ${b.msg}`));
  process.exit(1);
}
console.log('  全部通过（注意：语法通过 ≠ 编译通过，类型与依赖仍需 mvn compile 验证）');
