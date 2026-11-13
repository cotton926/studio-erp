package com.studio.erp.order.controller;

import com.studio.erp.auth.DataScope;
import com.studio.erp.auth.RequireRole;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.api.R;
import com.studio.erp.order.dto.OrderCreateRequest;
import com.studio.erp.order.dto.OrderDetailVO;
import com.studio.erp.order.dto.OrderListVO;
import com.studio.erp.order.dto.OrderPaymentRequest;
import com.studio.erp.order.dto.OrderPricingRequest;
import com.studio.erp.order.dto.OrderQuery;
import com.studio.erp.order.service.OrderQueryService;
import com.studio.erp.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口。
 *
 * <p>【角色分工与需求的对应关系】
 * <pre>
 * 提单     客服 / 主管   —— 客服跟客户谈好内容，价格由主管录（本接口不含金额字段）
 * 定价     主管 / 管理员 —— 录明细单价、指定设计师与分成比例，确认后订单生效
 * 推进     主管 / 管理员 —— 开始执行、交付
 * 登记回款 财务 / 管理员 —— 到账即触发业绩确认
 * 查看     全部人         —— 设计师只能看到自己参与的单，且看不到金额
 * </pre>
 *
 * <p>写在 Controller 上而不是 Service 上：Service 是可被其他 Service 复用的，
 * 把"谁能调"绑进业务逻辑，日后写个定时任务调用同一个方法就会被权限拦住。
 * 权限属于"入口"的概念，应该留在这一层。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queryService;

    /**
     * 客服提单。
     *
     * @return 新订单 id（前端拿到后跳转到详情，等主管定价）
     */
    @PostMapping
    @RequireRole({"CS", "SUPERVISOR", "ADMIN"})
    public R<Long> create(@Valid @RequestBody OrderCreateRequest request) {
        return R.ok(orderService.create(request));
    }

    /**
     * 主管定价并确认订单。
     *
     * <p>整单提交：明细、分成比例、总额、签单日期一次落库。
     * 重复调用是安全的 —— 状态 CAS 会让第二次提交收到"订单已被他人修改"。
     */
    @PutMapping("/{id}/pricing")
    @RequireRole({"SUPERVISOR", "ADMIN"})
    public R<Void> price(@PathVariable Long id, @Valid @RequestBody OrderPricingRequest request) {
        orderService.price(id, request);
        return R.ok();
    }

    /**
     * 推进执行状态。
     *
     * @param status 目标状态码：3执行中 4已交付 9已取消
     */
    @PutMapping("/{id}/status")
    @RequireRole({"SUPERVISOR", "ADMIN"})
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam int status) {
        orderService.changeStatus(id, status);
        return R.ok();
    }

    /**
     * 登记回款并核销到本单。
     *
     * @return 核销记录 id（业绩流水的来源凭证，可用于追溯）
     */
    @PostMapping("/{id}/payments")
    @RequireRole({"FINANCE", "ADMIN", "SUPERVISOR"})
    public R<Long> registerPayment(@PathVariable Long id,
                                   @Valid @RequestBody OrderPaymentRequest request) {
        return R.ok(orderService.registerPayment(id, request));
    }

    /** 订单列表：设计师自动只看自己参与的单 */
    @GetMapping
    @DataScope(ScopeType.SELF)
    public R<PageResult<OrderListVO>> page(OrderQuery query) {
        return R.ok(queryService.page(query));
    }

    /** 订单详情 */
    @GetMapping("/{id}")
    @DataScope(ScopeType.SELF)
    public R<OrderDetailVO> detail(@PathVariable Long id) {
        return R.ok(queryService.detail(id));
    }

    /**
     * 待定价订单（主管首页的待办）。
     *
     * <p>单独一个接口而不是让前端传 status=1：待定价是主管每天必看的一栏，
     * 给它一个稳定语义的路径，比让前端记状态码更不容易出错。
     */
    @GetMapping("/pending-pricing")
    @RequireRole({"SUPERVISOR", "ADMIN"})
    public R<List<OrderListVO>> pendingPricing() {
        OrderQuery query = new OrderQuery();
        query.setStatus(com.studio.erp.common.enums.OrderStatus.PENDING_PRICE.getCode());
        query.setPageSize(50);
        return R.ok(queryService.page(query).list());
    }
}
