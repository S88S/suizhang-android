#!/usr/bin/env python3
"""Guards for the defensive fixes found in the 1.4.9 retrospective.

Each check below corresponds to a concrete failure mode that was reachable from shipped code:
a render throw crashing the app, an out-of-range tax rate read from a hand-edited Excel settings
sheet, a cache written from two threads, and half-committed dividend writes. These are source
assertions, so they prove the guard is present, not that the behaviour was observed on a device.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
worker = (root / "app/src/main/java/cn/suizhang/ledger/DividendReminderWorker.java").read_text(encoding="utf-8")
milestones = (root / "app/src/main/java/cn/suizhang/ledger/DividendMilestones.java").read_text(encoding="utf-8")


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS  " + label)


# --- render must not be able to take the app down ---
require("render 自身负责兜底，不再把异常抛给调用方",
        "private void render() {" in activity and "catch (Exception e) { Log.w(\"Suizhang\", \"render failed\"" in activity)
require("渲染主体拆到 renderPage，保持 render 为受保护入口",
        "private void renderPage() {" in activity)

# --- a hand-edited Excel settings sheet must not be able to crash on every render ---
require("A 股三档税率先钳制再传入，避免参数校验直接抛异常",
        "clampRate(settingDouble(\"tax_a_short\", .2))" in activity
        and "clampRate(settingDouble(\"tax_a_mid\", .1))" in activity
        and "clampRate(settingDouble(\"tax_a_long\", 0))" in activity)
require("复利页的再投资比例与税率同样先钳制",
        "clampRate(settingDouble(\"drip_reinvest\", 1))" in activity
        and "clampRate(settingDouble(\"drip_tax\", .10))" in activity)

# --- restore path: OutOfMemoryError is an Error and used to escape catch (Exception) ---
require("恢复备份时单独捕获内存溢出，把闪退变成提示",
        "catch (OutOfMemoryError tooLarge)" in activity)

# --- thread safety ---
require("期初持仓缓存改为并发容器，不再被 UI 线程与后台线程同时写",
        "new ConcurrentHashMap<>()" in database and "import java.util.concurrent.ConcurrentHashMap;" in database)
require("销毁时先优雅停机再关库，避免后台任务拿着失效 Cursor",
        "dataExecutor.shutdown()" in activity and "awaitTermination" in activity)

# --- atomicity ---
mark_received = database[database.index("void markDividendReceived("):database.index("int markReceivedBatch(")]
add_dividend = database[database.index("long addDividend("):database.index("boolean addPublicDividend(")]
require("标记到账与其成本摊薄进入同一事务",
        "beginTransaction()" in mark_received and "setTransactionSuccessful()" in mark_received)
require("新增分红与其成本摊薄进入同一事务",
        "beginTransaction()" in add_dividend and "setTransactionSuccessful()" in add_dividend)

# --- null and format hardening ---
require("设置读取对 NULL 值回退到默认值",
        "return value == null ? fallback : value;" in database)
require("百分比与金额对非有限值显示破折号，不输出 NaN",
        'Double.isFinite(rate) ? moneyFormat.format(rate * 100d) + "%" : "—"' in activity
        and "Double.isFinite(value) ? moneyFormat.format(value) : \"—\"" in activity)
require("金额格式显式采用四舍五入，不用默认的银行家舍入",
        "setRoundingMode(RoundingMode.HALF_UP)" in activity)

# --- quote availability ---
require("行情缺失按原因分流，未填代码与不支持市场不再混为一句",
        "private String quoteUnavailableReason(JSONObject h)" in activity
        and "该市场暂不支持行情查询" in activity)
require("刷新失败的原因保留在持仓页并可重试",
        "quoteWarning" in activity and "quoteRefreshInFlight) { toast(\"行情正在刷新，请稍候\")" in activity)

# --- form validation ---
require("持仓表单逐字段报错，不再把成本/派息/税率错误挂在数量框",
        'cost.setError("请填写有效成本")' in activity
        and 'annualDiv.setError("每份年分红不能为负")' in activity
        and 'taxRate.setError("税率需在 0–100% 之间")' in activity)
require("交易表单逐字段报错，单价与手续费各自提示",
        'price.setError("成交单价不能为负")' in activity and 'fees.setError("手续费不能为负")' in activity)
require("提供字段级数值校验，避免半成品输入被静默读成 0",
        "private boolean hasNumber(EditText e)" in activity)

# --- empty states ---
require("空态给出下一步动作，不再只有一行灰字",
        'emptyCard("还没有支出目标"' in activity and 'emptyCard("还没有持仓"' in activity)
require("账户列表为空时也有说明",
        "还没有账户。新建一个可以把不同券商或不同用途的持仓分开记账。" in activity)

# --- restore must not keep the raw bytes, a decoded String and the parsed tree alive at once ---
require("恢复路径在生成文本后立即释放原始字节数组，避免三份数据同时在堆上",
        "file = null;" in activity and "new String(file, StandardCharsets.UTF_8)" in activity)
require("备份树在弹窗前解析完成，坏文件在读文件阶段就失败而不是点了确认才失败",
        "final JSONObject parsed = new JSONObject(json);" in activity and "json = null;" in activity)
require("确认弹窗持有已解析的备份树而不是原始文本",
        "db.importJson(parsed)" in activity and "final JSONObject parsed" in activity)
require("数据库提供接收已解析备份树的重载，非空即报错",
        "void importJson(JSONObject root) throws JSONException" in database
        and 'throw new JSONException("备份为空")' in database)
require("旧的字符串入口保留长度上限后转为复用新重载",
        'json.length() > 10_000_000' in database
        and "importJson(new JSONObject(json));" in database)

# --- reminder rules are now testable off-device ---
require("提醒窗口规则抽出为不依赖 Android 的纯类",
        "final class DividendMilestones" in milestones
        and "import android." not in milestones
        and "import org.json." not in milestones)
require("纯规则只接收字符串与日期，避免在 android.jar 桩下无法测试",
        "static String milestoneOf(String recordDate, String exDate, String payDate, LocalDate today)" in milestones)
require("提醒 Worker 改为委托纯规则，自己不重复实现窗口判断",
        "DividendMilestones.milestoneOf(" in worker
        and "ChronoUnit" not in worker)

print("通过：防御性修复与可打磨项回归；源码断言不替代 Android 设备验证。")
