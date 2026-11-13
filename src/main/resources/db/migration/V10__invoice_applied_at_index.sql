-- 开票台账按申请月份筛选走的是 applied_at，但没有索引时只能全表扫。
-- 台账页和导出都带 period 条件，是高频查询路径。
ALTER TABLE invoice ADD INDEX idx_applied_at (applied_at);
