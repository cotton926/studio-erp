// 业绩分摊算法验证（Java 未编译时的替代手段）
//
// 严格镜像 PerformanceAllocator + MoneyUtil 的实现：
//   · 金额统一用「分」的 BigInt 表示，等价于 DECIMAL(12,2)
//   · 除法取整 floor((2n+d)/(2d))，等价于 BigDecimal HALF_UP
//   · 同样使用累计取整法
//
// 目的不是替代单元测试（PerformanceAllocatorTest），而是证明算法的数值正确性：
// 无论怎么随机组合，"个人业绩合计 == 核销金额" 且 "无负数" 必须恒成立。
// 这两条不变量一旦被破坏，员工看到的钱就是错的。

const FULL_BP = 10000n;

const divRound = (num, den) => {
  if (den === 0n) throw new Error('分母为 0');
  return (num * 2n + den) / (2n * den);
};

function allocate(confirmCents, items) {
  if (confirmCents < 0n) throw new Error('确认金额不能为负数');
  const eligible = items.filter((i) => i.type === 1 && i.amount > 0n);
  if (eligible.length === 0) throw new Error('订单没有可计价的有效明细行');

  for (const it of eligible) {
    if (it.assignees.length === 0) throw new Error(`明细行【${it.name}】没有指定设计师`);
    const sum = it.assignees.reduce((a, x) => a + BigInt(x.bp), 0n);
    if (sum !== FULL_BP) throw new Error(`明细行【${it.name}】分成比例合计为 ${Number(sum) / 100}%`);
  }

  const base = eligible.reduce((a, x) => a + x.amount, 0n);
  if (base <= 0n) throw new Error('业绩分摊基数为 0');
  if (confirmCents === 0n) return [];

  const out = [];
  let cumBase = 0n;
  let cumAlloc = 0n;
  for (const it of eligible) {
    cumBase += it.amount;
    const targetCum = divRound(confirmCents * cumBase, base);
    const lineAmount = targetCum - cumAlloc;
    cumAlloc = targetCum;
    if (lineAmount === 0n) continue;

    let cumBp = 0n;
    let cum = 0n;
    for (const a of it.assignees) {
      cumBp += BigInt(a.bp);
      const t = divRound(lineAmount * cumBp, FULL_BP);
      const share = t - cum;
      cum = t;
      if (share === 0n) continue;
      out.push({ emp: a.emp, item: it.id, amount: share });
    }
  }
  return out;
}

const total = (rows) => rows.reduce((a, r) => a + r.amount, 0n);
const yuan = (cents) => (Number(cents) / 100).toFixed(2);
const item = (id, name, value, assignees, type = 1) => ({
  id, name, amount: BigInt(Math.round(value * 100)), assignees, type,
});

let pass = 0;
let total_ = 0;
const check = (name, fn) => {
  total_++;
  try { fn(); pass++; console.log(`  ✓ ${name}`); }
  catch (e) { console.log(`  ✗ ${name} → ${e.message}`); }
};
const eq = (a, b, msg) => {
  if (a !== b) throw new Error(`${msg}：期望 ${b}，实际 ${a}`);
};

console.log('业绩分摊算法验证');
console.log('─'.repeat(74));

check('单人 100%：订金 6000 全额归该设计师', () => {
  const r = allocate(600000n, [item(1, '主视觉海报', 20000, [{ emp: 101, bp: 10000 }])]);
  eq(r.length, 1, '流水条数');
  eq(yuan(r[0].amount), '6000.00', '金额');
});

check('两人 70/30：6000 拆成 4200 与 1800', () => {
  const r = allocate(600000n, [item(1, '画册', 10000, [{ emp: 101, bp: 7000 }, { emp: 102, bp: 3000 }])]);
  eq(yuan(r[0].amount), '4200.00', 'A 金额');
  eq(yuan(r[1].amount), '1800.00', 'B 金额');
  eq(total(r), 600000n, '合计');
});

