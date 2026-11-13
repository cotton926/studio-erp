package com.studio.erp.common.api;

import java.util.List;

/**
 * 统一分页结构。
 *
 * <p>【为什么不直接返回 MyBatis-Plus 的 IPage】
 * IPage 实现类里带着 searchCount、optimizeJoin、orders 这些内部字段，
 * 把框架对象直接吐给前端有三个坏处：
 * 1) 页面上会看到一堆跟业务无关的字段，改动框架版本还可能悄悄改变响应结构；
 * 2) 字段名（records/current/size）是框架方言，前端要跟着框架走；
 * 3) 哪天换成自己写的分页或换 ORM，前端全部要改。
 * 这里固定成 pageNum/pageSize/total/list 四个字段，前后端约定一次，之后不再变。
 *
 * @param pageNum  当前页，从 1 开始
 * @param pageSize 每页条数
 * @param total    总条数（前端据此渲染页码，不要用 list.size() 判断有没有下一页）
 * @param list     当前页数据
 */
public record PageResult<T>(long pageNum, long pageSize, long total, List<T> list) {

    public static <T> PageResult<T> of(long pageNum, long pageSize, long total, List<T> list) {
        return new PageResult<>(pageNum, pageSize, total, list == null ? List.of() : list);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(pageNum, pageSize, 0, List.of());
    }
}
