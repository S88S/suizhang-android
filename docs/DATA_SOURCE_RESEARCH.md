# 数据源调研结论：美股分红接口

> 调研日期：2026-10-11　所有端点均由本机实测，非引用二手资料。

## 一、为什么美股分红一直做不出来

现有 A股/港股分红走东方财富（`RPT_SHAREBONUS_DET`）。**东方财富没有美股分红接口**——实测各路径均 404 或「股票代码不合法」；新浪美股分红页表格为空。所以这套方案无法平移到美股，必须另找数据源。

而最初尝试的 Yahoo Finance **在中国大陆被硬性封锁**，实测带完整浏览器 UA 返回 403，正文明确写着：

> As of November 1st, 2021 Yahoo's suite of services will no longer be accessible from mainland China.

拦截发生在 IP 地理层，业界流传的 cookie + crumb 绕过流程因此无效（`fc.yahoo.com` 能拿到 A3 cookie，但 `getcrumb` 返回的仍是同一张 403 拦截页）。**不要再尝试 Yahoo Finance。**

## 二、推荐方案：stockanalysis.com JSON 接口

```
股票（NYSE + Nasdaq + ADR + 中概）  https://stockanalysis.com/api/symbol/s/{TICKER}/dividend
ETF（路径前缀不同！）               https://stockanalysis.com/api/symbol/e/{TICKER}/dividend
```

**实测优势**：大陆直连可用；无需 API key；无需 cookie/crumb；**连 User-Agent 都不需要**（空 UA 也能 200）；连续 10 次请求 10/10 成功，无限流；单次请求同时返回股息率、年度股息、除息日与近 20 期派息历史。

### 返回结构与字段映射

```json
{
  "status": 200,
  "data": {
    "infoTable": { "yield": "2.41%", "annual": "$2.12", "exdiv": "Sep 15, 2026",
                   "frequency": "Quarterly", "payoutRatio": "63.71%", "years": "64" },
    "history": [ { "dt": "2026-09-15", "amt": "$0.530",
                   "dec": "n/a", "record": "2026-09-15", "pay": "2026-10-01" } ]
  }
}
```

| 业务字段 | 来源 | 解析注意 |
|---|---|---|
| 除息日 ex-date | `history[].dt` | ISO `yyyy-MM-dd`，可直接用 |
| 每股股息 | `history[].amt` | `"$0.530"` → 需 strip `$` |
| 派息日 / 登记日 | `pay` / `record` | ISO 日期 |
| 宣布日 | `dec` | 多数为 `"n/a"`，必须容错 |
| 股息率 | `infoTable.yield` | `"2.41%"` → 需 strip `%` |
| 年度股息 | `infoTable.annual` | `"$2.12"` |

### 已实测的边界行为（这些是最容易踩坑的地方）

| 情形 | 实测结果 | 必须这样处理 |
|---|---|---|
| 不分红标的（TSLA、PDD） | HTTP 200 + `yield="n/a"` + `history` 为空 | 按「无分红」处理，**不是异常** |
| 代码不存在 | HTTP 404 + `status=404` | 提示代码无效 |
| ETF（如 SCHD） | 走 `/e/` 路径，HTTP 200 | 路径前缀与股票不同，易错 |
| ADR（NVO，Semi-Annual） | 正常，10 条 | frequency 字段需读 |

> **HTTP 200 不等于有数据**，必须判 `status==200 && !history.isEmpty()`。

### 已知限制

- `history` **硬上限 20 条**（约 5 年季度分红）。实测 `?limit=100`、`?period=full`、`?range=max` 全部无效。**无法回溯 20 年以上分红史**，对记账够用但不能做长周期回溯分析。
- 第三方站点，建议加 1~2 秒节流；实测响应 1~2.4 秒，超时设 10s。

## 三、备选：Nasdaq 官方接口（仅作 Nasdaq 上市股的长历史补充）

```
GET https://api.nasdaq.com/api/quote/{TICKER}/dividends?assetclass=stocks
```

