// DDL 双层校验：
//   第一层 —— 语法：用 MySQL 方言解析每条建表语句，抓真正的语法错误
//   第二层 —— 结构：主键 / 索引列存在性 / 表注释 / DROP 与 CREATE 配对
// 为什么两层都要：解析器只保证"能读懂"，不保证"建出来的表是对的"；
// 而结构检查抓不出少写一个逗号这类问题。二者互补。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
// node-sql-parser 是 CommonJS 包，必须走默认导入再解构；
// 直接用 { Parser } 具名导入在 Node 22 下会报 "Named export not found"
import sqlParser from 'node-sql-parser';

const { Parser } = sqlParser;

const here = path.dirname(fileURLToPath(import.meta.url));
const sqlDir = path.resolve(here, '../src/main/resources/db/migration');
const H = '─'.repeat(74);

let failed = false;

const files = fs.readdirSync(sqlDir).filter((f) => f.endsWith('.sql')).sort();
console.log(`SQL 校验：${files.length} 个迁移脚本`);

for (const file of files) {
  const sql = fs.readFileSync(path.join(sqlDir, file), 'utf8');

  // ---------- 第一层：语法 ----------
  const parser = new Parser();
  // 先剥掉整行注释再切分。
  // 【踩过的坑】直接按分号切分时，每条语句都会带着它上面的注释行，
  // 于是 `-- 说明\nINSERT ...` 因为"以 -- 开头"被整体跳过，
  // V2 的 6 条 INSERT 一条都没校验，却显示"通过" —— 这比报错更危险。
  const cleaned = sql
    .split('\n')
    .filter((line) => !line.trim().startsWith('--'))
    .join('\n');
  const statements = cleaned
    .split(/;\s*\n/)
    .map((s) => s.trim())
    .filter((s) => s && !/^(SET\s|DROP\s)/i.test(s));

  let syntaxOk = 0;
  const syntaxErrors = [];
  for (const stmt of statements) {
    // 表级 COMMENT='...' 里可能出现解析器敏感的字符，校验语法时先摘掉；
    // 它是否存在由第二层的正则单独检查，不会漏。
    const sanitized = stmt.replace(/COMMENT\s*=\s*'[^']*'/g, '');
    try {
      parser.astify(sanitized, { database: 'MySQL' });
      syntaxOk++;
    } catch (e) {
      syntaxErrors.push(e.message.split('\n')[0]);
    }
  }

  // ---------- 第二层：结构 ----------
  const blocks = sql.split(/CREATE TABLE\s+/i).slice(1);
  const structErrors = [];
  const structWarns = [];
  const tables = [];

  for (const raw of blocks) {
    const nameMatch = raw.match(/^`?(\w+)`?\s*\(/);
    if (!nameMatch) continue;
    const table = nameMatch[1];

    let depth = 0;
    let end = -1;
    for (let i = raw.indexOf('('); i < raw.length; i++) {
      if (raw[i] === '(') depth++;
      else if (raw[i] === ')') { depth--; if (depth === 0) { end = i; break; } }
    }
    const body = raw.slice(raw.indexOf('(') + 1, end);
    const tail = raw.slice(end);
    const lines = body.split('\n').map((l) => l.trim()).filter(Boolean);

    const cols = [];
    let idx = 0, uk = 0, checks = 0;

    for (const line of lines) {
      const clean = line.replace(/,$/, '');
      const keyMatch = clean.match(/^(PRIMARY KEY|UNIQUE KEY|KEY|INDEX)\s*(`?\w+`?)?\s*\(([^)]*)\)/i);
      if (keyMatch) {
        const kind = keyMatch[1].toUpperCase();
        if (kind.startsWith('UNIQUE')) uk++;
        else if (!kind.startsWith('PRIMARY')) idx++;
        keyMatch[3].split(',').forEach((c) => {
          const col = c.trim().replace(/`/g, '').split(' ')[0];
          if (col && !cols.includes(col)) {
            structErrors.push(`${table}: 索引引用了不存在的列 "${col}"`);
          }
        });
        continue;
      }
      if (/^(CONSTRAINT|CHECK)\b/i.test(clean)) { checks++; continue; }
      const colMatch = clean.match(/^`?(\w+)`?\s+[A-Z]/i);
      if (colMatch) cols.push(colMatch[1]);
    }

    if (!/PRIMARY KEY/i.test(body)) structErrors.push(`${table}: 缺少主键`);
    if (!/COMMENT\s*=\s*'/.test(tail)) structWarns.push(`${table}: 缺少表注释`);
    if (cols.length === 0) structErrors.push(`${table}: 未解析到任何列`);

    tables.push({ table, cols: cols.length, idx, uk, checks });
  }

  // DROP 与 CREATE 配对只在"整库重建型"脚本里检查（V1 那种先 DROP 再 CREATE 的写法）。
  // 增量迁移（如 V5：只 ALTER + 新建一张表）本来就不该有 DROP，
  // 无条件比对会把它误判成错误 —— 假红灯和假绿灯一样有害，都会让人不再相信自检。
  const drops = (sql.match(/DROP TABLE IF EXISTS/gi) || []).length;
  if (drops > 0 && drops !== tables.length) {
    structErrors.push(`DROP(${drops}) 与 CREATE(${tables.length}) 数量不一致`);
  }
  if (drops > 0 && tables.length === 0) {
    structErrors.push('脚本里只 DROP 不 CREATE，迁移后表结构会丢');
  }

  // ---------- 输出 ----------
  console.log(H);
  console.log(file);
  console.log(`  语法：${syntaxOk}/${statements.length} 条语句通过` +
    (syntaxErrors.length ? `，失败 ${syntaxErrors.length} 条` : ''));
  syntaxErrors.forEach((m) => console.log(`    ✗ ${m}`));
  if (tables.length) {
    const totalCols = tables.reduce((a, t) => a + t.cols, 0);
    console.log(`  结构：${tables.length} 张表 / ${totalCols} 个字段`);
    console.log(`    ${tables.map((t) => t.table).join(', ')}`);
  }
  structWarns.forEach((w) => console.log(`    · ${w}`));
  structErrors.forEach((m) => console.log(`    ✗ ${m}`));

  if (syntaxErrors.length || structErrors.length) failed = true;
}

console.log(H);
console.log(failed ? 'SQL 校验：失败' : 'SQL 校验：通过');
process.exit(failed ? 1 : 0);
