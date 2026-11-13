package com.studio.erp.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.SimpleColumnWidthStyleStrategy;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 统一把数据写成 xlsx 并推给浏览器。
 *
 * <p>【为什么不返回文件地址、让前端去下载】
 * 内部系统没有对象存储，落在磁盘上的临时文件要么没人清理（几个月后磁盘告警），
 * 要么被定时任务删掉（用户点开是 404）。直接写响应流，数据不落地。
 *
 * <p>【中文文件名必须编码两次】
 * HTTP 头只能是 ASCII。只写 filename="工资表.xlsx"，浏览器拿到的是乱码或直接丢弃；
 * 必须同时给 filename（URL 编码，兼容老浏览器）和 filename*（RFC 5987，现代浏览器优先用）。
 * 编码后空格会变成 "+"，而 "+" 在 URL 里表示空格之外的含义，所以要换回 %20。
 *
 * <p>【为什么用 dataFormat=4 而不是每个金额字段自己拼字符串】
 * 拼成 "1,234.50" 是文本，Excel 里不能求和、不能排序，用户第一件事就是把它转成数字。
 * 让单元格保持数值 + 数字格式，才是真正的"能用的表格"。
 */
@Slf4j
public final class ExcelWriter {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelWriter() {
    }

    public static <T> void write(HttpServletResponse response, String filename,
                                 String sheetName, Class<T> type, List<T> rows) {
        try {
            response.reset();
            response.setContentType(CONTENT_TYPE);
            response.setCharacterEncoding("UTF-8");
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            // 导出内容含工资、客户金额，不允许任何中间层缓存
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");

            EasyExcel.write(response.getOutputStream(), type)
                    // 统一列宽，默认宽度会让"客户名称"这类长文本被截断成 ####
                    .registerWriteHandler(new SimpleColumnWidthStyleStrategy(18))
                    .sheet(sheetName)
                    .doWrite(rows == null ? List.of() : rows);
        } catch (IOException | RuntimeException e) {
            // 走到这里通常响应已经开始写了，再想返回 JSON 错误也来不及，
            // 只能记日志让运维看到；前端会表现为"下载了一个打不开的文件"
            log.error("导出 Excel 失败 filename={} rows={}", filename, rows == null ? 0 : rows.size(), e);
            throw new BizException(ErrorCode.EXPORT_FAILED);
        }
    }
}
