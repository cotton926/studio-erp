package com.studio.erp.order.dto;

import java.time.LocalDate;

/**
 * 订单列表查询条件。
 *
 * <p>【为什么把 pageNum / pageSize 放在这里而不是用 @RequestParam 逐个接收】
 * 查询条件会越来越多（客户、状态、日期区间、金额区间、设计师），
 * 逐个参数声明会让 Controller 方法签名长到没法读，而且每个 Controller 都要
 * 重复写一遍分页上限截断。收成一个对象之后，上限只在服务层处理一次。
 */
public class OrderQuery {

    /** 订单号 / 客户名称 / 项目名称 模糊匹配 */
    private String keyword;

    /** 执行状态：0草稿 1待定价 2已确认 3执行中 4已交付 9已取消 */
    private Integer status;

    /** 收款状态：0未收 1部分 2已结清 */
    private Integer paymentStatus;

    /** 签单日期起（含） */
    private LocalDate dateFrom;

    /** 签单日期止（含） */
    private LocalDate dateTo;

    private int pageNum = 1;

    private int pageSize = 20;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(Integer paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
