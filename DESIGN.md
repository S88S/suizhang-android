# 穗账 Android 设计指南

## 产品方向

穗账的主要任务是查看高股息持仓、分红现金流和后续收息安排。界面先回答“预计多少、来自哪些持仓、下一笔何时发生”，再呈现录入、设置和统计等辅助操作。产品不是新增的通用记账入口；四个稳定的顶层页面是**总览、持仓、日历、更多**，“规划”不作为底部导航项恢复。

这份文件是后续界面设计的单一、可读、可版本控制的依据。RICOUI DESIGN 关于本地优先、以 `DESIGN.md` 为可编辑源文件、由用户掌握内容的工作台方式是工作流参考，而非运行依赖：不因此添加云服务、远程字体、React 组件库或设计站点调用（[RICOUI DESIGN](https://design.ricoui.com/about)，[项目说明](https://github.com/ricocc/ricoui-design-md)）。下方令牌段采用 **YAML 兼容的 JSON**，可由工具抽取；界面实际颜色和尺寸以 Android `values/colors.xml`、`values-night/colors.xml`、`values/dimens.xml` 为准，改动时须同步维护并运行一致性测试。

## 可抽取的设计令牌

```yaml
{
  "schema": "suizhang.design.v1",
  "platform": "Android Java + HiMiuix + Material 3 controls",
  "colors": {
    "light": {
      "background": {
        "resource": "app_background",
        "value": "#F7F3E8"
      },
      "surface": {
        "resource": "app_surface",
        "value": "#FFFCF5"
      },
      "on_surface": {
        "resource": "app_on_surface",
        "value": "#2E271A"
      },
      "on_surface_variant": {
        "resource": "app_on_surface_variant",
        "value": "#675A40"
      },
      "primary": {
        "resource": "app_primary",
        "value": "#8A5E0B"
      },
      "on_primary": {
        "resource": "app_on_primary",
        "value": "#FFFFFF"
      },
      "primary_container": {
        "resource": "app_primary_container",
        "value": "#F1E3C2"
      },
      "on_primary_container": {
        "resource": "app_on_primary_container",
        "value": "#3C2B08"
      },
      "secondary": {
        "resource": "app_secondary",
        "value": "#8A5E0B"
      },
      "on_secondary": {
        "resource": "app_on_secondary",
        "value": "#FFFFFF"
      },
      "secondary_container": {
        "resource": "app_secondary_container",
        "value": "#F1E3C2"
      },
      "on_secondary_container": {
        "resource": "app_on_secondary_container",
        "value": "#3C2B08"
      },
      "error": {
        "resource": "app_error",
        "value": "#B3261E"
      },
      "outline": {
        "resource": "app_outline",
        "value": "#725F3C"
      },
      "outline_variant": {
        "resource": "app_outline_variant",
        "value": "#E0D3B6"
      },
      "hero_surface": {
        "resource": "app_hero_surface",
        "value": "#72500D"
      },
      "hero_on_surface": {
        "resource": "app_hero_on_surface",
        "value": "#FFF9EA"
      },
      "hero_muted": {
        "resource": "app_hero_muted",
        "value": "#F4DFAF"
      },
      "progress_track": {
        "resource": "app_progress_track",
        "value": "#E4D9BD"
      },
      "market_up": {
        "resource": "app_market_up",
        "value": "#B3261E"
      },
      "market_down": {
        "resource": "app_market_down",
        "value": "#176B45"
      },
      "dividend_emphasis": {
        "resource": "app_dividend_emphasis",
        "value": "#8A5E0B"
      },
      "dividend_on_hero": {
        "resource": "app_dividend_on_hero",
        "value": "#F4DFAF"
      },
      "calendar_record_date": {
        "resource": "app_calendar_record_date",
        "value": "#23698A"
      },
      "calendar_ex_date": {
        "resource": "app_calendar_ex_date",
        "value": "#B3261E"
      },
      "calendar_pay_date": {
        "resource": "app_calendar_pay_date",
        "value": "#176B45"
      },
      "calendar_received": {
        "resource": "app_calendar_received",
        "value": "#8A5E0B"
      },
      "calendar_estimated": {
        "resource": "app_calendar_estimated",
        "value": "#775613"
      },
      "miuix_theme": {
        "resource": "miuix_theme_color",
        "value": "#8A5E0B"
      },
      "miuix_background": {
        "resource": "miuix_basic_background_color",
        "value": "#F7F3E8"
      },
      "miuix_text": {
        "resource": "miuix_title_color",
        "value": "#2E271A"
      },
      "miuix_secondary_text": {
        "resource": "miuix_summary_color",
        "value": "#675A40"
      },
      "miuix_surface": {
        "resource": "miuix_default_surface_color",
        "value": "#FFFCF5"
      },
      "miuix_dialog_surface": {
        "resource": "miuix_dialog_background",
        "value": "#FFFCF5"
      },
      "miuix_selected_surface": {
        "resource": "miuix_item_choose_background",
        "value": "#F1E3C2"
      }
    },
    "dark": {
      "background": {
        "resource": "app_background",
        "value": "#17150F"
      },
      "surface": {
        "resource": "app_surface",
        "value": "#242117"
      },
      "on_surface": {
        "resource": "app_on_surface",
        "value": "#F3EDD9"
      },
      "on_surface_variant": {
        "resource": "app_on_surface_variant",
        "value": "#D1C6A8"
      },
      "primary": {
        "resource": "app_primary",
        "value": "#F0C45B"
      },
      "on_primary": {
        "resource": "app_on_primary",
        "value": "#3C2A04"
      },
      "primary_container": {
        "resource": "app_primary_container",
        "value": "#4A3715"
      },
      "on_primary_container": {
        "resource": "app_on_primary_container",
        "value": "#F6E8C1"
      },
      "secondary": {
        "resource": "app_secondary",
        "value": "#F0C45B"
      },
      "on_secondary": {
        "resource": "app_on_secondary",
        "value": "#3C2A04"
      },
      "secondary_container": {
        "resource": "app_secondary_container",
        "value": "#4A3715"
      },
      "on_secondary_container": {
        "resource": "app_on_secondary_container",
        "value": "#F6E8C1"
      },
      "error": {
        "resource": "app_error",
        "value": "#FFB4AB"
      },
      "outline": {
        "resource": "app_outline",
        "value": "#B9A77E"
      },
      "outline_variant": {
        "resource": "app_outline_variant",
        "value": "#554B33"
      },
      "hero_surface": {
        "resource": "app_hero_surface",
        "value": "#483512"
      },
      "hero_on_surface": {
        "resource": "app_hero_on_surface",
        "value": "#FFF8E6"
      },
      "hero_muted": {
        "resource": "app_hero_muted",
        "value": "#F2D98F"
      },
      "progress_track": {
        "resource": "app_progress_track",
        "value": "#4E4124"
      },
      "market_up": {
        "resource": "app_market_up",
        "value": "#FF8A80"
      },
      "market_down": {
        "resource": "app_market_down",
        "value": "#73D0A3"
      },
      "dividend_emphasis": {
        "resource": "app_dividend_emphasis",
        "value": "#F2C14E"
      },
      "dividend_on_hero": {
        "resource": "app_dividend_on_hero",
        "value": "#F2D98F"
      },
      "calendar_record_date": {
        "resource": "app_calendar_record_date",
        "value": "#8BC8EF"
      },
      "calendar_ex_date": {
        "resource": "app_calendar_ex_date",
        "value": "#FFB4AB"
      },
      "calendar_pay_date": {
        "resource": "app_calendar_pay_date",
        "value": "#83D0A6"
      },
      "calendar_received": {
        "resource": "app_calendar_received",
        "value": "#F2C14E"
      },
      "calendar_estimated": {
        "resource": "app_calendar_estimated",
        "value": "#D9B863"
      },
      "miuix_theme": {
        "resource": "miuix_theme_color",
        "value": "#F0C45B"
      },
      "miuix_background": {
        "resource": "miuix_basic_background_color",
        "value": "#17150F"
      },
      "miuix_text": {
        "resource": "miuix_title_color",
        "value": "#F3EDD9"
      },
      "miuix_secondary_text": {
        "resource": "miuix_summary_color",
        "value": "#D1C6A8"
      },
      "miuix_surface": {
        "resource": "miuix_default_surface_color",
        "value": "#242117"
      },
      "miuix_dialog_surface": {
        "resource": "miuix_dialog_background",
        "value": "#242117"
      },
      "miuix_selected_surface": {
        "resource": "miuix_item_choose_background",
        "value": "#4A3715"
      }
    }
  },
  "dimensions": {
    "spacing": {
      "micro": {
        "resource": "ds_space_micro",
        "value": "4dp"
      },
      "compact": {
        "resource": "ds_space_compact",
        "value": "8dp"
      },
      "component": {
        "resource": "ds_space_component",
        "value": "12dp"
      },
      "section": {
        "resource": "ds_space_section",
        "value": "16dp"
      },
      "page_gutter": {
        "resource": "ds_page_gutter",
        "value": "18dp"
      },
      "header_gutter": {
        "resource": "ds_header_gutter",
        "value": "20dp"
      },
      "card_inset_horizontal": {
        "resource": "ds_card_inset_horizontal",
        "value": "16dp"
      },
      "card_inset_vertical": {
        "resource": "ds_card_inset_vertical",
        "value": "14dp"
      },
      "compact_card_inset_vertical": {
        "resource": "ds_compact_card_inset_vertical",
        "value": "12dp"
      },
      "hero_inset_horizontal": {
        "resource": "ds_hero_inset_horizontal",
        "value": "20dp"
      },
      "hero_inset_vertical": {
        "resource": "ds_hero_inset_vertical",
        "value": "18dp"
      },
      "touch_target_min": {
        "resource": "ds_touch_target_min",
        "value": "48dp"
      }
    },
    "shape_and_depth": {
      "card_radius": {
        "resource": "ds_card_radius",
        "value": "16dp"
      },
      "hero_radius": {
        "resource": "ds_hero_radius",
        "value": "20dp"
      },
      "control_radius": {
        "resource": "ds_control_radius",
        "value": "12dp"
      },
      "control_group_radius": {
        "resource": "ds_control_group_radius",
        "value": "14dp"
      },
      "filter_pill_radius": {
        "resource": "ds_filter_pill_radius",
        "value": "24dp"
      },
      "event_badge_radius": {
        "resource": "ds_event_badge_radius",
        "value": "12dp"
      },
      "market_badge_radius": {
        "resource": "ds_market_badge_radius",
        "value": "15dp"
      },
      "calendar_cell_radius": {
        "resource": "ds_calendar_cell_radius",
        "value": "12dp"
      },
      "card_stroke_width": {
        "resource": "ds_card_stroke_width",
        "value": "0dp"
      },
      "card_elevation": {
        "resource": "ds_card_elevation",
        "value": "1dp"
      },
      "navigation_elevation": {
        "resource": "ds_navigation_elevation",
        "value": "3dp"
      },
      "standard_vector_icon": "24dp",
      "home_fab": "56dp"
    },
    "typography": {
      "family": "Android system sans-serif; no downloaded font",
      "brand": {
        "resource": "ds_type_brand",
        "value": "24sp"
      },
      "page_title": {
        "resource": "ds_type_page_title",
        "value": "24sp"
      },
      "section_title": {
        "resource": "ds_type_section_title",
        "value": "16sp"
      },
      "hero_metric": {
        "resource": "ds_type_hero_metric",
        "value": "22sp"
      },
      "data_value": {
        "resource": "ds_type_data_value",
        "value": "14sp"
      },
      "button": {
        "resource": "ds_type_button",
        "value": "14sp"
      },
      "body": {
        "resource": "ds_type_body",
        "value": "14sp"
      },
      "secondary": {
        "resource": "ds_type_secondary",
        "value": "12sp"
      },
      "metadata": {
        "resource": "ds_type_metadata",
        "value": "11sp"
      }
    }
  },
  "layout": {
    "navigation": [
      "总览",
      "持仓",
      "日历",
      "更多"
    ],
    "home_reading_order": [
      "年度分红预测",
      "主要持仓",
      "下一笔预期",
      "补充指标"
    ],
    "holding_metrics": "两列×两行；允许长数值换行，不设固定卡片高度",
    "home_fab": "仅总览显示；右下角悬浮于底部导航上方；滚动内容预留底部空间",
    "calendar_sync_responsive": "同步/手工按钮随字号和可用宽度调高；窄屏或大字号时上下排列"
  },
  "actions": {
    "primary_action_per_screen": 1,
    "home_fab_choices": [
      "手动新增持仓",
      "识别券商截图"
    ],
    "touch_target_min": "48dp",
    "press_feedback": "使用Material原生ripple/状态层，不因按压改变布局尺寸",
    "calendar_state_encoding": "三色圆点 + 完整文字图例和日期口述说明；颜色不单独承载信息"
  },
  "states": {
    "no_holdings": "说明尚无持仓并指出总览加号入口，不显示为零金额",
    "zero_amount": "只有真实计算结果为零时才显示0；未知值显示暂无数据",
    "forecast_unavailable": "区分请求失败、近期无可用历史记录、旧缓存和手工预测",
    "dividend_status": "预测、正式公告、待收、已到账分别标识；已到账与未来预计分开统计",
    "loading": "明确说明正在读取；等待时保留当前表单和用户输入",
    "empty_calendar": "说明当前日期范围没有事件，并保留更新公开数据或手工录入的下一步",
    "error": "指出受影响的任务和可选下一步；不得把网络失败伪装成零值"
  },
  "contrast_checks": [
    {
      "theme": "light",
      "foreground": "on_surface",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "on_surface_variant",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "on_primary",
      "background": "primary",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "hero_on_surface",
      "background": "hero_surface",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "calendar_record_date",
      "background": "secondary_container",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "calendar_ex_date",
      "background": "secondary_container",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "calendar_pay_date",
      "background": "secondary_container",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "market_up",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "market_down",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "dividend_emphasis",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "light",
      "foreground": "dividend_on_hero",
      "background": "hero_surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "on_surface",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "on_surface_variant",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "on_primary",
      "background": "primary",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "hero_on_surface",
      "background": "hero_surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "calendar_record_date",
      "background": "secondary_container",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "calendar_ex_date",
      "background": "secondary_container",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "calendar_pay_date",
      "background": "secondary_container",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "market_up",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "market_down",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "dividend_emphasis",
      "background": "surface",
      "minimum": 4.5
    },
    {
      "theme": "dark",
      "foreground": "dividend_on_hero",
      "background": "hero_surface",
      "minimum": 4.5
    }
  ],
  "typography": {
    "family": "Noto Sans SC static Regular，本地随包加载",
    "weight": "400；标题按需合成粗体",
    "license": "SIL Open Font License 1.1"
  }
}
```

浅色以暖纸白与麦金色组织层级；深色使用暖黑底和明金主色。主页摘要卡使用深麦金表面，市场涨跌继续沿用中国市场习惯的红/绿方向色。主/次角色及其 `on-*` 前景成对使用；沪深市场惯例保持**涨红跌绿**，日历登记/除息/派息另用可辨色相。上面的日历色对比基于浅色次级容器 `#F9DEDC` 和深色次级容器 `#4D0700`，主要文字、操作文字和小字号状态标记的颜色组合按至少 **4.5:1** 检查；不把颜色作为状态的唯一编码。

字号令牌表达稳定层级，正文不得为了塞进卡片而缩小或截断。金额列使用等宽数字特性（`tnum`）减少跳动；中文界面本地随包使用 Noto Sans SC 静态 Regular 字体，源码随附 SIL OFL 1.1 许可，不联网请求字体（[官方 GitHub 字体文件](https://github.com/notofonts/noto-cjk/blob/main/Sans/SubsetOTF/SC/NotoSansSC-Regular.otf)）。

## 页面与组件模式

**1.4.5 Miuix 风格**以 HiMiuix `MiuixCardView` 承载摘要卡、指标卡和日历卡，浅/深色资源同时覆写 HiMiuix 组件默认蓝色；Material 原生单选组、表单与底部导航继续保留其行为与无障碍语义。日历控件组在同一张 Miuix 卡片承载月历/年度总览、说明入口和账户范围。切换沿用 `MaterialButtonToggleGroup`，两项等宽、48dp 高并配本地图标；卡片内部只保留一个浅金分段轨道和金色选中态，不叠加重复描边。说明入口使用本地 Material `info` 图标，账户范围用钱包图标提示其为非交互范围文字，并允许自然换行。外部截图标注只表示待优化区域，不复刻成 App 的圈线或装饰边框。说明仍默认折叠并可按需展开；以上入口保留至少 48dp 触控目标。图标以 VectorDrawable 本地随包、可染色，不加载图标字体或远程资源（[图标目录](https://github.com/material-icons/material-icons)，[Google Fonts Material Symbols 指南](https://developers.google.com/fonts/docs/material_symbols)）。

沪深行情明确读出“上涨 / 下跌 / 平盘”：正涨为红、下跌为绿、零变化为中性色；买入/卖出动作不借用涨跌色。红色分红角色突出年度估算、已到账、YoC 与股息率。日期类型、预计/已到账生命周期图例仍使用独立状态色和文字，不用红绿替代。

**顶栏**仅总览显示“穗账”与全局账户筛选；筛选按钮明确读出当前账户。持仓、日历、更多主 tab 不重复品牌栏或账户 selector，账户范围只以紧凑、非交互提示呈现，切换入口留在总览；当前账户筛选跨页一致，并在导航/Activity 状态恢复时保持。页面水平留白以 18dp 为基准，标准卡片内距 16×14dp，首页紧凑持仓卡的垂直内距为 12dp。卡片使用 18dp 圆角、1dp 细描边和轻微层次；年度摘要卡使用 24dp 圆角、20×18dp 内距，数字优先于说明文字。

**底部导航**只承载四个常用顶层页面，图标与文字同时可见；它不是新增动作菜单。底部栏上方用细分隔线与内容区区分，并显式处理状态栏、导航栏、挖孔、系统手势与 IME insets。滚动容器须在底部保留可滚动 padding，使最后一行完整可达，且不被导航栏、手势区或浮动按钮覆盖。主页加号为 56dp 本地矢量按钮，位于内容区右下方、导航栏上方；首页滚动内容始终预留可滚动空间，避免 FAB 遮挡列表。

**总览**按“年度预测金额 → 最高分红贡献的前四项持仓 → 下一笔预期 → YoC 与累计已确认到账”的顺序呈现。预测说明应紧邻金额，明确多币种不换算以及金额不保证到账。新增不抢占总览主线，只通过加号提供手工新增和本机券商截图识别两条路径；空持仓说明如何开始，不重复放置大型新增按钮。

**持仓卡**先显示标的与市场，再显示报价/涨跌、四项核心指标和预计年分红来源。数量、市值、成本、股息率按两列×两行排列，长名称与数值可以自然换行。涨跌同时使用正负号和文字/数值；报价来源、更新时间、缓存状态要明确。持仓列表可编辑，但新增与更多操作保持次级强调。

**日历**月历、年度总览可切换，网格只显示登记/除息/派息三色圆点；图例展示完整名称，日期控件的屏幕阅读描述读出日期及全部事件类型。更新公开分红按钮保留完整读屏名称，窄屏或大字号时操作按钮上下排列并按文字高度调整。按钮至少 48dp，可访问性描述读出日期、今天/选中状态和当日事件。详细日期口径说明默认折叠，以可访问的至少 48dp 控件按需展开/收起；展开说明不遮挡日历事件。分红行区分历史推算、正式公告、待收、到账及本地手工来源；年度与月度统计分别展示到账和预计金额，不跨币种合并。月历最后一周须可完整滚动查看。

**操作与表单**每个页面只设一个视觉主动作，其他操作使用次级按钮、文本动作或本机对话框。输入项使用可见标签；当 `TextInputLayout` 已承担字段标签时，不再重复显示相同的独立标签，示例提示不能替代标签。一次只显示一个主要弹窗；弹窗在键盘出现时调整尺寸，表单内容可以滚动，校验错误就近显示且不清空输入。选日期、清空日期等子操作结束后要回到清晰的父流程。

## 主 tab 密度与标题层级

底部 tab 已标示当前 root destination，因此总览以外的 root 页面不再用完整页面名和副标题重复占高；页面直接从核心数据或控制开始。视觉标题只从 root presentation 中移除，不删深层编辑、日期详情、统计子页或账户操作所需的上下文标题。无视觉大标题的 root 内容容器应设置 TalkBack 页面上下文；账户筛选状态以轻量文字提示说明“当前范围”和总览切换入口，不新增重复 selector。说明性长文案优先默认折叠，提供可感知展开状态、清楚名称与足够触控区域，不自动打断用户。

## 状态与数据表达

“暂无数据”表示没有可用估算，不等于金额为零。公开请求失败、请求成功但近期无历史分红、旧缓存、手工预测和正式公告是不同状态；失败时可提示稍后重试或手工录入。没有未来已录入事件时说明这个范围当前没有事件，不暗示全年不会收息。

预计年分红来自持仓数量与录入值或历史年分红推算；月均仅作参考。预计派息不是公告，也不是已到账。只有用户或明确的现有规则确认到账后，金额才进入已确认合计。公开证券数据源没有正式授权或服务保证时，不要在产品文案里承诺准确率、覆盖率或SLA；本地账户、数量、成本和交易数据不随公开查询外传。

状态色应有文字、符号、数值或无障碍描述补充。加载期间说明正在做什么；错误文字说明受影响的任务和可行替代路径；缓存数据标出更新时间或“缓存”。空态应给一个当前可执行的下一步，而不是只留空白面板。

空态文案用短句、避免重复同义说法、先说明最重要的信息，CTA 使用明确有力的动词。先解释为何没有数据，并限为一个主动作、最多一个次动作：未录入时引导“添加持仓”；筛选无匹配时说明筛选结果并优先给“清除筛选”，可选“修改条件”；加载态显示进度而非“无数据”；错误态说明失败并提供“重试”或“手工录入”等可行主动作。不要把未录入、筛选无匹配、加载和错误折叠成同一种空态。

## Android 无障碍和适配

Android 页面采用原生 `dp` 触控尺寸、`sp` 文字缩放、Material 3 控件与系统导航习惯。重要交互目标至少 48dp；纯图标动作提供清楚的内容描述，装饰图标不重复进入屏幕阅读顺序；选中、禁用、展开和错误等状态需能被 TalkBack 感知。正文与状态文字目标对比至少 4.5:1，必要的图形边界目标对比至少 3:1。

内容顺序与屏幕阅读顺序一致；不依赖红绿区分，不在固定高度容器里裁掉大字号文本。小窗口、横屏、系统字体放大和键盘弹出时允许内容重排或滚动；底部固定项不得挡住滚动焦点。交互反馈使用 Material 原生 ripple，按压态不改变布局边界；不增加装饰性动画，尊重系统减少动态效果的偏好。

Apple HIG 在此只借鉴**通用原则**：信息易懂、支持更大文字、状态不只靠颜色、导航层级稳定；Android 实施仍使用 Material 3/Android 的 `dp`、`sp`、BottomNavigationView、TalkBack 与系统返回行为，而不是照搬 iOS 的 point、Tab Bar 或控件样式（[Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)、[Typography](https://developer.apple.com/design/human-interface-guidelines/typography)、[Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars/)）。Material 3 提供颜色语义角色和文字层级的实现依据（[Color roles](https://m3.material.io/styles/color/roles)、[Typography](https://m3.material.io/styles/typography/applying-type)）；Fluent、Carbon、Atlassian 与 Ant Design 的启发限于简洁文案、可访问内容、语义令牌和清楚的数据图形（[Fluent accessibility](https://fluent2.microsoft.design/accessibility)、[Fluent content design](https://fluent2.microsoft.design/content-design)、[Carbon color](https://carbondesignsystem.com/guidelines/color/overview/)、[Carbon charts](https://carbondesignsystem.com/data-visualization/getting-started/)、[Atlassian tokens](https://atlassian.design/foundations/tokens/)、[Ant Design values](https://ant.design/docs/spec/values/)）。Ant Design Mobile 仅用于理解轻量、可复用的界面模式，不引入该 Web UI 库（[项目说明](https://github.com/ant-design/ant-design-mobile)）。

Shopify 当前官方的 [Content 指南](https://shopify.dev/docs/apps/design/content) 与 [Empty state 模式](https://shopify.dev/docs/api/app-home/latest/patterns/compositions/empty-state)也提供通用参考：短文案先到重点、避免同义重复，CTA 使用强动词；空态要解释无数据的原因，限制为一个主动作和最多一个次动作，并把未录入、筛选无匹配、加载与错误分别表达。这里只借鉴内容与状态表达原则，不引入 Shopify UI 或产品模式。

## 券商截图字段分组与确认


**窄列持仓列表**按股票分组和列位置逐行匹配名称、持仓/可用上行数量、现价/成本下行成本；不将当日/持仓盈亏或基金分组串作持仓字段。详情页优先依据顶部摘要区域的明确字段标签读取当前数量和成本，不使用可用数量、现价、买入均价、盈亏或历史记录替代。ETF/基金代码不受新增表单默认市场类别过滤。截图未显示完整代码、字段位置冲突或日期无法解析时留空并标“待核对”，不按证券名称猜代码，不默认把今天当建仓日。

预览应区分“标签明确/列位匹配/未识别/存在冲突”，冲突统一标“待核对”；并逐字段展示可编辑识别值与安全的原标签/来源说明，所有预填数据仍需用户逐项核对。无法识别或 ML Kit 失败时，保留空白手工修正路径。多标的列表先列出候选，单只进入既有持仓表单逐项确认；本流程不批量写入、不生成交易或分红记录。识别到历史交易/已到账区域时明确告知这些记录不从持仓截图导入。

所选图片、OCR 文本只用于设备本地字段解析；不上传到穗账服务、不写入日志或备份，也不把完整原文回显（可能包含账户识别码）。现有 ML Kit 服务使用/性能指标披露继续保留；它不应包含截图或识别文字。截图识别路径不增加权限、数据库字段或 JSON 备份字段。

## 持仓代码自动回填

新增持仓表单中输入完整代码后先防抖，再复用现有无密钥公开证券搜索；必须精确匹配代码才自动回填名称、市场/类别与币种。基金/ETF建议类别不能被表单默认的 A 股类别过滤掉；可用时按现有腾讯公开行情端点补充行情。界面显示公开来源、更新时间及在线/本机缓存状态，旧缓存和失败必须明确提示。每次异步回调都核对请求序号和当前代码，旧结果不得覆盖新代码。

数量和每份成本始终由用户输入；行情、代码搜索和历史分红都不能推算、回填这两项。分红只在现有已验证 A/H 数据源可用时估算；基金/ETF没有已验证分红来源时明确标示不可用，而不是显示零。公开查询只发送证券代码，不发送持仓数量、成本、账户或交易流水；查询失败、无精确匹配或离线时仍可继续手工录入。

## 设计审查与验收

每次改动前先确认它解决的是持仓、现金流、日历或可理解性问题；优先修复可访问性和阅读层级，再调整视觉装饰。新增颜色必须有语义名称、浅/深主题值和成对前景，并同步此文件和 Android 资源；组件不在页面里写新的十六进制颜色。新组件应使用上述间距、圆角、类型、卡片及触控令牌，优先复用 Material 3/HiMiuix 原生实现。

交付前检查：

- 总览仍优先显示分红和持仓；底部四个标签无“规划”，加号只在总览作为次要入口。
- 持仓和日历保留既有数据、排序、预测与录入逻辑；预计金额、公告和到账没有混为一谈。
- 浅色、深色的主要文字和小字号语义色对比达标；涨跌和日历事件都有非颜色提示。
- 对话框不叠窗，字段标签不重复，键盘可见时内容能调整和滚动；操作目标至少 48dp。
- 小屏/大字布局可换行，不以固定卡片高度裁切；FAB、导航、系统状态栏与手势区域不冲突。
- 令牌资源与本文件一致；运行源码级回归、构建、lint 与适用的单元测试。没有可用真机或模拟器时，应如实标注未进行设备截图目视验收。

这份指南约束视觉语言与信息表达，不授权改变数据库、算法、行情/分红数据源、权限、备份格式或产品导航。设计改动应保持本地账本与既有估算规则不变。

## 1.4.3 日历信息层级与持仓指标

月历按“已公告提醒 → 月份导航/回到今天 → 登记/除权/派息图例 → 本月到账与待收概况 → 最近数据更新时间 → 日历网格 → 日期事件与操作”排序。已公告卡只统计明确标为已公告的未来事件；空态提供更新入口。日历展示的金额按币种分列，预计金额使用记录口径，已到账优先展示用户确认的实收金额；不让 OCR 推算替代正式公告或券商记录。

持仓卡通过数据层次清晰区分最近收盘价、市值、成本、浮动盈亏与分红收益率。缺少行情时用成本显示估值参考，同时把浮动盈亏标为不可用；同币种占比只在币种内计算，避免将 CNY/HKD/USD 混成一个组合比例。三种成本方式须标明手续费和切换起算点，未知税率不得展示成 0% 免税结果。
