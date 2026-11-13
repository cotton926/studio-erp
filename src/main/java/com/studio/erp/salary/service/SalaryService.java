package com.studio.erp.salary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studio.erp.auth.CurrentUser;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.enums.LedgerType;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.employee.domain.EmpLevel;
import com.studio.erp.employee.entity.Employee;
import com.studio.erp.employee.mapper.EmployeeMapper;
import com.studio.erp.performance.domain.MoneyUtil;
import com.studio.erp.salary.domain.BaseSalaryResolver;
import com.studio.erp.salary.domain.SalaryBreakdown;
import com.studio.erp.salary.domain.SalaryCalculator;
import com.studio.erp.salary.domain.SalaryTierDef;
import com.studio.erp.salary.domain.SalaryTierMode;
import com.studio.erp.salary.dto.BasePolicySaveRequest;
import com.studio.erp.salary.dto.BasePolicyVO;
import com.studio.erp.salary.dto.SalaryVO;
import com.studio.erp.salary.dto.TierSaveRequest;
import com.studio.erp.salary.dto.TierVO;
import com.studio.erp.salary.entity.SalaryBasePolicy;
import com.studio.erp.salary.entity.SalaryTier;
import com.studio.erp.salary.mapper.EmployeePerfRow;
import com.studio.erp.salary.mapper.SalaryBasePolicyMapper;
import com.studio.erp.salary.mapper.SalaryEmployeeRow;
import com.studio.erp.salary.mapper.SalaryQueryMapper;
import com.studio.erp.salary.mapper.SalaryTierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工资核算服务。
 *
 * <p>【本类的三个职责，以及为什么不能合并】
 * 1) 取数：把业绩流水汇总成"本月业绩"；
 * 2) 算钱：交给 {@link SalaryCalculator} 这个纯函数；
 * 3) 组装：把人员信息、业绩、档位说明拼成一张工资单。
 * 只有第 2 步是"规则"，1 和 3 是"数据搬运"。分开的好处是改规则时只动 Calculator，
 * 改数据源时只动这里，两者的测试也互不干扰。
 *
 * <p>【业绩口径只有一个：已到账回款】
 * 工资按月核算，用的是 {@code LedgerType} 里 countsAsPerformance=true 的流水
 * （CONFIRM / REVERSE / ADJUST），也就是"钱到账才算业绩"。
 * SIGN（签单额）只做展示，绝不进提成 —— 否则客户跑单时公司已经把钱发出去了，
 * 而这类损失在几十人的小公司里是致命的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryService {

    /**
     * 参与提成计算的流水类型。
     *
     * <p>从枚举推导，不写死 {2,3,4}。日后新增一种"算业绩"的流水类型，
     * 这里会自动跟上；如果写死数字，新类型就会悄悄不算工资，而页面显示一切正常。
     */
    private static final List<Integer> PERF_TYPES = Arrays.stream(LedgerType.values())
            .filter(LedgerType::countsAsPerformance)
            .map(LedgerType::getCode)
            .toList();

    private final SalaryTierMapper tierMapper;
    private final SalaryBasePolicyMapper basePolicyMapper;
    private final SalaryQueryMapper queryMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditService auditService;

    /** 阶梯算法，默认跳点；改配置即可切换，不用改代码 */
    @Value("${studio.salary.tier-mode:WHOLE}")
    private String tierModeText;

    // =====================================================================
    // 查询
    // =====================================================================

    /**
     * 查自己的工资。
     *
     * <p>【为什么员工端只查自己，不接收 employeeId 参数】
     * 不接收参数就不存在"改参数越权"这条攻击面。
     * 需要看别人的工资时走 {@link #teamSalary}，那条路径有独立的数据范围校验。
     */
    public SalaryVO mySalary(String period) {
        Long employeeId = CurrentUserHolder.currentEmployeeId();
        if (employeeId == null) {
            // 管理员账号可能没有绑定员工（纯系统账号），此时没有"本人工资"这个概念
            throw new BizException(ErrorCode.BAD_REQUEST, "当前账号未绑定员工，无法查看个人工资");
        }
        return calculateOne(normalizePeriod(period), requireEmployee(employeeId),
                loadTierSet(), mode(), loadBasePolicies());
    }

    /**
     * 查团队工资表（主管 / 老板）。
     *
     * <p>【为什么业绩用一次 GROUP BY 查全公司，而不是每人查一次】
     * 几十个人的话，每人一次查询就是几十次往返，页面要等好几秒；
     * 而且这种 N+1 会随人数线性变差，明年招到 80 人就明显卡了。
     * 一次汇总 + 内存匹配，无论多少人都是固定两次查询。
     */
    public List<SalaryVO> teamSalary(String period, String keyword) {
        String p = normalizePeriod(period);
        TierSet tierSet = loadTierSet();
        SalaryTierMode mode = mode();
        Map<EmpLevel, BigDecimal> basePolicies = loadBasePolicies();

        List<SalaryEmployeeRow> employees = queryMapper.selectActiveEmployees(keyword);
        Map<Long, BigDecimal> perfMap = toMap(queryMapper.sumPerformanceByEmployee(p, PERF_TYPES));
        Map<Long, BigDecimal> signMap = toMap(queryMapper.sumSignAmountByEmployee(p));

        List<SalaryVO> result = new ArrayList<>(employees.size());
        for (SalaryEmployeeRow row : employees) {
            BigDecimal perf = perfMap.getOrDefault(row.employeeId(), BigDecimal.ZERO);
            BigDecimal sign = signMap.getOrDefault(row.employeeId(), BigDecimal.ZERO);
            BaseSalaryResolver.Resolution base = resolveBase(row, basePolicies);
            SalaryBreakdown bd = compute(perf, base.amount(), tierSet, mode);
            result.add(SalaryVO.of(p, row, sign, bd, remarkOf(tierSet, perf), base));
        }
        // 按应发降序：主管看工资表第一眼要的是"这个月谁拿得最多"
        result.sort(Comparator.comparing(SalaryVO::getPayable).reversed());
        return result;
    }

    /** 档位列表（员工也能看，方便自己算） */
    public List<TierVO> listTiers() {
        return tierMapper.selectList(new LambdaQueryWrapper<SalaryTier>()
                        .orderByAsc(SalaryTier::getMinAmount))
                .stream().map(TierVO::of).toList();
    }

    /**
     * 底薪政策（员工也能看）。
     *
     * <p>【为什么只读也开放给员工】
     * 工资单上会写"底薪 2000（精英员工）"，如果不给他看政策表，
     * 他无法判断这 2000 是公司标准还是主管随口定的。
     * 级别是公开的、金额是按规则算的，这类质疑就自然消失。
     */
    public List<BasePolicyVO> listBasePolicy() {
        return basePolicyMapper.selectList(new LambdaQueryWrapper<SalaryBasePolicy>()
                        .orderByAsc(SalaryBasePolicy::getSortNo)
                        .orderByAsc(SalaryBasePolicy::getId))
                .stream().map(BasePolicyVO::of).toList();
    }

    // =====================================================================
    // 维护
    // =====================================================================

    /**
     * 整表替换阶梯。
     *
     * <p>【为什么整表替换而不是逐档改】
     * 档位之间有"首尾相接"的整体约束。逐档改会出现"删掉中间一档"的中间态，
     * 而此时若有工资查询进来，会命中错误的档位而不报错。
     * 整表提交 = 一次校验通过才整体生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<TierVO> replaceTiers(TierSaveRequest request) {
        List<SalaryTierDef> defs = new ArrayList<>();
        List<SalaryTier> rows = new ArrayList<>();
        int seq = 1;
        for (TierSaveRequest.TierItem item : request.tiers()) {
            int levelNo = item.levelNo() == null ? seq : item.levelNo();
            // 构造即校验（下限为空、上限不大于下限、比例越界，都会在这里抛）
            SalaryTierDef def = new SalaryTierDef(levelNo, item.minAmount(), item.maxAmount(), item.rateBp());
            defs.add(def);

            SalaryTier row = new SalaryTier();
            row.setLevelNo(levelNo);
            row.setMinAmount(MoneyUtil.yuan(item.minAmount()));
            row.setMaxAmount(item.maxAmount() == null ? null : MoneyUtil.yuan(item.maxAmount()));
            row.setRateBp(item.rateBp());
            row.setRemark(item.remark());
            rows.add(row);
            seq++;
        }

        // 整体校验：第一档必须从 0 开始、相邻档必须首尾相接、最后一档不能被封顶
        try {
            SalaryCalculator.normalize(defs);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.SALARY_TIER_INVALID, e.getMessage());
        }

        // 【为什么不是"先清空再插入"】项目开了全表删除保护，DELETE 不带 where 会被直接拦掉；
        // 而且清空瞬间若有人查工资，会读到 0 档位。改成按 min_amount 对齐：
        // 同一个下限的档位就地更新，新出现的插入，消失的按 id 删除。
        Map<BigDecimal, SalaryTier> existing = tierMapper.selectList(null).stream()
                .collect(Collectors.toMap(t -> MoneyUtil.yuan(t.getMinAmount()), t -> t, (a, b) -> a));
        Set<BigDecimal> keep = new HashSet<>();
        for (SalaryTier row : rows) {
            BigDecimal key = MoneyUtil.yuan(row.getMinAmount());
            keep.add(key);
            SalaryTier old = existing.get(key);
            if (old == null) {
                tierMapper.insert(row);
                continue;
            }
            row.setId(old.getId());
            // 用 UpdateWrapper 逐列 set：最高档 max_amount 是 null（无上限），
            // updateById 会跳过 null 字段，导致"把 20000~25000 改成 20000~无上限"改不掉。
            tierMapper.update(null, new LambdaUpdateWrapper<SalaryTier>()
                    .eq(SalaryTier::getId, old.getId())
                    .set(SalaryTier::getLevelNo, row.getLevelNo())
                    .set(SalaryTier::getMaxAmount, row.getMaxAmount())
                    .set(SalaryTier::getRateBp, row.getRateBp())
                    .set(SalaryTier::getRemark, row.getRemark()));
        }
        List<Long> stale = existing.values().stream()
                .filter(t -> !keep.contains(MoneyUtil.yuan(t.getMinAmount())))
                .map(SalaryTier::getId)
                .toList();
        if (!stale.isEmpty()) {
            // 有 where 的批量删除，不走全表删除路径
            tierMapper.deleteBatchIds(stale);
        }
        log.info("工资阶梯已更新，共 {} 档，操作人={}", rows.size(), CurrentUserHolder.get() == null
                ? "-" : CurrentUserHolder.get().username());
        // 阶梯决定所有人下个月拿多少钱，属于"改了没人会当场发现、发工资时才发现"的配置。
        // 记下改前整表，事后能还原出"当时是按什么规则算的"。
        auditService.policy("SALARY_TIER", 0L,
                formatTiers(existing.values().stream().sorted(Comparator.comparing(SalaryTier::getMinAmount)).toList()),
                formatTiers(rows));
        return rows.stream().map(TierVO::of).toList();
    }

    /** 阶梯的可读摘要：0~20000:40% | 20000~25000:45% | 25000~∞:50% */
    private static String formatTiers(List<SalaryTier> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(SalaryTier::getMinAmount))
                .map(t -> plain(t.getMinAmount()) + "~"
                        + (t.getMaxAmount() == null ? "∞" : plain(t.getMaxAmount()))
                        + ":" + bpText(t.getRateBp()))
                .collect(Collectors.joining(" | "));
    }

    private static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    private static String bpText(Integer rateBp) {
        return rateBp == null ? "?" : BigDecimal.valueOf(rateBp).movePointLeft(2)
                .stripTrailingZeros().toPlainString() + "%";
    }

    /**
     * 整表替换底薪政策。
     *
     * <p>【为什么必须校验"覆盖全部级别"】
     * 少配一个级别，该级别的员工打开工资页就会直接报错（不是算错，是算不出来）。
     * 在保存时就拦住，比事后被十几个人同时反馈要便宜得多。
     *
     * <p>【为什么把 levelCode 归一化成枚举名再落库】
     * 前端传 "elite" 也存成 "ELITE"，查询时 EmbLevel.parse 不需要再猜大小写。
     * 归一化只做一次（写入口），读的地方就永远不用防。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BasePolicyVO> replaceBasePolicy(BasePolicySaveRequest request) {
        List<SalaryBasePolicy> rows = new ArrayList<>();
        Set<EmpLevel> seen = EnumSet.noneOf(EmpLevel.class);
        int seq = 1;
        for (BasePolicySaveRequest.PolicyItem item : request.policies()) {
            EmpLevel level = parseLevel(item.levelCode());
            if (!seen.add(level)) {
                throw new BizException(ErrorCode.SALARY_BASE_POLICY_INVALID,
                        "级别「" + level.getLabel() + "」重复提交，一个级别只能有一条政策");
            }
            SalaryBasePolicy row = new SalaryBasePolicy();
            row.setLevelCode(level.name());
            row.setLevelName(level.getLabel());
            row.setBaseSalary(MoneyUtil.yuan(item.baseSalary()));
            row.setSortNo(seq++);
            row.setRemark(item.remark());
            rows.add(row);
        }

        List<String> missing = Arrays.stream(EmpLevel.values())
                .filter(l -> !seen.contains(l))
                .map(EmpLevel::getLabel)
                .toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.SALARY_BASE_POLICY_INVALID,
                    "底薪政策缺少级别：" + String.join("、", missing));
        }

        // 【为什么按 level_code 对齐而不是清空重插】理由同 replaceTiers：
        // 全表删除会被 BlockAttackInnerInterceptor 拦掉，且清空瞬间查工资会算不出底薪。
        // level_code 有唯一索引，正好当业务键用。
        Map<String, SalaryBasePolicy> existing = basePolicyMapper.selectList(null).stream()
                .collect(Collectors.toMap(SalaryBasePolicy::getLevelCode, p -> p, (a, b) -> a));
        for (SalaryBasePolicy row : rows) {
            SalaryBasePolicy old = existing.get(row.getLevelCode());
            if (old == null) {
                basePolicyMapper.insert(row);
                continue;
            }
            row.setId(old.getId());
            // 逐列 set（含 null）：要把某级别的说明清空时，updateById 会跳过 null 字段
            basePolicyMapper.update(null, new LambdaUpdateWrapper<SalaryBasePolicy>()
                    .eq(SalaryBasePolicy::getId, old.getId())
                    .set(SalaryBasePolicy::getLevelName, row.getLevelName())
                    .set(SalaryBasePolicy::getBaseSalary, row.getBaseSalary())
                    .set(SalaryBasePolicy::getSortNo, row.getSortNo())
                    .set(SalaryBasePolicy::getRemark, row.getRemark()));
        }
        // 库里可能存在已不再使用的级别（历史遗留 / 手工改过数据），这里顺手清掉。
        // 本次提交已校验覆盖全部 EmpLevel，所以留下的都是真正多余的。
        List<Long> stale = existing.values().stream()
                .filter(p -> {
                    EmpLevel lv = parseLevelQuietly(p.getLevelCode());
                    // 非法级别码一律视为多余数据：它永远匹配不到任何员工
                    return lv == null || !seen.contains(lv);
                })
                .map(SalaryBasePolicy::getId)
                .toList();
        if (!stale.isEmpty()) {
            basePolicyMapper.deleteBatchIds(stale);
        }
        log.info("底薪政策已更新：{}，操作人={}",
                rows.stream().map(r -> r.getLevelName() + "=" + r.getBaseSalary().toPlainString())
                        .collect(Collectors.joining(", ")),
                CurrentUserHolder.get() == null ? "-" : CurrentUserHolder.get().username());
        auditService.policy("SALARY_BASE_POLICY", 0L,
                formatPolicies(existing.values().stream().toList()),
                formatPolicies(rows));
        return rows.stream().map(BasePolicyVO::of).toList();
    }

    /** 底薪政策的可读摘要：普通=1500 | 精英=2000 | 主管=3000 */
    private static String formatPolicies(List<SalaryBasePolicy> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(SalaryBasePolicy::getLevelCode))
                .map(p -> p.getLevelName() + "=" + plain(p.getBaseSalary()))
                .collect(Collectors.joining(" | "));
    }

    /**
     * 调整底薪。
     *
     * <p>【为什么要单独一个方法并记日志】
     * 底薪是敏感字段，改错了员工直接少拿钱。日志里记下"谁把谁从多少改成多少"，
     * 是事后唯一能说清楚的方式 —— 表里只存最终值，看不出改动轨迹。
     *
     * <p>【传 null 的含义是"恢复按级别"，不是"没传"】
     * 有了分级底薪政策后，个人底薪只是覆盖值。要撤销特批（比如实习期满），
     * 必须能把这一列清回 NULL。若把 null 当成参数缺失直接拒绝，
     * 就只能填一个假数字（比如填回政策金额），以后再调政策这个人就跟不上了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBaseSalary(Long employeeId, BigDecimal baseSalary) {
        BigDecimal after = baseSalary == null ? null : MoneyUtil.yuan(baseSalary);
        if (after != null && after.signum() < 0) {
            throw new BizException(ErrorCode.BASE_SALARY_INVALID);
        }
        Integer status = queryMapper.selectEmployeeStatus(employeeId);
        if (status == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        if (status == 0) {
            throw new BizException(ErrorCode.EMPLOYEE_RESIGNED);
        }

        BigDecimal before = queryMapper.selectEmployee(employeeId).baseSalary();
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, employeeId)
                .set(Employee::getBaseSalary, after));

        CurrentUser operator = CurrentUserHolder.get();
        log.info("底薪调整 employeeId={} {} -> {} 操作人={}",
                employeeId,
                before == null ? "按级别" : before.toPlainString(),
                after == null ? "按级别" : after.toPlainString(),
                operator == null ? "-" : operator.username());
        // 底薪是员工最敏感的数字，改错了当场就会来问。留痕要能回答"谁、什么时候、从多少改到多少"
        auditService.record("EMPLOYEE", employeeId, "base_salary",
                before == null ? "按级别" : before.toPlainString(),
                after == null ? "按级别" : after.toPlainString());
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private SalaryVO calculateOne(String period, SalaryEmployeeRow row,
                                  TierSet tierSet, SalaryTierMode mode,
                                  Map<EmpLevel, BigDecimal> basePolicies) {
        BigDecimal perf = queryMapper.sumPerformanceOf(row.employeeId(), period, PERF_TYPES);
        BigDecimal sign = queryMapper.sumSignAmountOf(row.employeeId(), period);
        BaseSalaryResolver.Resolution base = resolveBase(row, basePolicies);
        SalaryBreakdown bd = compute(perf, base.amount(), tierSet, mode);
        return SalaryVO.of(period, row, sign, bd, remarkOf(tierSet, perf), base);
    }

    /**
     * 解析某人的实际底薪：个人指定优先，否则按级别取政策金额。
     *
     * <p>【为什么把 IllegalArgumentException 翻成 BizException】
     * 同 {@link #compute}：领域层不认识业务错误码。不翻译的话，
     * "某个级别底薪没配"会变成"系统开小差"，而它其实是主管点两下就能修的问题。
     */
    private BaseSalaryResolver.Resolution resolveBase(SalaryEmployeeRow row,
                                                     Map<EmpLevel, BigDecimal> policies) {
        try {
            return BaseSalaryResolver.resolve(row.baseSalary(), EmpLevel.parse(row.empLevel()), policies);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.SALARY_BASE_POLICY_INVALID, e.getMessage());
        }
    }

    /**
     * 载入级别 → 底薪。
     *
     * <p>【为什么每次实时读库，不做缓存】
     * 政策只有三行，一次全表扫描不到 1ms；而做缓存就要处理"改了政策但工资页还是旧数"。
     * 工资算错的代价远高于多查一次库。
     *
     * <p>【遇到脏的 level_code 为什么只 warn 不抛】
     * 库里出现非法级别码（比如手工改过数据），只影响那一条政策；
     * 若直接抛异常，全公司都打不开工资页。让该级别缺失，再由真正用到它的人报错，
     * 影响面最小、定位也最准。
     */
    private Map<EmpLevel, BigDecimal> loadBasePolicies() {
        List<SalaryBasePolicy> rows = basePolicyMapper.selectList(null);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.SALARY_BASE_POLICY_EMPTY);
        }
        Map<EmpLevel, BigDecimal> map = new EnumMap<>(EmpLevel.class);
        for (SalaryBasePolicy row : rows) {
            EmpLevel level = parseLevelQuietly(row.getLevelCode());
            if (level == null || row.getBaseSalary() == null) {
                log.warn("底薪政策存在非法行 levelCode={} baseSalary={}，已忽略",
                        row.getLevelCode(), row.getBaseSalary());
                continue;
            }
            map.put(level, row.getBaseSalary());
        }
        return map;
    }

    /** 级别码解析，统一翻成业务错误，避免 400 变成 500 */
    private EmpLevel parseLevel(String code) {
        try {
            return EmpLevel.parse(code);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.EMP_LEVEL_INVALID, e.getMessage());
        }
    }

    /**
     * 宽松解析：脏数据返回 null，不抛异常。
     *
     * <p>用在"清理历史数据"的场景（比如找出早就不用的级别）。那里遇到一行脏数据
     * 应该继续处理剩下的，而不是让整次保存失败 —— 否则一条手工改错的数据
     * 就能把底薪政策锁死，只能进数据库改。
     */
    private EmpLevel parseLevelQuietly(String code) {
        try {
            return EmpLevel.parse(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 调算法并翻译异常。
     *
     * <p>【为什么要把 IllegalArgumentException 翻成 BizException】
     * 领域层不认识业务错误码（它要能脱离框架跑单测），所以抛的是 IllegalArgumentException。
     * 不翻译的话会掉进全局兜底分支，用户看到"系统开小差了"，
     * 而实际上这是"阶梯少配了一档"这种能自己修的问题，提示必须具体。
     */
    private SalaryBreakdown compute(BigDecimal perf, BigDecimal baseSalary, TierSet tierSet, SalaryTierMode mode) {
        try {
            return SalaryCalculator.compute(perf, baseSalary, tierSet.defs(), mode);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.SALARY_TIER_INVALID, e.getMessage());
        }
    }

    /** 命中档位的说明文案；负数业绩没有对应档位，退回第一档的说明 */
    private String remarkOf(TierSet tierSet, BigDecimal perf) {
        return tierSet.defs().stream()
                .filter(d -> d.covers(perf))
                .findFirst()
                .or(() -> tierSet.defs().stream().findFirst())
                .map(d -> tierSet.remarkByLevel().get(d.levelNo()))
                .orElse(null);
    }

    private TierSet loadTierSet() {
        // 每次实时读库：档位只有几行，一次主键范围扫描不到 1ms；
        // 做缓存就要处理"改了阶梯但缓存没失效"，而工资算错比慢一点严重得多。
        List<SalaryTier> rows = tierMapper.selectList(new LambdaQueryWrapper<SalaryTier>()
                .orderByAsc(SalaryTier::getMinAmount));
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.SALARY_TIER_EMPTY);
        }
        Map<Integer, String> remarkByLevel = rows.stream()
                .filter(r -> StringUtils.hasText(r.getRemark()))
                .collect(Collectors.toMap(SalaryTier::getLevelNo, SalaryTier::getRemark, (a, b) -> a));
        return new TierSet(rows.stream().map(SalaryTier::toDef).toList(), remarkByLevel);
    }

    private SalaryTierMode mode() {
        try {
            return SalaryTierMode.parse(tierModeText);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.SALARY_TIER_INVALID, e.getMessage());
        }
    }

    private SalaryEmployeeRow requireEmployee(Long employeeId) {
        SalaryEmployeeRow row = queryMapper.selectEmployee(employeeId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return row;
    }

    private static Map<Long, BigDecimal> toMap(List<EmployeePerfRow> rows) {
        Map<Long, BigDecimal> map = new HashMap<>(rows.size() * 2);
        for (EmployeePerfRow r : rows) {
            if (r.employeeId() != null && r.amount() != null) {
                map.put(r.employeeId(), r.amount());
            }
        }
        return map;
    }

    /**
     * 期间格式校验。
     *
     * <p>【为什么要严格校验，而不是查不到就返回 0】
     * 前端传成 "2026-11-01" 或 "2026/11" 时，若静默返回 0 元工资，
     * 员工会以为这个月没挣钱，然后打电话给主管 —— 排查成本远高于直接报错。
     */
    private String normalizePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return YearMonth.now().toString();
        }
        try {
            return YearMonth.parse(period.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.SALARY_PERIOD_INVALID);
        }
    }

    /** 档位集合：计算用的 defs + 展示用的说明文案 */
    private record TierSet(List<SalaryTierDef> defs, Map<Integer, String> remarkByLevel) {
    }
}
