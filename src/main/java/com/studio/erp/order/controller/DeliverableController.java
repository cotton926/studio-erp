package com.studio.erp.order.controller;

import com.studio.erp.auth.DataScope;
import com.studio.erp.auth.RequireRole;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.common.api.R;
import com.studio.erp.order.dto.DeliverableVO;
import com.studio.erp.order.dto.ReworkStatVO;
import com.studio.erp.order.dto.ReviewRequest;
import com.studio.erp.order.service.DeliverableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 稿件版本与送审接口。
 *
 * <p>【为什么审核权限给客服，上传只给设计师和主管】
 * 图是设计师画的（上传），客户的意见是客服/主管听到的（审核结果）。
 * 让设计师自己录"客户已通过"，等于让他给自己发通行证 —— 这条线必须分开。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeliverableController {

    private final DeliverableService deliverableService;

    /** 某单的稿件版本列表 */
    @GetMapping("/orders/{orderId}/deliverables")
    @DataScope(ScopeType.SELF)
    public R<List<DeliverableVO>> list(@PathVariable Long orderId) {
        return R.ok(deliverableService.list(orderId));
    }

    /** 上传新版本（版本号由服务端递增） */
    @PostMapping("/orders/{orderId}/deliverables")
    @RequireRole({"DESIGNER", "SUPERVISOR", "ADMIN"})
    @DataScope(ScopeType.SELF)
    public R<Long> upload(@PathVariable Long orderId,
                          @RequestParam(value = "orderItemId", required = false) Long orderItemId,
                          @RequestParam("file") MultipartFile file) {
        return R.ok(deliverableService.upload(orderId, orderItemId, file));
    }

    /** 录客户反馈：通过 / 退回（退回必填原因，并自动生成返工记录） */
    @PutMapping("/deliverables/{id}/review")
    @RequireRole({"CS", "SUPERVISOR", "ADMIN"})
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        deliverableService.review(id, request);
        return R.ok();
    }

    /**
     * 下载稿件。
     *
     * <p>返回二进制而不是 JSON，所以不套 {@code R<T>}。
     * 强制 attachment：pdf/svg 这类能被浏览器渲染的格式若内联打开，
     * 恶意脚本会在系统域下执行。正常下载不会触发任何解析。
     */
    @GetMapping("/deliverables/{id}/file")
    @DataScope(ScopeType.SELF)
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DeliverableService.DownloadFile file = deliverableService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file.path()));
    }

    /** 月度返工统计（主管视角：看谁的一版通过率低、哪类单最费改） */
    @GetMapping("/deliverables/stats")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public R<ReworkStatVO> stats(@RequestParam(required = false) String period) {
        return R.ok(deliverableService.stats(period));
    }
}