- **必须带完整浏览器 User-Agent**，空 UA 或 `okhttp/4.9.0` 直接连接失败（HTTP 000）
- 返回 `data.dividends.rows[]`，日期格式 `MM/dd/yyyy`（与 stockanalysis 的 ISO 不同，需分别解析）

**致命限制：只覆盖 Nasdaq 上市股票。** 实测 KO / JNJ / MCD / JPM / XOM 全部返回 HTTP 200 但数据为空（`"Dividend History for Non-Nasdaq symbols is not available"`），而这几只恰是美股分红主力票（均为 NYSE）。经 `/api/quote/{TICKER}/info` 确认原因为 `"isNasdaqListed":false`。

> **陷阱**：该接口 HTTP 状态是 200 但数据为空，极易误判为成功。**不能单独使用**，仅适合给 Nasdaq 上市票补长历史（AAPL 83 条、INTC 88 条、PEP 92 条）。

## 四、已实测失效，不要再试

| 方案 | 实测结果 |
|---|---|
| Yahoo Finance 全端点 | 403 地域封锁（2021-11-01 起），cookie+crumb 无效 |
| Alpha Vantage / FMP | 空响应，**必须 API key** |
| Stooq | 返回 JS 质询挑战页（SHA-256 PoW 反爬），且无分红数据 |
| MarketWatch | HTTP 401 |
| WSJ | HTTP 000 连接失败 |
| 东方财富美股分红 F10 | 全部 404 / 代码不合法 |
| 新浪美股分红页 | 表格为空 |
| stockanalysis 网页 HTML | 能通但需解析 HTML，不如直接用其 JSON 接口 |

## 五、可参考的开源项目

| 仓库 | 参考价值 |
|---|---|
| [OpenBB-finance/OpenBB](https://github.com/OpenBB-finance/OpenBB) | `openbb_nasdaq` provider 封装了 Nasdaq 分红端点，是该端点的权威参考实现 |
| [hsteffens/dividend_analysis](https://github.com/hsteffens/dividend_analysis) | 直接抓 Nasdaq 全美股分红，含 0.5s 节流实现，可参考其限流做法 |
| [makkader/pynasdaq](https://github.com/makkader/pynasdaq) | 早期 Nasdaq 抓取库，端点已过时，仅作历史参考 |
| [ranaroussi/yfinance](https://github.com/ranaroussi/yfinance) | Yahoo 封装，**大陆不可用**，不要参考 |

## 六、落地建议（若将来实现）

1. 新增 `UsDividendSource.java`，职责**仅限分红**；报价继续走现有腾讯/东财接口，不要合并
2. 解析 `infoTable` 与 `history[]`，字段全是带符号字符串（`"$0.530"`、`"2.41%"`），`parseDouble` 前必须 strip
3. `"n/a"` / `""` / `null` 是常态（尤其 `dec`），务必容错
4. 判断成功用 `status==200 && !history.isEmpty()`，不能只看 HTTP 状态
5. 不需要 cookie、crumb、代理、签名任何机制

## 东财美股行情（现有能力补充确认）

东财**行情**可用（**分红不可用**），这解释了为何 A股/港股方案无法平移：

```java
// 报价：105=Nasdaq, 106=NYSE（实测 KO/JNJ/PG/XOM 106 均返回正确中文名与价格）
"https://push2.eastmoney.com/api/qt/stock/get?secid=106.KO&fields=f43,f57,f58,f84,f85"
// K线：push2his.eastmoney.com/api/qt/stock/kline/get?secid=106.KO&klt=101&fqt=0|1
// 搜索：searchapi.eastmoney.com/api/suggest/get?input=AAPL&type=14&token=D43BF722C8E33BDC906FB84D85E326E8
//      → 返回 "QuoteID":"105.AAPL" 可反查 secid
```

另有一条**推导兜底**思路：对比 `fqt=0`（不复权）与 `fqt=1`（前复权）同一天 K 线价差可近似每股分红（KO 实测 4/1 原始 76.150 vs 前复权 75.090，差 1.06 ≈ 两次派息 0.53×2）。但这是**推导值、精度受多次派息累积影响，仅适合校验兜底，不建议做主源**。