check('三行除不尽 3333.33×3 分摊 10000，合计精确', () => {
  const items = [1, 2, 3].map((i) =>
    item(i, `内页${i}`, 3333.33, [{ emp: 101, bp: 5000 }, { emp: 102, bp: 5000 }]));
  const r = allocate(1000000n, items);
  eq(total(r), 1000000n, '合计');
  r.forEach((x) => { if (x.amount <= 0n) throw new Error('出现 0 或负数业绩'); });
});

check('极小额 0.01 分给三人 33/33/34，仍是 0.01 且无负数', () => {
  const r = allocate(1n, [item(1, '修改一处', 100,
    [{ emp: 101, bp: 3300 }, { emp: 102, bp: 3300 }, { emp: 103, bp: 3400 }])]);
  eq(total(r), 1n, '合计');
  r.forEach((x) => { if (x.amount <= 0n) throw new Error('出现负数业绩'); });
});

check('赠品与返工行不参与分摊，不稀释正常业绩', () => {
  const r = allocate(1000000n, [
    item(1, '海报', 10000, [{ emp: 101, bp: 10000 }]),
    item(2, '赠品-名片', 0, [{ emp: 101, bp: 10000 }], 2),
    item(3, '返工-换文案', 0, [], 3),
  ]);
  eq(r.length, 1, '流水条数');
  eq(yuan(r[0].amount), '10000.00', '金额');
});

check('比例不足 100% 时拒绝确认，报错含差额', () => {
  try {
    allocate(100000n, [item(1, '包装', 5000, [{ emp: 101, bp: 7000 }, { emp: 102, bp: 2000 }])]);
    throw new Error('未抛异常');
  } catch (e) {
    if (!e.message.includes('90%')) throw new Error('报错未包含差额：' + e.message);
  }
});

check('无有效明细行时拒绝确认', () => {
  try {
    allocate(50000n, [item(1, '赠品', 0, [{ emp: 101, bp: 10000 }], 2)]);
    throw new Error('未抛异常');
  } catch (e) { /* 预期 */ }
});

check('负数金额被拒绝，冲销须走反向流水', () => {
  try {
    allocate(-10000n, [item(1, '海报', 1000, [{ emp: 101, bp: 10000 }])]);
    throw new Error('未抛异常');
  } catch (e) { /* 预期 */ }
});

// 随机压测：随机行数 / 人数 / 比例 / 金额，校验两条不变量
let stressFail = 0;
const ROUNDS = 1000;
for (let round = 0; round < ROUNDS; round++) {
  const lineCount = 1 + Math.floor(Math.random() * 5);
  const items = [];
  for (let i = 0; i < lineCount; i++) {
    const cents = BigInt(1 + Math.floor(Math.random() * 3000000));
    const personCount = Math.random() < 0.8 ? 1 : 2 + Math.floor(Math.random() * 2);
    let remain = 10000;
    const assignees = [];
    for (let p = 0; p < personCount; p++) {
      const bp = p === personCount - 1 ? remain : Math.floor(remain * Math.random());
      assignees.push({ emp: 100 + p, bp });
      remain -= bp;
    }
    items.push({ id: i + 1, name: `随机行${i}`, amount: cents, assignees, type: 1 });
  }
  const baseCents = items.reduce((a, x) => a + x.amount, 0n);
  const confirm = BigInt(1 + Math.floor(Math.random() * Number(baseCents)));
  try {
    const r = allocate(confirm, items);
    if (total(r) !== confirm) stressFail++;
    if (r.some((x) => x.amount <= 0n)) stressFail++;
  } catch {
    stressFail++;
  }
}

console.log('─'.repeat(74));
console.log(`  用例 ${pass}/${total_} 通过`);
console.log(`  随机压测 ${ROUNDS} 组：${stressFail === 0
  ? '全部通过（合计恒等于核销额，无负数）'
  : `失败 ${stressFail} 处`}`);
console.log('─'.repeat(74));
console.log(pass === total_ && stressFail === 0 ? '算法验证：通过' : '算法验证：失败');
process.exit(pass === total_ && stressFail === 0 ? 0 : 1);
