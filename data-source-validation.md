# 穗账 1.3.0 公共接口抽样验证记录

**验证时间：** 2026-10-03（UTC+8）；测试仅发送公开证券代码（600519、00700.HK、AAPL）或公开搜索词，不发送账户、数量、成本或交易信息。

> 这些抽样响应不构成稳定性 SLA、数据完整性或再分发授权确认。生产实现设置 8 秒连接/读取超时，并在本机缓存；请求失败时显示缓存/未同步，不阻断账本。

## 已观察结果

- [东方财富 A 股分红报表](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_SHAREBONUS_DET&columns=SECURITY_CODE%2CSECURITY_NAME_ABBR%2CREPORT_DATE%2CPRETAX_BONUS_RMB%2CEQUITY_RECORD_DATE%2CEX_DIVIDEND_DATE%2CPLAN_NOTICE_DATE%2CASSIGN_PROGRESS&filter=(SECURITY_CODE%3D%22600519%22)&pageNumber=1&pageSize=2&sortColumns=NOTICE_DATE&sortTypes=-1&source=WEB&client=WEB)：HTTPS 200，`text/plain;charset=UTF-8`，返回 JSON；贵州茅台样例字段 `PRETAX_BONUS_RMB=280.2423`，登记日 2026-06-25、除息日 2026-06-26、状态“实施分配”。实现按每 10 股除以 10 转成每股 CNY；接口无派息日，界面标“约”，以除息日 +1 天推算。
- [东方财富港股分红报表](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_HKF10_INFO_DIVIDEND&columns=SECURITY_CODE%2CSECUCODE%2CPLAN_EXPLAIN%2CRECORD_DATE%2CEX_DIVIDEND_DATE%2CDIVIDEND_DATE%2CASSIGN_PROGRESS&filter=(SECUCODE%3D%2200700.HK%22)&pageNumber=1&pageSize=2&source=HSF10&client=PC)：HTTPS 200，返回 JSON；样例 `PLAN_EXPLAIN=每股派港币5.3元`、登记/除息/派息日期可读。请求不含 `sortColumns`，按代码侧排序/展示；港币按每股金额解析，不除以 10。
- [腾讯行情 GBK](https://qt.gtimg.cn/q=sh600519%2Chk00700%2CusAAPL)：HTTPS 200，响应头 `text/html; charset=GBK`。用 GBK 解码后可读取 `v_sh600519` 等 `~` 分隔记录；A 股样例索引 `[1]` 名称、`[3]` 现价、`[32]` 涨跌幅（已是百分数）。App 只接 A/H 代码；价格按约 15 分钟本地缓存，涨红跌绿。
- [东方财富证券搜索（无 token）](https://searchapi.eastmoney.com/api/suggest/get?input=600519&type=14&count=3)：HTTPS 200，`application/json`，样例结构为 `QuotationCodeTable.Data`，含 `Code`、`Name`、`Classify=AStock`。搜索响应可变；实现做结构校验、缓存回退，并提醒用户核对，不能匹配时仍可离线/手动录入。
- [汇率接口样例](https://open.er-api.com/v6/latest/USD)：HTTPS 200，JSON `result=success`、provider 为 exchangerate-api；样例包含 CNY 与 HKD 报价。App 本轮没有做币种汇总/换算，避免把未进一步验证的汇率策略当作已实现功能。

## 美股分红端点限制

- [东方财富美股分红报表（抽样）](https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_USF10_INFO_DIVIDEND&columns=SECURITY_CODE%2CPLAN_EXPLAIN%2CEX_DIVIDEND_DATE%2CBONUS_PAY_DATE%2CASSIGN_PROGRESS&filter=(SECURITY_CODE%3D%22AAPL%22)&pageNumber=1&pageSize=1&source=HSF10&client=PC)：第一次调用读取超时；随后省略“排序字段”重试时返回 HTTP 200，但正文 `success=false`、错误消息 `ASSIGN_PROGRESS返回字段不存在`。因此美股分红同步未接入；不能根据该接口列表宣称稳定可用。解析单元测试只验证“若出现明确 USD 每股方案文案时”的金额解析，不表示线上源可用。

## 授权/使用边界

东方财富、腾讯与 exchangerate-api 的公开端点是否允许本 App 长期直连、缓存、展示及再分发，抽样请求无法确认。App 已在界面和源码说明里披露供应方/失效可能性，未声称实时、权威或有官方授权；若需对外商业发行，应另行取得服务商的许可/稳定接口。

- 再次将美股分红请求列字段缩减为 `SECURITY_CODE,PLAN_EXPLAIN,EX_DIVIDEND_DATE,BONUS_PAY_DATE`、去掉 `ASSIGN_PROGRESS` 后，读取仍在 10 秒超时。因此仍不在客户端接入美股自动分红。

## 1.3.3 持仓分红预测说明

1.3.3 未增加新的分红 API 供应商或请求字段，继续复用本文中已抽样的东方财富公开 A 股/港股分红报表接口。持仓表单选中标的后会按市场与证券代码读取数据；网络失败时使用本机旧缓存并标注“旧缓存”，无缓存/无完整年度记录时显示无法估算并允许手工填写。默认按最近 3 个完整报告年度取每股平均，可切换 1/3/5 年；每年度内多笔分红先累加，计算所得写入既有 `annual_dividend_per_unit`，来源、窗口和更新时间写入已有 `app_settings` 表。

持仓数量及成本、账户、税务资料、截图和交易流水不发送给公开数据接口。只有证券代码/市场或用户主动提交的搜索词会用于公开查询。估算卡片与正式公告/已到账日历事件保持区分。美股自动分红仍未接入；接口数据稳定性、完整性、授权和商用许可均未因本次改动改变。预测仅为历史均值参考，不是收益保证、投资建议或税务意见。
