package com.studio.erp.order.service;

import com.studio.erp.auth.CurrentUser;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.enums.OrderStatus;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.order.dto.DeliverableVO;
import com.studio.erp.order.dto.ReworkStatVO;
import com.studio.erp.order.dto.ReviewRequest;
import com.studio.erp.order.entity.Deliverable;
import com.studio.erp.order.entity.ReworkLog;
import com.studio.erp.order.entity.ReviewResult;
import com.studio.erp.order.entity.SalesOrder;
import com.studio.erp.order.mapper.AssigneeRow;
import com.studio.erp.order.mapper.DeliverableMapper;
import com.studio.erp.order.mapper.DeliverableRow;
import com.studio.erp.order.mapper.OrderItemAssigneeMapper;
import com.studio.erp.order.mapper.ReworkCountRow;
import com.studio.erp.order.mapper.ReworkLogMapper;
import com.studio.erp.order.mapper.ReworkRow;
import com.studio.erp.order.mapper.ReworkStatRow;
import com.studio.erp.order.mapper.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 稿件版本与送审。
 *
 * <p>【为什么这套流程只登记结果，不做在线批注】
 * 需求阶段已经明确砍掉客户端门户：客户仍在微信里看图、回"这版不行"。
 * 系统要做的是把"第几版、谁说的、为什么退"记清楚，
 * 而不是再造一个客户不会用的批注工具。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverableService {

    /**
     * 允许上传的扩展名。
     *
     * <p>【为什么要有白名单】
     * 这是内网系统，但仍然有人会把 .exe / .bat / .jsp 传上来当"稿件"。
     * 一旦哪天有人把 upload 目录挂到 IIS/Nginx 上，这些文件就成了后门。
     * 白名单只放设计行业真正会用的格式，多一个都要有理由。
     */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff",
            "pdf", "ai", "psd", "eps", "cdr", "sketch", "fig", "indd", "xd",
            "zip", "rar", "7z",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp4", "mov", "txt");

    private final DeliverableMapper deliverableMapper;
    private final ReworkLogMapper reworkLogMapper;
    private final SalesOrderMapper orderMapper;
    private final OrderItemAssigneeMapper assigneeMapper;
    private final AuditService auditService;
    /** 上传写库用编程式事务：文件落盘必须在事务外，@Transactional 会把 IO 也包进去 */
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    @Value("${studio.storage.upload-dir:./data/uploads}")
    private String uploadDir;

    // =====================================================================
    // 查询
    // =====================================================================

    /** 某单的版本列表（新的在前），带返工记录 */
    public List<DeliverableVO> list(Long orderId) {
        CurrentUser user = CurrentUserHolder.require();
        assertVisible(orderId, user);

        List<DeliverableRow> rows = deliverableMapper.selectByOrder(orderId);
        Map<Long, List<ReworkRow>> byDeliverable = new HashMap<>();
        for (ReworkRow r : reworkLogMapper.selectByOrder(orderId)) {
            byDeliverable.computeIfAbsent(r.deliverableId(), k -> new ArrayList<>()).add(r);
        }

        List<DeliverableVO> result = new ArrayList<>(rows.size());
        for (DeliverableRow row : rows) {
            result.add(toVO(row, byDeliverable.get(row.id())));
        }
        return result;
    }

    // =====================================================================
    // 上传新版本
    // =====================================================================

    /**
     * 上传一个稿件版本。
     *
     * <p>【版本号由服务端算，不接受前端传】
     * 前端传版本号会出现"两个人同时传 V3"，唯一索引会拦下第二个，
     * 但用户看到的只是"上传失败"，不知道发生了什么。服务端算 + 冲突翻译成明确文案，
     * 才让人知道下一步该刷新重试。
     *
     * <p>【先落盘再写库，写库失败要删文件】
     * 顺序反过来的话，事务提交后文件写失败会留下一条指向空文件的记录，
     * 点下载就是 404。反过来则是"多一个没人认领的文件"，清理即可，不会产生坏数据。
     *
     * <p>【文件 IO 不进事务】
     * 整个方法刻意不加 @Transactional：几百 MB 的源文件 transferTo 期间，
     * 如果事务还开着，数据库连接会被一直占着，几个人同时传大文件就能拖垮连接池。
     * 落盘在校验之后、写库之前完成；写库用编程式事务包住（insert 只有这一条写操作）。
     */
    public Long upload(Long orderId, Long orderItemId, MultipartFile file) {
        CurrentUser user = CurrentUserHolder.require();
        SalesOrder order = requireOrder(orderId);
        assertVisible(orderId, user);

        if (OrderStatus.of(order.getStatus()) == OrderStatus.CANCELLED) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单已取消，不能再上传稿件");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.DELIVERABLE_FILE_EMPTY);
        }

        String originalName = safeFileName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.DELIVERABLE_TYPE_INVALID,
                    "不支持 ." + ext + " 格式，请上传图片、设计源文件、文档或压缩包");
        }

        Path root = rootDir();
        // 按订单分目录：一单几十个版本时，平铺在一个目录里连 ls 都不敢执行
        String relativePath = orderId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(relativePath).normalize();

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("稿件落盘失败 orderId={} file={}", orderId, originalName, e);
            throw new BizException(ErrorCode.BAD_REQUEST, "文件保存失败，请重试或联系管理员");
        }

        try {
            Long id = new TransactionTemplate(txManager).execute(status -> {
                int versionNo = deliverableMapper.maxVersion(orderId) + 1;
                Deliverable row = new Deliverable();
                row.setOrderId(orderId);
                row.setOrderItemId(orderItemId);
                row.setVersionNo(versionNo);
                row.setFileName(originalName);
                row.setFileUrl(relativePath);
                row.setFileSize(file.getSize());
                row.setFileType(ext);
                row.setReviewResult(ReviewResult.PENDING.getCode());
                row.setUploadedBy(user.employeeId());
                deliverableMapper.insert(row);
                log.info("稿件上传 orderNo={} V{} {} ({}KB) 上传人={}",
                        order.getOrderNo(), versionNo, originalName, file.getSize() / 1024, user.username());
                return row.getId();
            });
            return id;
        } catch (DuplicateKeyException e) {
            // 并发上传撞 uk_order_version：把文件删掉，别在磁盘上留垃圾
            deleteQuietly(target);
            throw new BizException(ErrorCode.DELIVERABLE_VERSION_CONFLICT);
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    // =====================================================================
    // 送审结果
    // =====================================================================

    /**
     * 录入客户反馈：通过或退回。
     *
     * <p>【为什么"待审"才能审】
     * 一个版本只对应一次客户答复。允许反复改结果，会出现"先退回、后改成通过"，
     * 返工记录已经生成却撤不掉 —— 数据自己打自己。
     * 真要重新送审，正确做法是上传新版本，版本号会让历史自然对得上。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long deliverableId, ReviewRequest request) {
        CurrentUser user = CurrentUserHolder.require();
        Deliverable row = deliverableMapper.selectById(deliverableId);
        if (row == null) {
            throw new BizException(ErrorCode.DELIVERABLE_NOT_FOUND);
        }
        ReviewResult result = ReviewResult.of(request.result());
        if (ReviewResult.of(row.getReviewResult()) != ReviewResult.PENDING) {
            throw new BizException(ErrorCode.DELIVERABLE_ALREADY_REVIEWED,
                    "该版本已是【" + ReviewResult.labelOf(row.getReviewResult()) + "】，如需再次送审请上传新版本");
        }
        if (result == ReviewResult.REJECTED && !StringUtils.hasText(request.feedback())) {
            throw new BizException(ErrorCode.DELIVERABLE_FEEDBACK_REQUIRED);
        }

        Deliverable update = new Deliverable();
        update.setId(deliverableId);
        update.setReviewResult(result.getCode());
        update.setFeedback(request.feedback());
        update.setReviewedBy(user.employeeId());
        update.setReviewedAt(LocalDateTime.now());
        deliverableMapper.updateById(update);

        if (result == ReviewResult.REJECTED) {
            ReworkLog rework = new ReworkLog();
            rework.setOrderId(row.getOrderId());
            rework.setDeliverableId(deliverableId);
            rework.setFromVersion(row.getVersionNo());
            rework.setReason(request.feedback());
            // 不指定就是原稿人自己改；换人接手时由审核人指定
            rework.setReworkBy(request.reworkBy() == null ? row.getUploadedBy() : request.reworkBy());
            reworkLogMapper.insert(rework);
            log.info("稿件退回 orderId={} V{} 原因={}", row.getOrderId(), row.getVersionNo(), request.feedback());
        } else {
            log.info("稿件通过 orderId={} V{}", row.getOrderId(), row.getVersionNo());
        }

        auditService.record("DELIVERABLE", deliverableId, "review_result",
                ReviewResult.labelOf(row.getReviewResult()), result.getLabel());
    }

    // =====================================================================
    // 下载
    // =====================================================================

    /**
     * 取下载文件。
     *
     * <p>【为什么不用静态资源目录直接暴露】
     * 静态目录没有权限：知道 URL 的人都能下，设计师把同事的稿子链接一转发就绕过了隔离。
     * 走接口才能先校验"你有没有资格看这单"。
     *
     * <p>【为什么强制 octet-stream + attachment】
     * 上传里允许 pdf、svg 等能被浏览器直接渲染的格式，若内联打开，
     * 恶意 svg 里的脚本会在系统域下执行。强制下载，浏览器就不会解析它。
     */
    public DownloadFile download(Long deliverableId) {
        CurrentUser user = CurrentUserHolder.require();
        Deliverable row = deliverableMapper.selectById(deliverableId);
        if (row == null) {
            throw new BizException(ErrorCode.DELIVERABLE_NOT_FOUND);
        }
        assertVisible(row.getOrderId(), user);

        Path root = rootDir();
        Path target = root.resolve(row.getFileUrl()).normalize();
        // 防路径穿越：库里存的是相对路径，但历史脏数据可能带 ../，必须再确认一次落在根目录内
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.FORBIDDEN, "非法的文件路径");
        }
        if (!Files.exists(target)) {
            log.warn("稿件文件缺失 id={} path={}", deliverableId, target);
            throw new BizException(ErrorCode.DELIVERABLE_FILE_MISSING);
        }
        return new DownloadFile(target, row.getFileName());
    }

    // =====================================================================
    // 返工统计
    // =====================================================================

    /**
     * 按月的返工统计。
     *
     * <p>【为什么统计口径按"稿件上传时间"而不是"返工发生时间"】
     * 两者会错位：9 月上传的稿子 10 月才被退回。如果版本数按 9 月算、返工数按 10 月算，
     * 10 月就会出现"返工 5 次、上传 0 版"的诡异数字。
     * 统一按稿件归属月，宁可少算跨月的那部分，也要保证同一张表里数字自洽。
     */
    public ReworkStatVO stats(String period) {
        YearMonth ym = parsePeriod(period);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();

        Map<Long, ReworkCountRow> reworkMap = new LinkedHashMap<>();
        for (ReworkCountRow r : reworkLogMapper.reworkCountByEmployee(from, to)) {
            reworkMap.put(r.getEmployeeId(), r);
        }

        ReworkStatVO vo = new ReworkStatVO();
        vo.setPeriod(ym.toString());

        List<ReworkStatVO.PersonStat> persons = new ArrayList<>();
        int totalVersions = 0;
        int totalRejected = 0;
        for (ReworkStatRow row : reworkLogMapper.statByEmployee(from, to)) {
            ReworkStatVO.PersonStat ps = new ReworkStatVO.PersonStat();
            ps.setEmployeeId(row.getEmployeeId());
            ps.setEmployeeName(row.getEmployeeName() == null ? "未知" : row.getEmployeeName());
            ps.setVersions(row.getVersions());
            ps.setRejected(row.getRejected());
            ReworkCountRow rc = reworkMap.get(row.getEmployeeId());
            ps.setReworks(rc == null ? 0 : rc.getReworks());
            ps.setPassRate(passRate(row.getVersions(), row.getRejected()));
            persons.add(ps);
            totalVersions += row.getVersions();
            totalRejected += row.getRejected();
        }

        vo.setPersons(persons);
        vo.setTotalVersions(totalVersions);
        vo.setRejectedVersions(totalRejected);
        vo.setReworkCount(reworkLogMapper.countReworks(from, to));
        vo.setPassRate(passRate(totalVersions, totalRejected));
        return vo;
    }

    private static BigDecimal passRate(int versions, int rejected) {
        if (versions <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(versions - rejected)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(versions), 1, RoundingMode.HALF_UP);
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private SalesOrder requireOrder(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getDeleted() != null && order.getDeleted() == 1)) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /**
     * 能不能看这单的稿件。
     *
     * <p>和订单详情用同一套规则：设计师必须是本单参与者，管理岗不受限。
     * 稿件比金额更敏感（未定稿的方案本身就有价值），所以这里不能只靠"登录了"。
     */
    private void assertVisible(Long orderId, CurrentUser user) {
        if (user.scope().covers(ScopeType.TEAM)) {
            return;
        }
        boolean participated = assigneeMapper.selectByOrder(orderId).stream()
                .map(AssigneeRow::employeeId)
                .anyMatch(id -> id != null && id.equals(user.employeeId()));
        if (!participated) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能查看自己参与的订单");
        }
    }

    private DeliverableVO toVO(DeliverableRow row, List<ReworkRow> reworks) {
        DeliverableVO vo = new DeliverableVO();
        vo.setId(row.id());
        vo.setOrderItemId(row.orderItemId());
        vo.setVersionNo(row.versionNo());
        vo.setVersionText("V" + row.versionNo());
        vo.setFileName(row.fileName());
        vo.setFileType(row.fileType());
        vo.setFileSize(row.fileSize());
        vo.setFileSizeText(sizeText(row.fileSize()));
        vo.setReviewResult(row.reviewResult());
        vo.setReviewResultText(ReviewResult.labelOf(row.reviewResult()));
        vo.setFeedback(row.feedback());
        vo.setUploadedBy(row.uploadedBy());
        vo.setUploadedByName(row.uploadedByName());
        vo.setReviewedByName(row.reviewedByName());
        vo.setReviewedAt(row.reviewedAt());
        vo.setCreatedAt(row.createdAt());

        List<DeliverableVO.ReworkVO> rvs = new ArrayList<>();
        if (reworks != null) {
            for (ReworkRow r : reworks) {
                DeliverableVO.ReworkVO rv = new DeliverableVO.ReworkVO();
                rv.setId(r.id());
                rv.setReason(r.reason());
                rv.setReworkByName(r.reworkByName());
                rv.setCreatedAt(r.createdAt());
                rvs.add(rv);
            }
        }
        vo.setReworks(rvs);
        return vo;
    }

    private Path rootDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 清洗原始文件名：只留最后一段，去掉路径分隔符与 ..。
     *
     * <p>不清理的话，Chrome 老版本会把完整路径当文件名传上来，
     * 一旦拿去拼路径就变成目录穿越（../../xxx）。
     */
    private static String safeFileName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未命名";
        }
        String name = raw.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replace("..", "").trim();
        return name.isEmpty() ? "未命名" : name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sizeText(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static YearMonth parsePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PERIOD_INVALID);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理上传残留文件失败 path={}", path);
        }
    }

    /** 下载文件三要素：磁盘路径、给用户看的文件名 */
    public record DownloadFile(Path path, String fileName) {
    }
}
