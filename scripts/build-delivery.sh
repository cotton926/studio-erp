#!/usr/bin/env bash
# ============================================================
# 一键生成交付包
#   用法：bash scripts/build-delivery.sh [版本号]
#   默认 v2.2，产物 delivery/studio-erp-<版本>.zip
# 前置：JDK 17（JAVA_HOME）、Maven、Node.js、python
# 流程：前端构建 → 打成单 jar（含页面）→ 组装交付目录 → 校验 → 压缩
#
# 模板在 delivery/_templates/：
#   *.bat.utf8 是脚本的 UTF-8 源文件，构建时转成 GBK 的 .bat
#   （cmd 解析 UTF-8 批处理会截断中文行，必须存 GBK，勿直接改包内 .bat）
# ============================================================
set -euo pipefail

VERSION="${1:-v2.2}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TPL="$ROOT/delivery/_templates"
OUT="$ROOT/delivery/studio-erp-$VERSION"
JAR="studio-erp-1.0.0.jar"

echo "[1/5] 构建前端"
cd "$ROOT/web" && npm run build

echo "[2/5] 打包 jar（含前端静态资源）"
cd "$ROOT"
mvn -o -Pwith-web package

echo "[3/5] 组装交付目录：delivery/studio-erp-$VERSION"
rm -rf "$OUT"
mkdir -p "$OUT"/{app,sql,docs,data/uploads,logs}
cp "$ROOT/target/$JAR" "$OUT/app/"
cp "$ROOT/docs/接口清单.md" "$OUT/docs/"
cp "$ROOT/../平面设计公司管理系统-设计方案.md" "$OUT/docs/" 2>/dev/null || true
cp "$TPL/README.txt" "$TPL/config.env.example" "$OUT/"
cp "$TPL/部署手册.md" "$OUT/docs/"
cp "$TPL/01_create_database.sql" "$OUT/sql/"
touch "$OUT/logs/.gitkeep" "$OUT/data/uploads/.gitkeep"

# bat 转 GBK（\r\n 换行）
python - "$TPL" "$OUT" << 'PY'
import io, os, sys
tpl, out = sys.argv[1], sys.argv[2]
for name in ("start", "stop"):
    txt = io.open(os.path.join(tpl, name + ".bat.utf8"), encoding="utf-8").read()
    data = txt.replace("\r\n", "\n").replace("\n", "\r\n").encode("gbk")
    with open(os.path.join(out, name + ".bat"), "wb") as f:
        f.write(data)
print("     start.bat / stop.bat 已按 GBK 输出")
PY

echo "[4/5] 校验产物"
unzip -l "$OUT/app/$JAR" | grep -q "BOOT-INF/classes/static/index.html" \
    || { echo "错误：jar 内没有前端页面，检查 with-web profile"; exit 1; }
grep -q "NoLoggingImpl" <(unzip -p "$OUT/app/$JAR" BOOT-INF/classes/application-prod.yml) \
    || { echo "错误：生产配置未生效"; exit 1; }
echo "     jar 内含前端页面 ✓  大小 $(du -h "$OUT/app/$JAR" | cut -f1)"

echo "[5/5] 压缩"
cd "$ROOT/delivery"
rm -f "studio-erp-$VERSION.zip"
python - "studio-erp-$VERSION" "studio-erp-$VERSION.zip" << 'PY'
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for base, dirs, files in os.walk(src):
        for d in dirs:
            full = os.path.join(base, d)
            z.write(full, os.path.relpath(full, ".") + "/")
        for f in files:
            full = os.path.join(base, f)
            z.write(full, os.path.relpath(full, "."))
print("     zip 生成：", dst, round(os.path.getsize(dst) / 1048576, 1), "MB")
PY

echo "完成：delivery/studio-erp-$VERSION.zip"
