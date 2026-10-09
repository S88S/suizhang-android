package cn.suizhang.ledger;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.res.Configuration;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.util.TypedValue;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hchen.himiuix.MiuixBasicView;
import com.hchen.himiuix.MiuixListView;
import com.hchen.himiuix.widget.MiuixCardView;
import com.hchen.himiuix.callback.OnChooseItemListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private int HERO_SURFACE, ACCENT, PRIMARY_CONTAINER, CREAM, HERO_ON_SURFACE, INK, MUTED, LINE, RED, HERO_MUTED, MARKET_UP, MARKET_DOWN, DIVIDEND;
    private static final String[] MARKETS = {"A股", "港股", "美股", "基金", "ETF", "其他"};
    private static final String[] CURRENCIES = {"CNY", "HKD", "USD"};
    private static final String HOME_METRIC_SETTING = "home_metrics";
    private static final String HOME_METRIC_DEFAULTS = "cost_yield,market_yield,floating_profit,net_invested,holding_count,profit_rate";
    private static final int HOME_METRIC_LIMIT = 6;
    private static final String[] HOME_METRIC_IDS = {"cost_yield", "market_yield", "floating_profit", "net_invested", "holding_count", "profit_rate", "received_year", "total_cost", "market_value", "monthly_forecast", "daily_forecast", "cumulative_received"};
    private static final String[] HOME_METRIC_LABELS = {"成本息率", "市值息率", "浮动盈亏", "净投入", "持仓只数", "盈亏率", "今年已收", "总成本", "总市值", "月均预测分红", "日均预测分红", "累计收息"};
    private static final String[] HOME_METRIC_CHOICES = {
            "成本息率  · 预计年分红 ÷ 总成本", "市值息率  · 预计年分红 ÷ 最新市值", "浮动盈亏  · 最新市值 − 持仓成本",
            "净投入  · 买入成交额 − 卖出成交额", "持仓只数  · 当前持有标的数量", "盈亏率  · 浮动盈亏 ÷ 成本",
            "今年已收  · 本年度已确认到账", "总成本  · 当前数量 × 持仓成本", "总市值  · 最新行情；无行情按成本估值",
            "月均预测分红  · 预计年分红 ÷ 12", "日均预测分红  · 预计年分红 ÷ 365", "累计收息  · 历史已确认到账"
    };
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private LedgerDatabase db;
    private MarketDataClient marketData;
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, MarketDataClient.Quote> quotes = new HashMap<>();
    private boolean quoteRefreshInFlight;
    private long lastQuoteRefresh;
    private String lastQuoteSignature = "";
    private LinearLayout shell, content;
    private String activeTab = "home";
    private long selectedAccount = 0;
    private int year = LocalDate.now().getYear(), month = LocalDate.now().getMonthValue();
    private Integer selectedDay = LocalDate.now().getDayOfMonth();
    private boolean calendarYearView;
    private boolean taxNet;
    private boolean darkMode;
    private Typeface appTypeface = Typeface.DEFAULT;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appTypeface = getResources().getFont(R.font.noto_sans_sc);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        db = new LedgerDatabase(this);
        marketData = new MarketDataClient(this);
        loadColors();
        taxNet = "net".equals(db.setting("tax_display", "gross"));
        if (savedInstanceState != null) {
            String restoredTab = savedInstanceState.getString("ui_active_tab", "home");
            activeTab = ("home".equals(restoredTab) || "holdings".equals(restoredTab) || "calendar".equals(restoredTab) || "more".equals(restoredTab) || "stats".equals(restoredTab)) ? restoredTab : "home";
            selectedAccount = savedInstanceState.getLong("ui_selected_account", 0);
            year = savedInstanceState.getInt("ui_calendar_year", year);
            month = savedInstanceState.getInt("ui_calendar_month", month);
            int savedDay = savedInstanceState.getInt("ui_calendar_day", 0);
            selectedDay = savedDay > 0 ? savedDay : null;
            calendarYearView = savedInstanceState.getBoolean("ui_calendar_year_view", false);
        }
        getWindow().setStatusBarColor(CREAM); getWindow().setNavigationBarColor(getColor(R.color.app_surface));
        darkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        getWindow().getDecorView().setSystemUiVisibility(darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString("ui_active_tab", activeTab);
        outState.putLong("ui_selected_account", selectedAccount);
        outState.putInt("ui_calendar_year", year);
        outState.putInt("ui_calendar_month", month);
        outState.putInt("ui_calendar_day", selectedDay == null ? 0 : selectedDay);
        outState.putBoolean("ui_calendar_year_view", calendarYearView);
        super.onSaveInstanceState(outState);
    }
    private void loadColors() {
        HERO_SURFACE = getColor(R.color.app_hero_surface); ACCENT = getColor(R.color.app_secondary);
        PRIMARY_CONTAINER = getColor(R.color.app_secondary_container); CREAM = getColor(R.color.app_background);
        HERO_ON_SURFACE = getColor(R.color.app_hero_on_surface); INK = getColor(R.color.app_on_surface);
        MUTED = getColor(R.color.app_on_surface_variant); LINE = getColor(R.color.app_outline_variant);
        RED = getColor(R.color.app_error); HERO_MUTED = getColor(R.color.app_hero_muted);
        MARKET_UP = getColor(R.color.app_market_up); MARKET_DOWN = getColor(R.color.app_market_down);
        DIVIDEND = getColor(R.color.app_dividend_emphasis);
    }
    @Override protected void onDestroy() { dataExecutor.shutdownNow(); if (db != null) db.close(); super.onDestroy(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == 44) {
            startLocalScreenshotOcr(uri);
        } else if (requestCode == 42) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("无法创建备份文件");
                out.write(ExcelBackup.exportWorkbook(db.exportJson())); out.flush(); toast("Excel 完整备份已导出");
            } catch (Exception e) { toast("导出失败：" + safeMessage(e)); }
        } else if (requestCode == 43) {
            try {
                byte[] file = readBackupBytes(uri);
                boolean excel = isZipFile(file);
                String json = excel ? ExcelBackup.importWorkbook(file) : new String(file, StandardCharsets.UTF_8);
                new MaterialAlertDialogBuilder(this).setTitle(excel ? "确认恢复 Excel 备份" : "确认恢复旧版 JSON 备份")
                        .setMessage("恢复会完整替换这台设备上的账户、持仓、分红、交易、目标、税率假设和本地索引。建议先导出当前 Excel 备份。工作簿字段与关联编号验证失败时不会改动现有数据。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("验证并恢复", (d, w) -> {
                            try { db.importJson(json); selectedAccount = 0; render(); toast("备份恢复完成"); }
                            catch (Exception e) { toast("备份无效，原数据未更改：" + safeMessage(e)); }
                        }).show();
            } catch (Exception e) { toast("无法读取备份，原数据未更改：" + safeMessage(e)); }
        }
    }
    private byte[] readBackupBytes(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("无法打开文件");
            byte[] buf = new byte[8192]; int n, total = 0;
            while ((n = in.read(buf)) != -1) { total += n; if (total > 10_000_000) throw new IllegalArgumentException("备份超过 10 MB"); out.write(buf, 0, n); }
            return out.toByteArray();
        }
    }
    private boolean isZipFile(byte[] bytes) { return bytes != null && bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K'; }

    private void render() {
        shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setBackgroundColor(CREAM); shell.setFitsSystemWindows(false);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures() | WindowInsetsCompat.Type.mandatorySystemGestures());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(Math.max(safe.bottom, gestures.bottom), ime.bottom);
            view.setPadding(0, safe.top, 0, bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        if ("home".equals(activeTab)) {
            LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dimen(R.dimen.ds_header_gutter), dimen(R.dimen.ds_space_compact), dimen(R.dimen.ds_header_gutter), dimen(R.dimen.ds_space_compact)); header.setBackgroundColor(CREAM);
            TextView brand = tokenText("穗账", R.dimen.ds_type_brand, INK, true);
            header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton accountButton = new MaterialButton(this); accountButton.setText(accountLabel()); accountButton.setTextSize(13); accountButton.setAllCaps(false); accountButton.setTypeface(Typeface.create(appTypeface, Typeface.BOLD));
            accountButton.setInsetTop(0); accountButton.setInsetBottom(0); accountButton.setMinHeight(dimen(R.dimen.ds_touch_target_min)); accountButton.setMinimumHeight(dimen(R.dimen.ds_touch_target_min));
            accountButton.setPadding(dp(12), 0, dp(8), 0); accountButton.setCornerRadius(dimen(R.dimen.ds_filter_pill_radius));
            accountButton.setIconResource(R.drawable.ic_expand_more_24); accountButton.setIconSize(dp(18)); accountButton.setIconPadding(dp(2)); accountButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
            accountButton.setIconTint(ColorStateList.valueOf(getColor(R.color.app_primary))); accountButton.setBackgroundTintList(ColorStateList.valueOf(PRIMARY_CONTAINER)); accountButton.setTextColor(getColor(R.color.app_primary));
            accountButton.setStrokeWidth(dp(1)); accountButton.setStrokeColor(ColorStateList.valueOf(LINE)); accountButton.setContentDescription("账户筛选，当前" + accountLabel() + "，点击切换"); accountButton.setOnClickListener(v -> chooseAccount()); header.addView(accountButton); shell.addView(header);
        }
        FrameLayout pageStage = new FrameLayout(this); pageStage.setBackgroundColor(CREAM);
        ScrollView scroller = new ScrollView(this); scroller.setFillViewport(true); scroller.setClipToPadding(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setAccessibilityPaneTitle(accessibilityPaneTitle());
        content.setPadding(dimen(R.dimen.ds_page_gutter), dp(4), dimen(R.dimen.ds_page_gutter), dp("home".equals(activeTab) ? 96 : 32)); scroller.addView(content);
        pageStage.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        if ("home".equals(activeTab)) {
            FloatingActionButton addHolding = new FloatingActionButton(this);
            addHolding.setSize(FloatingActionButton.SIZE_NORMAL);
            addHolding.setImageResource(R.drawable.ic_add);
            addHolding.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.app_primary)));
            addHolding.setImageTintList(ColorStateList.valueOf(getColor(R.color.app_on_primary)));
            addHolding.setCompatElevation(dp(6));
            addHolding.setContentDescription("新增持仓：手动新增或识别券商截图");
            addHolding.setTooltipText("新增持仓");
            addHolding.setOnClickListener(v -> showHoldingQuickActions());
            FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
            fabParams.setMargins(0, 0, dimen(R.dimen.ds_page_gutter), dp(18));
            pageStage.addView(addHolding, fabParams);
        }
        shell.addView(pageStage, new LinearLayout.LayoutParams(-1, 0, 1));
        View navDivider = new View(this); navDivider.setBackgroundColor(LINE); navDivider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shell.addView(navDivider, new LinearLayout.LayoutParams(-1, dimen(R.dimen.ds_card_stroke_width)));
        shell.addView(bottomNav(), new LinearLayout.LayoutParams(-1, -2)); setContentView(shell); ViewCompat.requestApplyInsets(shell);
        switch (activeTab) {
            case "holdings": showHoldings(); break;
            case "calendar": showCalendar(); break;
            case "stats": showStatsPage(); break;
            case "more": showMore(); break;
            default: showHome();
        }
    }
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView nav = new com.google.android.material.bottomnavigation.BottomNavigationView(this);
        nav.inflateMenu(R.menu.bottom_navigation);
        nav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setItemHorizontalTranslationEnabled(false);
        nav.setBackgroundColor(getColor(R.color.app_surface));
        int[][] itemStates = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        int[] itemColors = new int[]{getColor(R.color.app_primary), getColor(R.color.app_on_surface_variant)};
        ColorStateList navColors = new ColorStateList(itemStates, itemColors);
        nav.setItemIconTintList(navColors); nav.setItemTextColor(navColors);
        nav.setElevation(dimen(R.dimen.ds_navigation_elevation));
        nav.setSelectedItemId("holdings".equals(activeTab) ? R.id.nav_holdings : "calendar".equals(activeTab) ? R.id.nav_calendar : ("more".equals(activeTab) || "stats".equals(activeTab)) ? R.id.nav_more : R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int selectedItem = item.getItemId();
            if (selectedItem == R.id.nav_holdings) activeTab = "holdings";
            else if (selectedItem == R.id.nav_calendar) activeTab = "calendar";
            else if (selectedItem == R.id.nav_more) activeTab = "more";
            else activeTab = "home";
            render();
            return true;
        });
        nav.setContentDescription("主要页面导航");
        return nav;
    }

    private void showHome() {
        LinearLayout homeHeading = new LinearLayout(this); homeHeading.setOrientation(LinearLayout.VERTICAL);
        TextView homeDate = text(LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · EEE", Locale.CHINA)), 12, MUTED, false);
        homeDate.setGravity(Gravity.CENTER_VERTICAL);
        homeHeading.addView(homeDate, new LinearLayout.LayoutParams(-1, -2));
        TextView homeTitle = tokenText("你的股息现金流", R.dimen.ds_type_page_title, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dimen(R.dimen.ds_home_heading_gap);
        homeHeading.addView(homeTitle, titleParams);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(2); headingParams.bottomMargin = dp(8);
        content.addView(homeHeading, headingParams);
        JSONArray hs = db.holdings(selectedAccount);
        Map<String, Double> annual = estimatedIncomeByCurrency(false); Map<String, Double> costs = costByCurrency();
        boolean hasEstimate = hasAnyDividendEstimate();
        List<String> metricIds = selectedHomeMetrics();
        Map<String, String> metricValues = homeMetricValues(hs, annual, costs);
        MiuixCardView heroSurface = new MiuixCardView(this); heroSurface.setCardBackgroundColor(HERO_SURFACE); heroSurface.setRadius(dimen(R.dimen.ds_hero_radius)); heroSurface.setCardElevation(dimen(R.dimen.ds_card_elevation));
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dimen(R.dimen.ds_hero_inset_horizontal), dimen(R.dimen.ds_hero_inset_vertical), dimen(R.dimen.ds_hero_inset_horizontal), dimen(R.dimen.ds_hero_inset_vertical)); heroSurface.addView(hero, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout heroTop = row(); heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroTop.addView(text(overviewHoldingDuration(hs), 11, HERO_MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        heroTop.addView(overviewMetricSettingsButton(metricIds.size())); hero.addView(heroTop);
        addHeroDivider(hero);
        LinearLayout heading = row(); heading.addView(tokenText(LocalDate.now().getYear() + " 年预计分红", R.dimen.ds_type_body, HERO_MUTED, true), new LinearLayout.LayoutParams(0, -2, 1)); heading.addView(text("本地估算", 11, HERO_MUTED, false)); hero.addView(heading);
        TextView annualValue = tokenText(hasEstimate ? formatAmounts(annual) : "暂无数据", R.dimen.ds_type_hero_metric,
                hasEstimate ? getColor(R.color.app_dividend_on_hero) : HERO_ON_SURFACE, true);
        annualValue.setFontFeatureSettings("tnum"); annualValue.setMaxLines(3); hero.addView(annualValue);
        String estimateNote = hasEstimate ? "月均参考  " + formatAmounts(scaleMap(annual, 1d / 12d))
                : hs.length() == 0 ? "添加持仓后显示估算；未知金额不按 ¥0 计入。" : "持仓分红暂无可用估算；未知金额不会按 ¥0 计入。";
        hero.addView(tokenText(estimateNote, R.dimen.ds_type_secondary, HERO_MUTED, false), margin(0, 4, 0, 0));
        if (hasEstimate) hero.addView(tokenText("按持仓数量与已录入/公开历史分红估算；币种分开展示，不换算，未来派息可能变化。", R.dimen.ds_type_metadata, HERO_MUTED, false), margin(0, 4, 0, 0));
        if (!metricIds.isEmpty()) {
            addHeroDivider(hero);
            addOverviewMetricGrid(hero, metricIds, metricValues);
        }
        content.addView(heroSurface, margin(0, 4, 0, 8));

        sectionHeader("持仓预计年分红", "查看全部持仓", () -> { activeTab = "holdings"; render(); });
        if (hs.length() == 0) {
            emptyInfoCard("还没有持仓", "新增持仓后，这里会显示分红估算与后续收息安排。可从右下角加号开始。");
        } else {
            ArrayList<JSONObject> ranked = new ArrayList<>();
            for (int i = 0; i < hs.length(); i++) { JSONObject holding = hs.optJSONObject(i); if (holding != null) ranked.add(holding); }
            Collections.sort(ranked, (a, b) -> Double.compare(annualOf(b, false), annualOf(a, false)));
            for (int i = 0; i < Math.min(4, ranked.size()); i++) addHomeHoldingCard(ranked.get(i));
            if (ranked.size() > 4) content.addView(text("还有 " + (ranked.size() - 4) + " 项持仓，查看全部列表。", 11, MUTED, false), margin(2, 0, 0, 6));
        }

        sectionHeader("下一笔预期", "分红日历", () -> { activeTab = "calendar"; render(); });
        JSONArray next = db.nextDividends(selectedAccount, LocalDate.now().toString(), 3);
        if (next.length() == 0) content.addView(text("尚无未来已录入派息事件；可在分红日历查看记录。", 12, MUTED, false), margin(0, 3, 0, 10));
        else for (int i = 0; i < next.length(); i++) addDividendRow(next.optJSONObject(i), false);
    }

    private MaterialButton overviewMetricSettingsButton(int selectedCount) {
        MaterialButton button = new MaterialButton(this); button.setText("设置指标 " + selectedCount + "/" + HOME_METRIC_LIMIT);
        button.setTextSize(11); button.setAllCaps(false); button.setTypeface(Typeface.create(appTypeface, Typeface.BOLD));
        button.setInsetTop(0); button.setInsetBottom(0); button.setInsetLeft(0); button.setInsetRight(0);
        button.setMinHeight(dimen(R.dimen.ds_touch_target_min)); button.setMinimumHeight(dimen(R.dimen.ds_touch_target_min));
        button.setMinWidth(dimen(R.dimen.ds_touch_target_min)); button.setIconResource(R.drawable.ic_edit_note_24);
        button.setIconSize(dp(16)); button.setIconPadding(dp(4)); button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconTint(ColorStateList.valueOf(HERO_MUTED)); button.setTextColor(HERO_MUTED);
        button.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)); button.setStrokeWidth(0);
        button.setContentDescription("设置总览指标，已选择 " + selectedCount + " 项，最多 " + HOME_METRIC_LIMIT + " 项");
        button.setOnClickListener(v -> showOverviewMetricSettings()); return button;
    }

    private void addHeroDivider(LinearLayout hero) {
        View line = new View(this); line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1)); params.topMargin = dp(8); params.bottomMargin = dp(8); hero.addView(line, params);
    }

    private void addOverviewMetricGrid(LinearLayout hero, List<String> metricIds, Map<String, String> values) {
        GridLayout grid = new GridLayout(this); grid.setColumnCount(3); grid.setUseDefaultMargins(false);
        for (int index = 0; index < metricIds.size(); index++) {
            String id = metricIds.get(index); int position = Arrays.asList(HOME_METRIC_IDS).indexOf(id);
            if (position < 0) continue;
            LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            cell.setMinimumHeight(dimen(R.dimen.ds_overview_metric_row_min_height)); cell.setPadding(dp(4), dp(3), dp(4), dp(3));
            TextView label = text(HOME_METRIC_LABELS[position], 11, HERO_MUTED, false); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
            TextView value = tokenText(values.getOrDefault(id, "—"), R.dimen.ds_type_data_value, getColor(R.color.app_dividend_on_hero), true);
            value.setGravity(Gravity.CENTER); value.setFontFeatureSettings("tnum"); value.setMaxLines(3);
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2)); cell.addView(value, margin(0, 2, 0, 0));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(index / 3), GridLayout.spec(index % 3, 1f));
            params.width = 0; params.height = -2; params.leftMargin = dp(2); params.rightMargin = dp(2); grid.addView(cell, params);
        }
        hero.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showOverviewMetricSettings() {
        String saved = db.setting(HOME_METRIC_SETTING, HOME_METRIC_DEFAULTS);
        Set<String> selected = new HashSet<>(Arrays.asList(saved.split(",")));
        boolean[] checked = new boolean[HOME_METRIC_IDS.length];
        for (int i = 0; i < HOME_METRIC_IDS.length; i++) checked[i] = selected.contains(HOME_METRIC_IDS[i]);
        new MaterialAlertDialogBuilder(this).setTitle("设置总览指标")
                .setMessage("可选 0–6 项，点选添加或取消。选 0 项时隐藏指标栅格；金额按币种分开展示，不做汇率换算。")
                .setMultiChoiceItems(HOME_METRIC_CHOICES, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked; int count = 0; for (boolean value : checked) if (value) count++;
                    if (count > HOME_METRIC_LIMIT) {
                        checked[which] = false; ((AlertDialog) dialog).getListView().setItemChecked(which, false); toast("最多选择 6 项指标");
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (dialog, which) -> { db.saveSetting(HOME_METRIC_SETTING, HOME_METRIC_DEFAULTS); render(); toast("已恢复默认指标"); })
                .setPositiveButton("保存", (dialog, which) -> {
                    ArrayList<String> values = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) values.add(HOME_METRIC_IDS[i]);
                    db.saveSetting(HOME_METRIC_SETTING, android.text.TextUtils.join(",", values)); render(); toast("总览指标已保存");
                }).show();
    }

    private List<String> selectedHomeMetrics() {
        String saved = db.setting(HOME_METRIC_SETTING, HOME_METRIC_DEFAULTS);
        Set<String> selected = new HashSet<>(Arrays.asList(saved.split(",")));
        ArrayList<String> ordered = new ArrayList<>();
        for (String id : HOME_METRIC_IDS) if (selected.contains(id) && ordered.size() < HOME_METRIC_LIMIT) ordered.add(id);
        return ordered;
    }

    private Map<String, String> homeMetricValues(JSONArray holdings, Map<String, Double> annual, Map<String, Double> costs) {
        Map<String, String> values = new HashMap<>();
        values.put("cost_yield", formatPercentMap(costYieldByCurrency(annual, costs)));
        values.put("market_yield", formatPercentMap(marketYieldByCurrency(holdings)));
        Map<String, Double> floating = floatingProfitByCurrency(holdings);
        values.put("floating_profit", formatAmounts(floating));
        Map<String, Double> netInvested = new TreeMap<>(); JSONArray flows = db.netInvestedByCurrency(selectedAccount);
        for (int i = 0; i < flows.length(); i++) { JSONObject row = flows.optJSONObject(i); if (row != null) netInvested.put(row.optString("currency", "CNY"), row.optDouble("total")); }
        values.put("net_invested", netInvested.isEmpty() ? "暂无流水" : formatAmounts(netInvested));
        int holdingCount = 0; for (int i = 0; i < holdings.length(); i++) { JSONObject h = holdings.optJSONObject(i); if (h != null && h.optDouble("quantity") > 0d) holdingCount++; }
        values.put("holding_count", holdingCount + " 只");
        values.put("profit_rate", formatPercentMap(profitRateByCurrency(holdings)));
        values.put("received_year", formatAmounts(receivedInYear(LocalDate.now().getYear())));
        values.put("total_cost", formatAmounts(costs));
        values.put("market_value", formatAmounts(marketValueByCurrency(holdings)));
        values.put("monthly_forecast", formatAmounts(scaleMap(annual, 1d / 12d)));
        values.put("daily_forecast", formatAmounts(scaleMap(annual, 1d / 365d)));
        values.put("cumulative_received", formatAmounts(receivedAllTime()));
        return values;
    }

    private String overviewHoldingDuration(JSONArray holdings) {
        LocalDate earliest = null, today = LocalDate.now();
        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.optJSONObject(i); if (h == null || h.optDouble("quantity") <= 0d) continue;
            LocalDate date = parseDate(db.firstPurchaseDate(h.optLong("_id")));
            if (date != null && !date.isAfter(today) && (earliest == null || date.isBefore(earliest))) earliest = date;
        }
        if (earliest == null) return holdings.length() == 0 ? "目前没有持仓" : "最早建仓日期待核对";
        long days = FinanceMath.holdingDays(earliest, today); long years = days / 365; long remainder = days % 365;
        return "首笔买入至今 · " + (years > 0 ? years + " 年 " : "") + remainder + " 天";
    }

    private Map<String, Double> costYieldByCurrency(Map<String, Double> annual, Map<String, Double> costs) {
        Map<String, Double> values = new TreeMap<>();
        for (String currency : costs.keySet()) if (annual.containsKey(currency)) {
            double value = FinanceMath.costYield(annual.get(currency), costs.get(currency)); if (Double.isFinite(value)) values.put(currency, value);
        }
        return values;
    }

    private Map<String, Double> marketYieldByCurrency(JSONArray holdings) {
        Map<String, Double> annual = new TreeMap<>(), marketValues = new TreeMap<>();
        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.optJSONObject(i); if (h == null) continue;
            MarketDataClient.Quote quote = quoteFor(h); double quantity = h.optDouble("quantity"), income = annualOf(h, false);
            if (quote == null || quote.price <= 0d || quantity <= 0d || income <= 0d) continue;
            String currency = h.optString("currency", "CNY");
            annual.put(currency, annual.getOrDefault(currency, 0d) + income);
            marketValues.put(currency, marketValues.getOrDefault(currency, 0d) + quantity * quote.price);
        }
        Map<String, Double> yields = new TreeMap<>();
        for (String currency : annual.keySet()) { double value = FinanceMath.marketYield(annual.get(currency), marketValues.getOrDefault(currency, 0d)); if (Double.isFinite(value)) yields.put(currency, value); }
        return yields;
    }

    private Map<String, Double> profitRateByCurrency(JSONArray holdings) {
        Map<String, Double> floating = new TreeMap<>(), costs = new TreeMap<>();
        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.optJSONObject(i); if (h == null) continue;
            MarketDataClient.Quote quote = quoteFor(h); if (quote == null) continue;
            String currency = h.optString("currency", "CNY"); double quantity = h.optDouble("quantity"), cost = quantity * h.optDouble("cost");
            floating.put(currency, floating.getOrDefault(currency, 0d) + FinanceMath.floatingProfit(quantity * quote.price, cost));
            costs.put(currency, costs.getOrDefault(currency, 0d) + cost);
        }
        Map<String, Double> rates = new TreeMap<>();
        for (String currency : floating.keySet()) { double value = FinanceMath.profitRate(floating.get(currency), costs.getOrDefault(currency, 0d)); if (Double.isFinite(value)) rates.put(currency, value); }
        return rates;
    }

    private Map<String, Double> receivedInYear(int targetYear) {
        Map<String, Double> values = new TreeMap<>(); JSONArray rows = db.incomeSummary(selectedAccount, targetYear);
        for (int i = 0; i < rows.length(); i++) { JSONObject row = rows.optJSONObject(i); if (row != null && "received".equals(row.optString("status"))) values.put(row.optString("currency", "CNY"), row.optDouble("total")); }
        return values;
    }

    private void addHomeHoldingCard(JSONObject holding) {
        CardColumn item = card(); item.setOrientation(LinearLayout.VERTICAL); item.setPadding(dimen(R.dimen.ds_card_inset_horizontal), dimen(R.dimen.ds_compact_card_inset_vertical), dimen(R.dimen.ds_card_inset_horizontal), dimen(R.dimen.ds_compact_card_inset_vertical));
        double gross = annualOf(holding, false), quantity = holding.optDouble("quantity"), cost = quantity * holding.optDouble("cost");
        LinearLayout heading = row(); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(holding.optString("name"), 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(text(holding.optString("market") + " · " + holding.optString("currency"), 10, MUTED, false));
        item.addView(heading);
        item.addView(text(gross > 0d ? amount(holding.optString("currency"), gross) + "/年" : "暂无数据", 15, gross > 0d ? DIVIDEND : MUTED, true), margin(0, 4, 0, 1));
        String detail = "持有 " + compact(quantity) + " 份 · " + forecastSourceLabel(holding);
        if (gross > 0d && cost > 0d) detail += " · YoC " + percent(gross / cost);
        item.addView(text(detail, 11, MUTED, false));
        content.addView(item, margin(0, 3, 0, 6));
    }

    private void showHoldingQuickActions() {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this).setTitle("新增持仓")
                .setItems(new String[]{"手动新增持仓", "识别券商截图"}, (dialog, which) -> {
                    if (which == 0) showHoldingEditor(null, null);
                    else if (which == 1) chooseBrokerScreenshot();
                }).setNegativeButton("取消", null).show();
    }

    private void addTaxToggle() {
        TextView label = text("分红显示口径", 12, MUTED, false);
        MaterialButton gross = actionButton(taxNet ? "税前" : "税前 ✓", !taxNet, () -> setTaxView(false));
        MaterialButton net = actionButton(taxNet ? "税后 ✓" : "税后", taxNet, () -> setTaxView(true));
        gross.setSingleLine(false); gross.setMaxLines(2); net.setSingleLine(false); net.setMaxLines(2);
        float fontScale = getResources().getConfiguration().fontScale;
        boolean stacked = fontScale > 1.25f || getResources().getConfiguration().screenWidthDp < 340;
        int buttonHeight = Math.max(dp(48), dp(getResources().getDimension(R.dimen.ds_type_button)
                / getResources().getDisplayMetrics().density * 2.8f + 16f));
        gross.setMinHeight(buttonHeight); gross.setMinimumHeight(buttonHeight);
        net.setMinHeight(buttonHeight); net.setMinimumHeight(buttonHeight);
        LinearLayout container = row(); container.setMinimumHeight(dp(48));
        if (stacked) {
            container.setOrientation(LinearLayout.VERTICAL);
            container.addView(label, new LinearLayout.LayoutParams(-1, -2));
            container.addView(actionButtonRow(gross, net), new LinearLayout.LayoutParams(-1, -2));
        } else {
            container.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            container.addView(gross, new LinearLayout.LayoutParams(-2, buttonHeight));
            View gap = new View(this); gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            container.addView(gap, new LinearLayout.LayoutParams(dp(7), 1));
            container.addView(net, new LinearLayout.LayoutParams(-2, buttonHeight));
        }
        content.addView(container, margin(0, 0, 0, 2));
    }
    private void setTaxView(boolean net) { taxNet = net; db.saveSetting("tax_display", net ? "net" : "gross"); render(); }

    private void showHoldings() {
        JSONArray hs = db.holdings(selectedAccount);
        content.addView(text(accountScopeLabel(), 11, MUTED, false), margin(1, 0, 1, 4));
        content.addView(actionButtonRow(actionButton("新增持仓", false, () -> showHoldingQuickActions()),
                actionButton("更多操作", false, this::showHoldingsMoreActions)), margin(0, 0, 0, 8));
        if (hs.length() == 0) { emptyInfoCard("还没有持仓记录", "可在上方选择手动新增，或从券商截图识别；行情与分红源不可用时仍可本地记账。"); return; }
        Map<String, Double> currencyMarketValues = marketValueByCurrency(hs);
        for (int i = 0; i < hs.length(); i++) {
            JSONObject h = hs.optJSONObject(i); CardColumn item = card(); item.setOrientation(LinearLayout.VERTICAL);
            MarketDataClient.Quote quote = quoteFor(h);
            LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL); top.addView(text(h.optString("name"), 16, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
            TextView tag = text(h.optString("market") + " · " + h.optString("currency"), 10, ACCENT, true); tag.setPadding(dp(8), dp(5), dp(8), dp(5)); tag.setBackground(roundPx(PRIMARY_CONTAINER, dimen(R.dimen.ds_market_badge_radius), 0, 0)); top.addView(tag); item.addView(top);
            String code = h.optString("code");
            String quoteLine = quote == null ? "行情未同步 · 按持仓成本估值" : amount(quote.currency, quote.price) + "  " + marketMoveLabel(quote.changePercent) + (quote.stale ? " · 缓存" : "");
            item.addView(text((code.isEmpty() ? "未填写代码" : code) + " · " + h.optString("account_name") + "   " + quoteLine + (quote == null ? "" : " · 腾讯截至 " + timeLabel(quote.updatedAt)), 11, quote == null ? MUTED : marketMoveColor(quote.changePercent), false), margin(0, 4, 0, 9));
            double q = h.optDouble("quantity"), unitCost = h.optDouble("cost"), gross = annualOf(h, false), invested = q * unitCost;
            String currency = h.optString("currency", "CNY");
            double marketValue = quote == null ? Math.max(0d, invested) : q * quote.price;
            double floating = quote == null ? 0d : FinanceMath.floatingProfit(marketValue, invested);
            double profitRate = quote == null ? Double.NaN : FinanceMath.profitRate(floating, invested);
            double currentYield = quote == null ? Double.NaN : FinanceMath.marketYield(h.optDouble("annual_dividend_per_unit"), quote.price);
            double costYield = FinanceMath.costYield(gross, invested);
            double positionWeight = FinanceMath.positionWeight(marketValue, currencyMarketValues.getOrDefault(currency, 0d));
            LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.VERTICAL); stats.setBaselineAligned(false);
            stats.addView(metricPair("持有数量", compact(q), "市值", amount(currency, marketValue)));
            stats.addView(metricPair("持仓成本", amount(currency, invested), "昨收息率", Double.isFinite(currentYield) ? percent(currentYield) : "—"), margin(0, 8, 0, 0));
            LinearLayout performance = row(); performance.setBaselineAligned(false);
            performance.addView(metric("浮动盈亏", quote == null ? "—" : amount(currency, floating), quote == null ? INK : (floating >= 0 ? MARKET_UP : MARKET_DOWN)), new LinearLayout.LayoutParams(0, -2, 1));
            performance.addView(metric("盈亏率", Double.isFinite(profitRate) ? percent(profitRate) : "—", quote == null ? INK : (floating >= 0 ? MARKET_UP : MARKET_DOWN)), new LinearLayout.LayoutParams(0, -2, 1));
            stats.addView(performance, margin(0, 8, 0, 0));
            stats.addView(metricPair("同币种占比", Double.isFinite(positionWeight) ? percent(positionWeight) : "—", "成本息率", Double.isFinite(costYield) ? percent(costYield) : "—"), margin(0, 8, 0, 0));
            item.addView(stats);
            double perUnit = h.optDouble("annual_dividend_per_unit");
            item.addView(text("预计年分红  " + (gross > 0d ? amount(currency, gross) : "暂无数据"), 12, gross > 0d ? DIVIDEND : MUTED, true), margin(0, 9, 0, 2));
            LocalDate firstBuy = parseDate(db.firstPurchaseDate(h.optLong("_id"))); long heldDays = FinanceMath.holdingDays(firstBuy, LocalDate.now());
            String holdingDuration = heldDays < 0 ? "持股天数待核对" : "首买至今 " + heldDays + " 天";
            item.addView(text((perUnit > 0d ? "每份年分红 " + amount(currency, perUnit) + " · " : "") + forecastSourceLabel(h) + " · " + holdingDuration + " · YoC " + (Double.isFinite(costYield) ? percent(costYield) : "—") + " · 报价 " + (quote == null ? "无" : "截至 " + timeLabel(quote.updatedAt)), 11, MUTED, false), margin(0, 3, 0, 10));
            LinearLayout buttons = row(); buttons.addView(actionButton("编辑", false, () -> showHoldingEditor(h, null)), new LinearLayout.LayoutParams(0, dp(48), 1));
            buttons.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1)); buttons.addView(actionButton("更多", false, () -> holdingMenu(h)), new LinearLayout.LayoutParams(0, dp(48), 1));
            item.addView(buttons); item.setOnLongClickListener(v -> { holdingMenu(h); return true; }); content.addView(item, margin(0, 5, 0, 8));
        }
        content.addView(text("浮动盈亏按最近已收盘行情减当前持仓成本；无行情时市值按成本估值且浮动盈亏不计。占比仅在同币种内计算，不将人民币、港币和美元直接相加；外币汇率折算尚未接入。", 11, MUTED, false), margin(2, 5, 0, 4));
        refreshQuotes(hs);
    }

    private LinearLayout metric(String label, String value) { return metric(label, value, INK); }
    private LinearLayout metric(String label, String value, int valueColor) { LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.addView(tokenText(label, R.dimen.ds_type_metadata, MUTED, false)); TextView amount = tokenText(value, R.dimen.ds_type_data_value, valueColor, true); amount.setFontFeatureSettings("tnum"); b.addView(amount, margin(0, 2, 0, 0)); return b; }
    private LinearLayout metricPair(String firstLabel, String firstValue, String secondLabel, String secondValue) {
        LinearLayout pair = row(); pair.setBaselineAligned(false);
        pair.addView(metric(firstLabel, firstValue, "股息率".equals(firstLabel) && !"—".equals(firstValue) ? DIVIDEND : INK), new LinearLayout.LayoutParams(0, -2, 1));
        View gap = new View(this); gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pair.addView(gap, new LinearLayout.LayoutParams(dimen(R.dimen.ds_space_component), 1));
        pair.addView(metric(secondLabel, secondValue, "股息率".equals(secondLabel) && !"—".equals(secondValue) ? DIVIDEND : INK), new LinearLayout.LayoutParams(0, -2, 1));
        return pair;
    }
    private void showHoldingsMoreActions() {
        new MaterialAlertDialogBuilder(this).setTitle("持仓工具").setItems(new String[]{"搜索证券", "导入券商截图", "刷新行情"}, (d, which) -> {
            if (which == 0) beginSecuritySearch(null);
            else if (which == 1) chooseBrokerScreenshot();
            else { lastQuoteRefresh = 0; refreshQuotes(db.holdings(selectedAccount)); }
        }).show();
    }
    private void holdingMenu(JSONObject h) {
        new MaterialAlertDialogBuilder(this).setTitle(h.optString("name")).setItems(new String[]{"记分红", "记交易", "编辑持仓", "删除持仓及关联流水"}, (d, which) -> {
            if (which == 0) showDividendDialog(h.optLong("_id"));
            else if (which == 1) showTransactionDialog(h.optLong("_id"));
            else if (which == 2) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(h, null));
            else new MaterialAlertDialogBuilder(this).setTitle("删除这项持仓？").setMessage("会同时删除分红与交易记录，且无法撤销。建议先导出备份。")
                    .setNegativeButton("取消", null).setPositiveButton("删除", (x, y) -> { db.deleteHolding(h.optLong("_id")); render(); toast("已删除"); }).show();
        }).show();
    }

    private void showCalendar() {
        content.addView(calendarControlPanel(), margin(0, 0, 0, 10));
        if (calendarYearView) { showCalendarYear(); return; }

        YearMonth cur = calendarMonth();
        if (selectedDay != null && (selectedDay < 1 || selectedDay > cur.lengthOfMonth())) selectedDay = null;
        String ym = String.format(Locale.US, "%04d-%02d", cur.getYear(), cur.getMonthValue());
        JSONArray events = db.dividends(selectedAccount, ym); if (events == null) events = new JSONArray();
        Map<Integer, int[]> marks = new TreeMap<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null) continue;
            markCalendarDate(marks, e.optString("record_date"), ym, 0);
            markCalendarDate(marks, e.optString("ex_date"), ym, 1);
            markCalendarDate(marks, e.optString("pay_date"), ym, 2);
        }

        JSONArray announced = db.announcedDividends(selectedAccount, LocalDate.now().toString());
        TreeMap<String, Double> announcedAmounts = new TreeMap<>();
        for (int i = 0; i < announced.length(); i++) {
            JSONObject e = announced.optJSONObject(i); if (e == null) continue;
            String c = e.optString("currency", "CNY");
            announcedAmounts.put(c, announcedAmounts.getOrDefault(c, 0d) + e.optDouble("total"));
        }
        CardColumn announcedCard = card(); announcedCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout announcedHead = row(); announcedHead.setGravity(Gravity.CENTER_VERTICAL);
        announcedHead.addView(text("已公告的持仓分红", 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        announcedHead.addView(text(announced.length() + " 笔", 11, ACCENT, true)); announcedCard.addView(announcedHead);
        if (announced.length() > 0) {
            announcedCard.addView(text("预计待收  " + formatAmounts(announcedAmounts), 14, DIVIDEND, true), margin(0, 5, 0, 0));
            announcedCard.addView(text("仅汇总已公告事件的记录金额，按币种分别展示；实际方案与到账以正式公告及券商记录为准。", 10, MUTED, false), margin(0, 4, 0, 0));
            JSONObject first = announced.optJSONObject(0); LocalDate firstDate = first == null ? null : parseDate(first.optString("pay_date"));
            announcedCard.setContentDescription("有 " + announced.length() + " 笔已公告的预期分红，预计待收 " + formatAmounts(announcedAmounts) + (firstDate == null ? "" : "；点按跳转到最早派息日"));
            announcedCard.setOnClickListener(v -> {
                if (firstDate != null) { year = firstDate.getYear(); month = firstDate.getMonthValue(); selectedDay = firstDate.getDayOfMonth(); calendarYearView = false; render(); }
            });
            announcedCard.setFocusable(true);
        } else {
            announcedCard.addView(text("当前没有已同步的未来公告分红；历史推算不会冒充正式公告。", 11, MUTED, false), margin(0, 4, 0, 0));
            announcedCard.addView(actionButton("更新公开公告", false, this::syncPublicDividends), margin(0, 5, 0, 0));
        }
        content.addView(announcedCard, margin(0, 0, 0, 10));

        LinearLayout monthHead = row(); monthHead.setGravity(Gravity.CENTER_VERTICAL);
        monthHead.addView(calendarNavButton(R.drawable.ic_chevron_left_24, "上一个月", () -> shiftMonth(-1)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout monthTitleBox = new LinearLayout(this); monthTitleBox.setGravity(Gravity.CENTER); monthTitleBox.setOrientation(LinearLayout.VERTICAL);
        TextView monthTitle = text(cur.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")), 17, INK, true); monthTitle.setGravity(Gravity.CENTER); monthTitleBox.addView(monthTitle);
        MaterialButton todayButton = calendarContextButton("回到今天", R.drawable.ic_today_24, "回到今天并选中今天", this::showCurrentMonth); monthTitleBox.addView(todayButton);
        monthHead.addView(monthTitleBox, new LinearLayout.LayoutParams(0, -2, 1));
        monthHead.addView(calendarNavButton(R.drawable.ic_chevron_right_24, "下一个月", () -> shiftMonth(1)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(monthHead, margin(0, 0, 0, 6));

        LinearLayout legend = row(); legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(calendarLegendItem("股权登记", getColor(R.color.app_calendar_record_date)), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(calendarLegendItem("除权除息", getColor(R.color.app_calendar_ex_date)), new LinearLayout.LayoutParams(0, -2, 1));
        legend.addView(calendarLegendItem("派息日", getColor(R.color.app_calendar_pay_date)), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(legend, margin(0, 0, 0, 5));

        CardColumn totalCard = card(); totalCard.setOrientation(LinearLayout.VERTICAL);
        totalCard.addView(text("本月到账与待收", 14, INK, true));
        Map<String, Double> monthReceived = new TreeMap<>(), monthExpected = new TreeMap<>(); int expectedCount = 0, receivedCount = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null || !e.optString("pay_date").startsWith(ym)) continue;
            boolean received = "received".equals(e.optString("status"));
            Map<String, Double> target = received ? monthReceived : monthExpected;
            String currency = e.optString("currency", "CNY");
            double amount = received && !e.isNull("received_amount") ? e.optDouble("received_amount") : e.optDouble("total");
            target.put(currency, target.getOrDefault(currency, 0d) + amount);
            if (received) receivedCount++; else expectedCount++;
        }
        totalCard.addView(text("待到账 " + expectedCount + " 笔  ·  " + formatAmounts(monthExpected), 12, INK, true), margin(0, 6, 0, 0));
        totalCard.addView(text("已到账 " + receivedCount + " 笔  ·  " + formatAmounts(monthReceived), 12, DIVIDEND, true), margin(0, 4, 0, 0));
        totalCard.addView(text("待收使用税前记录金额；已到账优先用实收金额，旧记录未填实收时显示记录金额。各币种不合并换算。", 10, MUTED, false), margin(0, 5, 0, 0));
        content.addView(totalCard, margin(0, 0, 0, 7));

        long dividendSyncAt = settingLong("dividend_sync_at", 0L); boolean syncStale = "true".equals(setting("dividend_sync_stale", "false"));
        String freshness = dividendSyncAt > 0 ? "公开分红数据上次更新 " + timeLabel(dividendSyncAt) + (syncStale ? " · 含旧缓存" : "") : "公开分红数据尚未同步；新公告同步后显示在上方提醒。";
        content.addView(text(freshness + "  ·  日期和金额以正式公告为准。", 10, MUTED, false), margin(2, 0, 0, 6));

        CardColumn cal = card(); cal.setOrientation(LinearLayout.VERTICAL); cal.setPadding(dp(8), dp(9), dp(8), dp(9));
        GridLayout grid = new GridLayout(this); grid.setColumnCount(7);
        for (String w : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
            TextView t = text(w, 10, MUTED, true); t.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(); p.width = 0; p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); grid.addView(t, p);
        }
        int offset = cur.atDay(1).getDayOfWeek().getValue() - 1; int cells = ((offset + cur.lengthOfMonth() + 6) / 7) * 7;
        int[] markerColors = {getColor(R.color.app_calendar_record_date), getColor(R.color.app_calendar_ex_date), getColor(R.color.app_calendar_pay_date)};
        for (int i = 0; i < cells; i++) {
            int day = i - offset + 1; LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(); p.width = 0; p.height = -2; p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            if (day > 0 && day <= cur.lengthOfMonth()) {
                final int cd = day; boolean selected = selectedDay != null && selectedDay == day;
                boolean today = LocalDate.now().getYear() == cur.getYear() && LocalDate.now().getMonthValue() == cur.getMonthValue() && LocalDate.now().getDayOfMonth() == day;
                TextView num = text(String.valueOf(day), 12, selected ? getColor(R.color.app_on_primary) : (today ? ACCENT : INK), today || selected); num.setGravity(Gravity.CENTER); cell.addView(num);
                int[] types = marks.get(day);
                if (types != null) {
                    LinearLayout markers = row(); markers.setGravity(Gravity.CENTER);
                    for (int ti = 0; ti < types.length; ti++) if (types[ti] > 0) {
                        View marker = calendarMarkerDot(markerColors[ti], 6, selected);
                        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(6), dp(6));
                        markerParams.setMargins(ti == 0 ? 0 : dp(3), 0, 0, 0);
                        markers.addView(marker, markerParams);
                    }
                    cell.addView(markers);
                }
                cell.setMinimumHeight(dp(52)); cell.setFocusable(true);
                cell.setContentDescription(cur.getYear() + "年" + cur.getMonthValue() + "月" + cd + "日" + (today ? "，今天" : "") + (types == null ? "，无分红事件" : calendarTypes(types)) + (selected ? "，已选中" : "，点按查看当日明细"));
                cell.setBackground(roundPx(selected ? getColor(R.color.app_primary) : (today ? PRIMARY_CONTAINER : 0x00000000), dimen(R.dimen.ds_calendar_cell_radius), 0, 0));
                cell.setOnClickListener(v -> { selectedDay = cd; render(); });
            } else { cell.setMinimumHeight(dp(52)); cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
            grid.addView(cell, p);
        }
        cal.addView(grid); content.addView(cal, margin(0, 0, 0, 8));

        String selectedDate = selectedDay == null ? "本月派息事件" : String.format(Locale.US, "%04d-%02d-%02d", cur.getYear(), cur.getMonthValue(), selectedDay);
        content.addView(text(selectedDate, 15, INK, true), margin(0, 2, 0, 6));
        boolean shown = false;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null) continue;
            String kinds = selectedDay == null ? (e.optString("pay_date").startsWith(ym) ? "派息日" : "") : calendarKinds(e, cur.getYear(), cur.getMonthValue(), selectedDay);
            if (!kinds.isEmpty()) { addDividendRow(e, true, kinds); shown = true; }
        }
        if (!shown) {
            String emptyMessage = selectedDay == null ? "本月没有派息日期记录；登记日或除息日仍会在日历格中标记。" : "所选日期没有分红事件。可先更新公开数据，或手工录入；来源不可用时不影响账本。";
            content.addView(text(emptyMessage, 12, MUTED, false), margin(0, 6, 0, 12));
        }
        MaterialButton syncButton = actionButton("同步公开分红", true, this::syncPublicDividends);
        syncButton.setContentDescription("更新公开分红数据");
        syncButton.setIconResource(R.drawable.ic_refresh_24); syncButton.setIconSize(dp(18)); syncButton.setIconPadding(dp(6)); syncButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        syncButton.setIconTint(ColorStateList.valueOf(getColor(R.color.app_on_primary)));
        MaterialButton manualButton = actionButton("手工录入", false, this::beginAddDividend);
        manualButton.setContentDescription("手工录入分红事件");
        manualButton.setIconResource(R.drawable.ic_edit_note_24); manualButton.setIconSize(dp(18)); manualButton.setIconPadding(dp(6)); manualButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        manualButton.setIconTint(ColorStateList.valueOf(getColor(R.color.app_primary)));
        syncButton.setSingleLine(false); syncButton.setMaxLines(2); manualButton.setSingleLine(false); manualButton.setMaxLines(2);
        float fontScale = getResources().getConfiguration().fontScale;
        boolean stackedActions = fontScale > 1.45f || getResources().getConfiguration().screenWidthDp < 360;
        float density = getResources().getDisplayMetrics().density;
        float maxTextSp = Math.max(syncButton.getTextSize(), manualButton.getTextSize()) / density;
        int actionHeight = Math.max(dp(56), dp(maxTextSp * (stackedActions ? 1.55f : 2.8f) + 16f));
        syncButton.setMinHeight(actionHeight); syncButton.setMinimumHeight(actionHeight);
        manualButton.setMinHeight(actionHeight); manualButton.setMinimumHeight(actionHeight);
        syncButton.setPaddingRelative(dp(8), dp(6), dp(8), dp(6));
        manualButton.setPaddingRelative(dp(8), dp(6), dp(8), dp(6));
        LinearLayout syncRow = row();
        if (stackedActions) {
            syncRow.setOrientation(LinearLayout.VERTICAL);
            syncRow.addView(syncButton, new LinearLayout.LayoutParams(-1, actionHeight));
            View gap = new View(this); gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            syncRow.addView(gap, new LinearLayout.LayoutParams(1, dp(7)));
            syncRow.addView(manualButton, new LinearLayout.LayoutParams(-1, actionHeight));
        } else {
            syncRow.addView(syncButton, new LinearLayout.LayoutParams(0, actionHeight, 1));
            View gap = new View(this); gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            syncRow.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
            syncRow.addView(manualButton, new LinearLayout.LayoutParams(0, actionHeight, 1));
        }
        content.addView(syncRow, margin(0, 0, 0, 10));
    }

    private CardColumn calendarControlPanel() {
        CardColumn panel = card(); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dimen(R.dimen.ds_card_inset_horizontal), dp(12), dimen(R.dimen.ds_card_inset_horizontal), dp(12));
        panel.addView(calendarViewToggle(), new LinearLayout.LayoutParams(-1, -2));
        return panel;
    }

    private MaterialButtonToggleGroup calendarViewToggle() {
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(this);
        group.setSingleSelection(true); group.setSelectionRequired(true); group.setOrientation(LinearLayout.HORIZONTAL);
        group.setPadding(dp(4), dp(4), dp(4), dp(4));
        group.setBackground(roundPx(getColor(R.color.app_primary_container), dimen(R.dimen.ds_control_group_radius), 0, 0));
        String[] labels = {"月历", "年度总览"};
        int[] icons = {R.drawable.ic_nav_calendar, R.drawable.ic_calendar_view_month_24};
        final int monthButtonId = View.generateViewId(), yearButtonId = View.generateViewId();
        for (int i = 0; i < labels.length; i++) {
            final boolean targetYear = i == 1; boolean checked = calendarYearView == targetYear;
            int id = targetYear ? yearButtonId : monthButtonId;
            MaterialButton button = new MaterialButton(this); button.setId(id); button.setText(labels[i]);
            button.setTextSize(14); button.setAllCaps(false); button.setTypeface(checked ? Typeface.create(appTypeface, Typeface.BOLD) : appTypeface);
            button.setInsetTop(0); button.setInsetBottom(0); button.setInsetLeft(0); button.setInsetRight(0);
            button.setMinHeight(dimen(R.dimen.ds_touch_target_min)); button.setMinimumHeight(dimen(R.dimen.ds_touch_target_min));
            button.setMaxLines(1); button.setCheckable(true);
            button.setCornerRadius(Math.max(0, dimen(R.dimen.ds_control_group_radius) - dp(4)));
            button.setIconResource(icons[i]); button.setIconSize(dp(18)); button.setIconPadding(dp(6)); button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            button.setIconTint(ColorStateList.valueOf(getColor(checked ? R.color.app_on_primary : R.color.app_on_primary_container)));
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(checked ? R.color.app_primary : android.R.color.transparent)));
            button.setTextColor(getColor(checked ? R.color.app_on_primary : R.color.app_on_primary_container)); button.setStrokeWidth(0);
            button.setContentDescription(labels[i] + (checked ? "，已选中" : "，切换视图"));
            group.addView(button, new LinearLayout.LayoutParams(0, dimen(R.dimen.ds_touch_target_min), 1));
            if (checked) group.check(id);
        }
        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean nextYearView = checkedId == yearButtonId;
            if (calendarYearView != nextYearView) { calendarYearView = nextYearView; render(); }
        });
        return group;
    }

    private MaterialButton calendarNavButton(int iconResource, String label, Runnable action) {
        MaterialButton button = new MaterialButton(this); button.setText(""); button.setGravity(Gravity.CENTER);
        button.setInsetTop(0); button.setInsetBottom(0); button.setInsetLeft(0); button.setInsetRight(0);
        button.setMinHeight(dimen(R.dimen.ds_touch_target_min)); button.setMinimumHeight(dimen(R.dimen.ds_touch_target_min));
        button.setMinWidth(dimen(R.dimen.ds_touch_target_min)); button.setMinimumWidth(dimen(R.dimen.ds_touch_target_min));
        button.setIconResource(iconResource); button.setIconSize(dp(18)); button.setIconTint(ColorStateList.valueOf(getColor(R.color.app_primary)));
        button.setCornerRadius(dimen(R.dimen.ds_control_radius)); button.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setStrokeWidth(0); button.setContentDescription(label); button.setOnClickListener(v -> action.run()); return button;
    }

    private MaterialButton calendarContextButton(String label, int iconResource, String description, Runnable action) {
        MaterialButton button = new MaterialButton(this); button.setText(label); button.setTextSize(12); button.setAllCaps(false);
        button.setInsetTop(0); button.setInsetBottom(0); button.setInsetLeft(0); button.setInsetRight(0);
        button.setMinHeight(dimen(R.dimen.ds_touch_target_min)); button.setMinimumHeight(dimen(R.dimen.ds_touch_target_min));
        button.setMinWidth(dimen(R.dimen.ds_touch_target_min)); button.setMinimumWidth(dimen(R.dimen.ds_touch_target_min)); button.setGravity(Gravity.CENTER);
        button.setIconResource(iconResource); button.setIconSize(dp(16)); button.setIconPadding(dp(4)); button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconTint(ColorStateList.valueOf(getColor(R.color.app_primary))); button.setTextColor(getColor(R.color.app_primary));
        button.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)); button.setStrokeWidth(0);
        button.setPaddingRelative(dp(4), 0, dp(4), 0);
        button.setContentDescription(description); button.setOnClickListener(v -> action.run()); return button;
    }

    private void showCalendarYear() {
        LinearLayout yearHead = row(); yearHead.setGravity(Gravity.CENTER_VERTICAL);
        yearHead.addView(calendarNavButton(R.drawable.ic_chevron_left_24, "上一年", () -> shiftYear(-1)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL); titleBox.setGravity(Gravity.CENTER);
        TextView title = text(year + " 年", 18, INK, true); title.setGravity(Gravity.CENTER); titleBox.addView(title);
        MaterialButton current = calendarContextButton("回到今年", R.drawable.ic_today_24, "回到今年", () -> { year = LocalDate.now().getYear(); calendarYearView = true; render(); }); titleBox.addView(current);
        yearHead.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        yearHead.addView(calendarNavButton(R.drawable.ic_chevron_right_24, "下一年", () -> shiftYear(1)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(yearHead, margin(0, 0, 0, 8));

        JSONArray rows = db.dividendsInYear(selectedAccount, year); ArrayList<CalendarYearSummary.Event> source = new ArrayList<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) {
            JSONObject e = rows.optJSONObject(i); if (e != null) source.add(new CalendarYearSummary.Event(e.optString("pay_date"), e.optString("currency", "CNY"), e.optString("status"), "received".equals(e.optString("status")) && !e.isNull("received_amount") ? e.optDouble("received_amount") : e.optDouble("total")));
        }
        CalendarYearSummary summary = CalendarYearSummary.calculate(year, source);
        CardColumn overview = card(); overview.setOrientation(LinearLayout.VERTICAL); overview.addView(text("年度收息概况", 15, INK, true));
        overview.addView(text("已到账  " + formatAmounts(summary.receivedByCurrency()), 12, DIVIDEND, true), margin(0, 8, 0, 0));
        overview.addView(text("预计 / 待收  " + formatAmounts(summary.estimatedByCurrency()), 12, INK, true), margin(0, 4, 0, 0));
        overview.addView(text("按币种分别统计，不做汇率换算；未到账事件保留预计口径，正式公告、历史推算与手工记录请在月历查看。", 10, MUTED, false), margin(0, 6, 0, 0));
        content.addView(overview, margin(0, 0, 0, 10));

        LinearLayout legend = row(); legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(text("■ 已到账", 10, getColor(R.color.app_calendar_received), true));
        legend.addView(text("    ■ 预计 / 待收", 10, getColor(R.color.app_calendar_estimated), true));
        content.addView(legend, margin(2, 0, 0, 6));
        content.addView(text("12 个月收息明细", 15, INK, true), margin(0, 2, 0, 5));
        if (!summary.hasEvents()) {
            content.addView(text("本年度暂无收息事件。可切回月历选择月份，查看登记、除息与派息日期。", 12, MUTED, false), margin(0, 6, 0, 12));
        }
        for (int monthIndex = 1; monthIndex <= 12; monthIndex++) addCalendarYearMonth(summary, monthIndex);
    }

    private void addCalendarYearMonth(CalendarYearSummary summary, int monthIndex) {
        CalendarYearSummary.Month monthSummary = summary.month(monthIndex);
        CardColumn monthCard = card(); monthCard.setOrientation(LinearLayout.VERTICAL); monthCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout heading = row(); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(monthIndex + " 月", 14, INK, true); heading.addView(label, new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView open = text("查看月历", 11, ACCENT, true); open.setGravity(Gravity.CENTER); open.setMinHeight(dp(48));
        setStartIcon(open, R.drawable.ic_nav_calendar, ACCENT, 16); open.setContentDescription("打开" + year + "年" + monthIndex + "月月历"); heading.addView(open);
        monthCard.addView(heading);
        if (monthSummary.currencyCodes().isEmpty()) {
            monthCard.addView(text("暂无事件", 11, MUTED, false), margin(0, 2, 0, 0));
        } else {
            for (String currency : monthSummary.currencyCodes()) {
                CalendarYearSummary.Amounts amounts = monthSummary.amounts(currency);
                monthCard.addView(text(currency + "  已到账 " + amount(currency, amounts.received) + "  ·  预计 / 待收 " + amount(currency, amounts.estimated), 10, INK, false), margin(0, 1, 0, 0));
                monthCard.addView(calendarAmountBar(amounts, summary.maxMonthlyTotal(currency)), margin(0, 3, 0, 4));
            }
        }
        monthCard.setClickable(true); monthCard.setFocusable(true); monthCard.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(getColor(R.color.app_primary_container)), null, roundDp(android.graphics.Color.WHITE, 12, 0, 0)));
        monthCard.setContentDescription(year + "年" + monthIndex + "月，" + monthSummary.eventCount() + "项收息事件，" + calendarMonthSpeech(monthSummary) + "，点按查看月历");
        monthCard.setOnClickListener(v -> { year = summary.year; month = monthIndex; selectedDay = null; calendarYearView = false; render(); });
        content.addView(monthCard, margin(0, 0, 0, 6));
    }

    private View calendarAmountBar(CalendarYearSummary.Amounts amounts, double maximum) {
        LinearLayout track = row(); track.setOrientation(LinearLayout.HORIZONTAL); track.setMinimumHeight(dp(7)); track.setBackground(roundDp(getColor(R.color.app_outline_variant), 5, 0, 0));
        double total = amounts.received + amounts.estimated;
        if (total <= 0 || maximum <= 0) { track.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return track; }
        float fraction = (float) Math.max(.025d, Math.min(1d, total / maximum));
        LinearLayout fill = row(); fill.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, dp(7), fraction); track.addView(fill, fillParams);
        if (amounts.received > 0) { View received = new View(this); received.setBackground(roundDp(getColor(R.color.app_calendar_received), 5, 0, 0)); fill.addView(received, new LinearLayout.LayoutParams(0, dp(7), (float) (amounts.received / total))); }
        if (amounts.estimated > 0) { View expected = new View(this); expected.setBackground(roundDp(getColor(R.color.app_calendar_estimated), 5, 0, 0)); fill.addView(expected, new LinearLayout.LayoutParams(0, dp(7), (float) (amounts.estimated / total))); }
        if (fraction < 1f) track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(7), 1f - fraction));
        return track;
    }

    private String calendarMonthSpeech(CalendarYearSummary.Month monthSummary) {
        ArrayList<String> parts = new ArrayList<>();
        for (String currency : monthSummary.currencyCodes()) {
            CalendarYearSummary.Amounts amounts = monthSummary.amounts(currency);
            parts.add(currency + "已到账" + amount(currency, amounts.received) + "，预计或待收" + amount(currency, amounts.estimated));
        }
        return parts.isEmpty() ? "暂无收息金额" : android.text.TextUtils.join("；", parts);
    }

    private void shiftYear(int delta) {
        long next = (long) year + delta;
        if (!CalendarYearSummary.isSupportedYear(next)) { toast("已到年份可查看范围边界"); return; }
        year = (int) next; calendarYearView = true; render();
    }
    private void showCurrentMonth() {
        LocalDate today = LocalDate.now(); year = today.getYear(); month = today.getMonthValue(); selectedDay = today.getDayOfMonth(); calendarYearView = false; render();
    }
    private void addDividendRow(JSONObject e, boolean allowMarkReceived) {
        addDividendRow(e, allowMarkReceived, "派息日");
    }
    private void addDividendRow(JSONObject e, boolean allowMarkReceived, String eventKind) {
        if (e == null) return; CardColumn item = card(); item.setOrientation(LinearLayout.VERTICAL);
        String dateValue = "登记日".equals(eventKind) ? e.optString("record_date") : "除息日".equals(eventKind) ? e.optString("ex_date") : e.optString("pay_date");
        String dataClass = e.optString("data_class", "manual"); boolean received = "received".equals(e.optString("status"));
        int badgeColor = "登记日".equals(eventKind) ? getColor(R.color.app_calendar_record_date) : "除息日".equals(eventKind) ? getColor(R.color.app_calendar_ex_date) : getColor(R.color.app_calendar_pay_date);
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL); TextView date = text(shortDate(dateValue), 12, badgeColor, true); date.setPadding(dp(7), dp(7), dp(7), dp(7)); date.setBackground(roundPx(PRIMARY_CONTAINER, dimen(R.dimen.ds_event_badge_radius), 0, 0)); top.addView(date);
        LinearLayout desc = new LinearLayout(this); desc.setOrientation(LinearLayout.VERTICAL); desc.setPadding(dp(9), 0, 0, 0); desc.addView(text(e.optString("holding_name"), 13, INK, true));
        String statusLabel = DividendCalendarRules.statusLabel(dataClass, e.optString("status"));
        String sourceLabel = "manual".equals(e.optString("source", "manual")) ? "手工录入" : e.optString("source", "");
        desc.addView(text(sourceLabel + " · " + eventKind + " · " + statusLabel + (e.optString("note").isEmpty() ? "" : " · " + e.optString("note")), 10, MUTED, false), margin(0, 2, 0, 0)); top.addView(desc, new LinearLayout.LayoutParams(0, -2, 1)); item.addView(top);
        double gross = e.optDouble("total"); String curr = e.optString("currency", "CNY");
        boolean taxKnown = e.optInt("tax_known") == 1; double taxRate = clampRate(e.optDouble("tax_rate"));
        if ("A股".equals(e.optString("holding_market"))) {
            String taxMode = e.optString("holding_tax_mode", "manual");
            String recordDate = e.optString("record_date", "").trim();
            JSONObject taxHolding = new JSONObject();
            try {
                taxHolding.put("_id", e.optLong("holding_id")); taxHolding.put("market", "A股");
                taxHolding.put("tax_mode", taxMode); taxHolding.put("tax_rate", e.optDouble("tax_rate", 0d));
                taxHolding.put("quantity", e.optDouble("holding_quantity", 0d));
            } catch (JSONException invalidTaxHolding) { taxHolding = null; }
            boolean manualTaxMode = "manual".equals(taxMode);
            taxKnown = taxHolding != null && (manualTaxMode || !recordDate.isEmpty()) && taxEstimateKnown(taxHolding, recordDate);
            taxRate = taxKnown ? effectiveTaxRate(taxHolding, recordDate) : 0d;
        }
        double net = FinanceMath.afterTax(gross, taxRate);
        boolean actualKnown = !e.isNull("received_amount"); double actual = actualKnown ? e.optDouble("received_amount") : gross;
        String quantityText = e.optDouble("record_quantity") > 0 ? compact(e.optDouble("record_quantity")) + " 股" : "登记股数待校正";
        String perShare = amount(curr, e.optDouble("amount_per_share"));
        String payDate = e.optString("pay_date_estimated").equals("1") || e.optInt("pay_date_estimated") == 1 ? "约 " : "";
        String amountLabel = received ? (actualKnown ? "实际到账 " : "到账记录额 ") + amount(curr, actual) : "预计记录额 " + amount(curr, gross);
        item.addView(text("每股 " + perShare + "  ·  登记数量 " + quantityText + "  ·  " + amountLabel, 11, INK, true), margin(0, 8, 0, 0));
        item.addView(text("派息日 " + payDate + shortDate(e.optString("pay_date")) + "  ·  " + (taxKnown ? "税后情景 " + amount(curr, net) : "税务状态未知，暂不估税（不等于免税）"), 10, MUTED, false), margin(0, 4, 0, 0));
        content.addView(item, margin(0, 4, 0, 4));
        if (allowMarkReceived && !received) { content.addView(actionButton("确认已到账", false, () -> confirmMarkReceived(e)), margin(62, 0, 0, 7)); }
        else if (allowMarkReceived && received && !actualKnown) { content.addView(actionButton("补录实际到账金额", false, () -> confirmMarkReceived(e)), margin(62, 0, 0, 7)); }
    }
    private void confirmMarkReceived(JSONObject e) {
        boolean alreadyReceived = "received".equals(e.optString("status"));
        EditText actual = numberEdit(e.isNull("received_amount") ? "" : String.valueOf(e.optDouble("received_amount")), "留空表示未记录实收金额");
        LinearLayout f = form(); f.addView(text("按券商实际入账填写税后现金金额。留空仍可确认到账，但这笔分红不会用于分红摊薄成本；之后可补录。", 11, MUTED, false));
        f.addView(field("实际到账金额（可选）", actual));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle(alreadyReceived ? "补录实际到账金额" : "确认收到这笔分红？")
                .setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton(alreadyReceived ? "保存金额" : "确认到账", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String raw = actual.getText().toString().trim(); Double amount = null;
            if (!raw.isEmpty()) { try { amount = Double.parseDouble(raw); } catch (NumberFormatException ignored) { amount = -1d; } }
            if (amount != null && (!Double.isFinite(amount) || amount < 0)) { actual.setError("实际到账金额不能为负或无效"); return; }
            if (alreadyReceived && amount == null) { actual.setError("请输入实际到账金额"); return; }
            db.markDividendReceived(e.optLong("_id"), amount); d.dismiss(); render(); toast(alreadyReceived ? "实收金额已保存" : "已确认到账");
        })); showFormDialog(d);
    }
    private YearMonth calendarMonth() {
        try { return YearMonth.of(year, month); }
        catch (DateTimeException invalidMonth) { YearMonth current = YearMonth.now(); year = current.getYear(); month = current.getMonthValue(); selectedDay = null; return current; }
    }
    private void shiftMonth(int delta) {
        try { YearMonth next = calendarMonth().plusMonths(delta); if (!CalendarYearSummary.isSupportedYear(next.getYear())) { toast("已到日历可查看范围边界"); return; } year = next.getYear(); month = next.getMonthValue(); selectedDay = null; calendarYearView = false; render(); }
        catch (DateTimeException outOfRange) { toast("已到日历可查看范围边界"); }
    }

    private void markCalendarDate(Map<Integer, int[]> marks, String value, String ym, int type) {
        if (marks == null || type < 0 || type >= 3 || ym == null) return;
        LocalDate d = parseDate(value); if (d == null || !d.toString().startsWith(ym)) return;
        int[] bits = marks.get(d.getDayOfMonth()); if (bits == null) bits = new int[3]; bits[type]++;
        marks.put(d.getDayOfMonth(), bits);
    }
    private String calendarTypes(int[] bits) {
        if (bits == null || bits.length < 3) return "，无分红事件";
        ArrayList<String> labels = new ArrayList<>(); if (bits[0] > 0) labels.add("股权登记日 " + bits[0] + " 项");
        if (bits[1] > 0) labels.add("除权除息日 " + bits[1] + " 项"); if (bits[2] > 0) labels.add("派息日 " + bits[2] + " 项");
        return "，" + android.text.TextUtils.join("、", labels);
    }
    private String calendarKinds(JSONObject e, int y, int m, int d) {
        if (e == null) return "";
        String selected = String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
        return DividendCalendarRules.kindsForDate(e.optString("record_date"), e.optString("ex_date"), e.optString("pay_date"), selected);
    }
    private void syncPublicDividends() {
        JSONArray holdings = db.holdings(selectedAccount);
        if (holdings.length() == 0) { toast("先添加证券持仓；公开数据请求只使用证券代码"); return; }
        content.addView(text("正在后台更新公开公告；只发送证券代码，不发送持仓数量、成本、交易流水或账户信息。", 11, MUTED, false), margin(0, 4, 0, 8));
        ArrayList<JSONObject> list = new ArrayList<>(); for (int i = 0; i < holdings.length(); i++) list.add(holdings.optJSONObject(i));
        dataExecutor.execute(() -> {
            int inserted = 0, skipped = 0, failed = 0; boolean stale = false; long latestUpdated = 0L; String warning = "";
            LocalDate today = LocalDate.now();
            for (JSONObject h : list) {
                String market = h.optString("market"), code = h.optString("code");
                if (code.isEmpty() || !("A股".equals(market) || "港股".equals(market))) continue;
                MarketDataClient.Snapshot<MarketDataClient.Dividend> snap = marketData.dividends(market, code);
                stale |= snap.stale; latestUpdated = Math.max(latestUpdated, snap.updatedAt); if (snap.rows.isEmpty()) { failed++; if (snap.warning != null) warning = snap.warning; continue; }
                for (MarketDataClient.Dividend ev : snap.rows) {
                    LocalDate record = parseDate(ev.recordDate), ex = parseDate(ev.exDate), pay = parseDate(ev.payDate);
                    LocalDate latest = pay != null ? pay : ex != null ? ex : record;
                    if (latest == null || latest.isBefore(today.minusYears(5)) || ev.payDate.isEmpty()) { skipped++; continue; }
                    boolean future = (record != null && !record.isBefore(today)) || (ex != null && !ex.isBefore(today)) || (pay != null && !pay.isBefore(today));
                    String dataClass = future ? "announced" : "historical_estimate";
                    double recordQty = ev.recordDate.isEmpty() ? (future ? h.optDouble("quantity") : 0d)
                            : db.estimatedQuantityAt(h.optLong("_id"), ev.recordDate);
                    if (recordQty <= 0 && future) recordQty = h.optDouble("quantity");
                    if (recordQty <= 0) { skipped++; continue; }
                    String taxDate = ev.recordDate.isEmpty() ? today.toString() : ev.recordDate;
                    double rate = effectiveTaxRate(h, taxDate); boolean known = taxEstimateKnown(h, taxDate);
                    String note = "同步于 " + timeLabel(snap.updatedAt) + "；" + (ev.progress.isEmpty() ? "公开报表未提供方案状态" : ev.progress);
                    if (ev.payDateEstimated) note += "；A股无派息日字段，按除息日+1日推算";
                    if (snap.stale) note += "；离线缓存，时间 " + timeLabel(snap.updatedAt);
                    if ("historical_estimate".equals(dataClass)) note += "；历史登记持仓根据本机初始日期/交易流水估算，需核对";
                    boolean ok = db.addPublicDividend(h.optLong("_id"), h.optLong("account_id"), code, ev.sourceKey,
                            dataClass, ev.perShare, recordQty, ev.currency, ev.recordDate, ev.exDate, ev.payDate,
                            ev.payDateEstimated, rate, known, note);
                    if (ok) inserted++; else skipped++;
                }
            }
            final int n = inserted, s = skipped, f = failed; final boolean oldCache = stale; final long dataUpdatedAt = latestUpdated; final String why = warning;
            runOnUiThread(() -> { if (isFinishing()) return;
                if (dataUpdatedAt > 0L) { db.saveSetting("dividend_sync_at", String.valueOf(dataUpdatedAt)); db.saveSetting("dividend_sync_stale", String.valueOf(oldCache)); }
                render();
                String msg = "同步完成：新增或更新 " + n + " 项，跳过/重复 " + s + " 项" + (f > 0 ? "，" + f + " 个请求无数据" : "") + (oldCache ? "；部分来自旧缓存" : "");
                if (!why.isEmpty()) msg += "；" + why; toast(msg); });
        });
    }

    private void showPlan() {
        pageTitle("复利与开支目标", "单币种、固定年化参数的情景测算；不代表实际收益");
        CardColumn card = card(); card.setOrientation(LinearLayout.VERTICAL); card.addView(text("DRIP 参数", 17, INK, true));
        EditText initial = numberEdit(setting("drip_initial", "0"), "期初资产");
        EditText yield = numberEdit(String.valueOf(settingDouble("drip_yield", .04) * 100d), "预期年化股息率，例如 4");
        EditText contribution = numberEdit(setting("drip_contribution", "0"), "每年追加投入");
        EditText reinvest = numberEdit(String.valueOf(settingDouble("drip_reinvest", 1) * 100d), "红利再投资比例 0–100");
        EditText years = numberEdit(setting("drip_years", "20"), "预测 1–30 年");
        EditText tax = numberEdit(String.valueOf(settingDouble("drip_tax", .10) * 100d), "综合税率假设 0–100");
        MiuixListView currency = spinner(CURRENCIES); selectSpinner(currency, setting("drip_currency", "CNY"));
        card.addView(field("期初资产（可手工填持仓成本/其他金额；没有市值同步）", initial)); card.addView(field("综合预期年化股息率（%）", yield));
        card.addView(field("年度追加投入（与下方选择币种相同）", contribution)); card.addView(field("分红再投资比例（%）", reinvest));
        card.addView(field("预测年限", years)); card.addView(field("综合税率假设（%）", tax)); card.addView(field("币种（不同币种不换算）", currency));
        card.addView(text("模型按期初资产产生年度分红：税后分红 × 再投资比例计入期末资产；定投在年末投入。结果是数学情景，不是回报预测。", 11, MUTED, false), margin(0, 2, 0, 9));
        card.addView(actionButton("保存参数并测算", true, () -> {
            double a = parseNumber(initial), r = parseNumber(yield), c = parseNumber(contribution), p = parseNumber(reinvest), y = parseNumber(years), t = parseNumber(tax);
            if (a < 0 || r < 0 || c < 0 || p < 0 || p > 100 || y < 1 || y > 30 || y != Math.rint(y) || t < 0 || t > 100) { toast("请检查参数范围"); return; }
            db.saveSetting("drip_initial", String.valueOf(a)); db.saveSetting("drip_yield", String.valueOf(r / 100d)); db.saveSetting("drip_contribution", String.valueOf(c));
            db.saveSetting("drip_reinvest", String.valueOf(p / 100d)); db.saveSetting("drip_years", String.valueOf((int) y)); db.saveSetting("drip_tax", String.valueOf(t / 100d)); db.saveSetting("drip_currency", selectedItem(currency));
            hideKeyboard(null); render(); toast("测算已更新");
        })); content.addView(card, margin(0, 0, 0, 11));
        showDripResults();
        content.addView(divider()); sectionHeader("生活支出覆盖目标", "添加目标", this::showGoalEditor);
        content.addView(text("按相同币种计算年化支出与预估净收入；未知税务状态的持仓不计入税后覆盖。币种之间不做汇率合并。", 11, MUTED, false), margin(0, 0, 0, 5)); showGoals();
    }
    private void showDripResults() {
        String cur = setting("drip_currency", "CNY"); double a = settingDouble("drip_initial", 0), r = settingDouble("drip_yield", .04), c = settingDouble("drip_contribution", 0);
        double p = settingDouble("drip_reinvest", 1), t = settingDouble("drip_tax", .10); int years = (int) Math.max(1, Math.min(30, settingDouble("drip_years", 20)));
        ArrayList<FinanceMath.DripYear> rows = new ArrayList<>(FinanceMath.project(a, r, c, p, years, t));
        double target = totalGoalForCurrency(cur); int coveredYear = -1; for (FinanceMath.DripYear row : rows) if (target > 0 && row.netDividend >= target) { coveredYear = row.year; break; }
        CardColumn result = card(); result.setOrientation(LinearLayout.VERTICAL); result.addView(text("逐年测算 · " + cur, 16, INK, true));
        result.addView(text(target > 0 ? (coveredYear > 0 ? "按当前同币种目标，预测第 " + coveredYear + " 年当年净分红达到年支出 " + amount(cur, target) : "预测期内未达到同币种年支出 " + amount(cur, target)) : "尚未设置同币种支出目标", 11, ACCENT, true), margin(0, 5, 0, 8));
        double max = 1; for (FinanceMath.DripYear row : rows) max = Math.max(max, row.closing);
        for (FinanceMath.DripYear row : rows) {
            int bars = (int) Math.max(1, Math.round(row.closing / max * 16)); StringBuilder bar = new StringBuilder(); for (int i = 0; i < bars; i++) bar.append('▰');
            LinearLayout yr = new LinearLayout(this); yr.setOrientation(LinearLayout.VERTICAL); yr.setPadding(0, dp(6), 0, dp(6));
            yr.addView(text("第 " + row.year + " 年  ·  年末资产 " + amount(cur, row.closing) + "  " + bar, 12, INK, true));
            yr.addView(text("期初 " + amount(cur, row.opening) + "  ·  税前分红 " + amount(cur, row.grossDividend) + "  ·  税后 " + amount(cur, row.netDividend), 10, MUTED, false), margin(0, 3, 0, 0));
            yr.addView(text("再投资 " + amount(cur, row.reinvested) + "  ·  追加投入 " + amount(cur, row.contribution) + "  ·  累计税后分红 " + amount(cur, row.cumulativeNet), 10, MUTED, false), margin(0, 2, 0, 0)); result.addView(yr); if (row.year < rows.size()) result.addView(divider());
        }
        content.addView(result, margin(0, 0, 0, 10));
    }
    private void showGoals() {
        JSONArray goals = db.goals(); if (goals.length() == 0) { content.addView(text("还没有支出目标。", 12, MUTED, false), margin(2, 2, 0, 10)); return; }
        Map<String, Double> netIncome = estimatedIncomeByCurrency(true);
        for (int i = 0; i < goals.length(); i++) {
            JSONObject g = goals.optJSONObject(i); double annual = safeAnnualExpense(g.optString("period"), g.optDouble("amount")); String curr = g.optString("currency", "CNY");
            double ratio = FinanceMath.coveragePercent(netIncome.getOrDefault(curr, 0d), annual); CardColumn item = card(); item.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row = row(); row.addView(text(g.optString("name"), 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1)); row.addView(actionButton("•••", false, () -> goalMenu(g))); item.addView(row);
            String per = g.optString("period"); item.addView(text(per + "支出 " + amount(curr, g.optDouble("amount")) + " · 年化 " + amount(curr, annual), 11, MUTED, false), margin(0, 5, 0, 0));
            item.addView(text("同币种预计净收入覆盖 " + percent(ratio / 100d) + "  ·  " + amount(curr, netIncome.getOrDefault(curr, 0d)) + " / 年", 12, ACCENT, true), margin(0, 5, 0, 0));
            LinearLayout progress = new LinearLayout(this); progress.setOrientation(LinearLayout.HORIZONTAL); progress.setBackground(roundDp(getColor(R.color.app_progress_track), 8, 0, 0)); progress.setClipToOutline(true);
            float fill = (float) Math.min(100d, Math.max(0d, ratio)); progress.addView(new View(this), new LinearLayout.LayoutParams(0, dp(5), Math.max(.01f, fill)));
            if (fill < 100f) progress.addView(new View(this), new LinearLayout.LayoutParams(0, dp(5), Math.max(.01f, 100f - fill)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(5)); bp.topMargin = dp(7); item.addView(progress, bp); content.addView(item, margin(0, 4, 0, 6));
        }
    }
    private void goalMenu(JSONObject goal) { new MaterialAlertDialogBuilder(this).setTitle(goal.optString("name")).setItems(new String[]{"编辑", "删除"}, (d, w) -> { if (w == 0) showGoalEditor(goal); else new MaterialAlertDialogBuilder(this).setTitle("删除支出目标？").setNegativeButton("取消", null).setPositiveButton("删除", (x, y) -> { db.deleteGoal(goal.optLong("_id")); render(); }).show(); }).show(); }
    private void showGoalEditor() { showGoalEditor(null); }
    private void showGoalEditor(JSONObject old) {
        EditText name = edit(old == null ? "" : old.optString("name"), "例如：房租、伙食"); MiuixListView period = spinner(new String[]{"日", "月", "年"}); if (old != null) selectSpinner(period, old.optString("period", "月"));
        EditText amount = numberEdit(old == null ? "" : String.valueOf(old.optDouble("amount")), "支出金额"); MiuixListView currency = spinner(CURRENCIES); if (old != null) selectSpinner(currency, old.optString("currency", "CNY"));
        LinearLayout f = form(); f.addView(field("目标名称", name)); f.addView(field("周期", period)); f.addView(field("金额", amount)); f.addView(field("币种", currency));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(old == null ? "新增支出目标" : "编辑支出目标").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            double a = parseNumber(amount); if (name.getText().toString().trim().isEmpty() || a <= 0) { amount.setError("请填写名称和大于 0 的金额"); return; }
            db.saveGoal(old == null ? 0 : old.optLong("_id"), name.getText().toString(), selectedItem(period), a, selectedItem(currency)); hideKeyboard(dialog); dialog.dismiss(); render();
        })); showFormDialog(dialog);
    }

    private void showMore() {
        showAccountCard(); showTaxSettingsCard();
        CardColumn stats = card(); stats.setOrientation(LinearLayout.VERTICAL); stats.addView(text("统计报表", 16, INK, true));
        stats.addView(text("预计年分红、成本 YoC、日/月均收入、累计已收与贡献排行；成本不等于市值。", 12, MUTED, false), margin(0, 5, 0, 10));
        stats.addView(actionButton("打开统计报表", true, () -> { activeTab = "stats"; render(); })); content.addView(stats, margin(0, 0, 0, 10));
        showCatalogCard(); showBackupCard(); showOpenSourceCard(); showTransactions(); showBoundaries();
    }
    private void showStatsPage() {
        pageTitle("统计报表", "按用户账本与税务设置估算；公开行情用于持仓市值参考"); addTaxToggle();
        Map<String, Double> costs = costByCurrency(), selectedAnnual = estimatedIncomeByCurrency(taxNet), gross = estimatedIncomeByCurrency(false), net = estimatedIncomeByCurrency(true);
        content.addView(text("持仓成本  " + formatAmounts(costs), 13, INK, true), margin(0, 4, 0, 6));
        JSONArray selectedHoldings = db.holdings(selectedAccount);
        content.addView(text("持仓市值（按币种）  " + formatAmounts(marketValueByCurrency(selectedHoldings)), 13, INK, true), margin(0, 0, 0, 5));
        content.addView(text("浮动盈亏（按币种）  " + formatAmounts(floatingProfitByCurrency(selectedHoldings)), 13, INK, true), margin(0, 0, 0, 8));
        content.addView(text("行情取最近已收盘价；缺行情时按成本估值且不计浮动盈亏。外币未折算。", 10, MUTED, false), margin(0, 0, 0, 8));
        content.addView(text("预计年分红 · 税前  " + formatAmounts(gross), 13, INK, true), margin(0, 0, 0, 5));
        content.addView(text("预计年分红 · 税后估算  " + formatAmounts(net), 13, DIVIDEND, true), margin(0, 0, 0, 5));
        if (taxNet && hasUnknownTaxEstimate()) content.addView(text("税务身份/渠道未确认的持仓未纳入税后合计；未知不代表免税，也不会以 0% 代算。", 10, MUTED, false), margin(0, 0, 0, 6));
        content.addView(text("当前口径日均  " + formatAmounts(scaleMap(selectedAnnual, 1d / 365d)), 12, MUTED, false), margin(0, 0, 0, 5));
        content.addView(text("当前口径月均  " + formatAmounts(scaleMap(selectedAnnual, 1d / 12d)), 12, MUTED, false), margin(0, 0, 0, 12));
        TreeMap<String, Double> yocs = new TreeMap<>(); for (String c : costs.keySet()) if (costs.get(c) > 0 && selectedAnnual.containsKey(c)) yocs.put(c, selectedAnnual.get(c) / costs.get(c));
        content.addView(text("综合成本收益率（YoC）", 15, INK, true)); content.addView(text(yocs.isEmpty() ? "—" : formatPercentMap(yocs), 13, DIVIDEND, true), margin(0, 4, 0, 12));
        content.addView(text("累计已确认分红", 15, INK, true)); content.addView(text(formatAmounts(receivedAllTime()), 13, DIVIDEND, true), margin(0, 4, 0, 12));
        sectionHeader("各标的年分红贡献", "持仓", () -> { activeTab = "holdings"; render(); });
        ArrayList<JSONObject> list = new ArrayList<>(); JSONArray holdings = db.holdings(selectedAccount); for (int i = 0; i < holdings.length(); i++) list.add(holdings.optJSONObject(i));
        Collections.sort(list, (a, b) -> Double.compare(annualForSort(b, taxNet), annualForSort(a, taxNet)));
        if (list.isEmpty()) content.addView(text("尚无持仓数据。", 12, MUTED, false));
        for (int i = 0; i < list.size(); i++) {
            JSONObject h = list.get(i); double annual = annualOf(h, taxNet), totalCost = h.optDouble("quantity") * h.optDouble("cost");
            String annualLabel = !Double.isFinite(annual) ? "税务未确认" : amount(h.optString("currency"), annual) + "/年";
            String yoc = Double.isFinite(annual) && totalCost > 0 ? percent(annual / totalCost) : "—";
            content.addView(text((i + 1) + ". " + h.optString("name") + " · " + annualLabel + " · YoC " + yoc, 12, INK, false), margin(0, 6, 0, 1));
        }
        LinearLayout back = row(); back.addView(actionButton("返回统计与设置", false, () -> { activeTab = "more"; render(); }), margin(0, 14, 0, 0)); content.addView(back);
    }
    private void showAccountCard() {
        CardColumn card = card(); card.setOrientation(LinearLayout.VERTICAL); card.addView(text("账户管理", 16, INK, true)); card.addView(text("当前筛选范围：" + accountLabel() + " · 在总览切换；仅用于本地筛选，不连接券商。", 11, MUTED, false), margin(0, 4, 0, 8));
        LinearLayout r = row(); r.addView(actionButton("新增账户", false, this::showAddAccount), new LinearLayout.LayoutParams(-1, dp(48))); card.addView(r);
        JSONArray acc = db.accounts(); for (int i = 0; i < acc.length(); i++) { JSONObject a = acc.optJSONObject(i); card.addView(text("• " + a.optString("name") + (selectedAccount == a.optLong("_id") ? " · 当前" : ""), 11, MUTED, false), margin(1, 6, 0, 0)); }
        content.addView(card, margin(0, 0, 0, 10));
    }
    private void showTaxSettingsCard() {
        CardColumn card = card(); card.setOrientation(LinearLayout.VERTICAL); card.addView(text("可编辑税率默认假设", 16, INK, true));
        card.addView(text("全局税务身份/账户通道仅由你选择，不按设备位置推断。自动模式按 A 股交易批次估算；港股普通账户、美股协定资格和基金分配未知时只显示税前参考。", 11, MUTED, false), margin(0, 5, 0, 8));
        card.addView(text("身份：" + setting("tax_identity", "未确认") + "\n港股：" + setting("tax_hk_channel", "未确认") + "\n美股：" + setting("tax_us_status", "未确认"), 11, ACCENT, true));
        card.addView(text("A股短/中/长情景参数：" + percent(settingDouble("tax_a_short", .2)) + " / " + percent(settingDouble("tax_a_mid", .1)) + " / " + percent(settingDouble("tax_a_long", 0)), 10, MUTED, false), margin(0, 5, 0, 0));
        card.addView(actionButton("编辑税率假设", false, this::showTaxSettingsDialog), margin(0, 8, 0, 0));
        content.addView(card, margin(0, 0, 0, 10));
    }
    private void showTaxSettingsDialog() {
        MiuixListView identity = spinner(new String[]{"未确认", "中国内地个人（需核对适用范围）", "其他税务身份/跨境情况"});
        MiuixListView hkChannel = spinner(new String[]{"未确认", "港股通", "普通香港券商账户"});
        MiuixListView usStatus = spinner(new String[]{"未确认", "符合协定且 W-8BEN 已被券商接受", "不适用协定或未提交有效表格"});
        selectSpinner(identity, setting("tax_identity", "未确认")); selectSpinner(hkChannel, setting("tax_hk_channel", "未确认")); selectSpinner(usStatus, setting("tax_us_status", "未确认"));
        EditText aShort = percentEdit("tax_a_short", .2), aMid = percentEdit("tax_a_mid", .1), aLong = percentEdit("tax_a_long", 0);
        EditText hk = percentEdit("tax_default_hk", .2), us = percentEdit("tax_default_us", .1), fund = percentEdit("tax_default_fund", 0), etf = percentEdit("tax_default_etf", 0);
        LinearLayout f = form(); f.addView(field("A股短期（≤1个月）%", aShort)); f.addView(field("A股中期（>1个月且≤1年）%", aMid)); f.addView(field("A股长期（>1年）%", aLong));
        f.addView(field("税务身份（不自动推断）", identity)); f.addView(field("港股持有渠道", hkChannel)); f.addView(field("美国股息协定/W-8BEN 状态", usStatus));
        f.addView(field("手动持仓/港股情景税率 %", hk)); f.addView(field("手动持仓美股情景税率 %", us)); f.addView(field("基金默认情景 %", fund)); f.addView(field("ETF 默认情景 %", etf));
        f.addView(text("税率仅用于估算。A股差别税率会在卖出时结算，本应用按本地交易流水/初始日期 FIFO 推测登记日批次；港股仅在你明确选择港股通时采用简化 20% 情景；美股只有明确选择协定资格并确认 W‑8BEN 被券商接受才采用 10% 情景。未知时不自动扣税，绝不表示免税。基金/ETF 分配以产品和券商税单为准。", 11, MUTED, false));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("编辑税率默认假设").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            EditText[] es = {aShort, aMid, aLong, hk, us, fund, etf}; String[] keys = {"tax_a_short", "tax_a_mid", "tax_a_long", "tax_default_hk", "tax_default_us", "tax_default_fund", "tax_default_etf"};
            for (EditText e : es) if (parseNumber(e) < 0 || parseNumber(e) > 100) { e.setError("税率需在 0–100% 之间"); return; }
            for (int i = 0; i < es.length; i++) db.saveSetting(keys[i], String.valueOf(parseNumber(es[i]) / 100d));
            db.saveSetting("tax_identity", selectedItem(identity)); db.saveSetting("tax_hk_channel", selectedItem(hkChannel)); db.saveSetting("tax_us_status", selectedItem(usStatus));
            d.dismiss(); render(); toast("税率默认假设已保存");
        })); showFormDialog(d);
    }
    private EditText percentEdit(String key, double fallback) { return numberEdit(String.valueOf(settingDouble(key, fallback) * 100d), "0–100"); }
    private void showCatalogCard() {
        CardColumn c = card(); c.setOrientation(LinearLayout.VERTICAL); c.addView(text("证券搜索与数据源", 16, INK, true));
        c.addView(text("优先尝试公开证券搜索；失败时仍可用本地索引或手输。只发证券代码/搜索词，不发持仓数量、成本、交易流水或账户资料。公开接口无稳定性/展示授权保证，报价与公告需核对券商/交易所资料。", 11, MUTED, false), margin(0, 5, 0, 8));
        c.addView(actionButton("在线搜索证券", false, () -> beginSecuritySearch(null)));
        c.addView(actionButton("搜索本地索引", false, this::beginCatalogSearch), margin(0, 6, 0, 0));
        c.addView(text("截图/识别文字由本机 bundled ML Kit 处理；穗账不会上传图片。ML Kit 可能发送服务使用指标，详见随包 Google 条款。不会自动生成交易，识别字段须逐项确认后才进入持仓表单。", 10, MUTED, false), margin(0, 7, 0, 0));
        content.addView(c, margin(0, 0, 0, 10));
    }
    private void showBackupCard() {
        CardColumn b = card(); b.setOrientation(LinearLayout.VERTICAL); b.addView(text("Excel 备份与恢复", 16, INK, true));
        b.addView(text("导出标准 .xlsx 工作簿，按账户、持仓、分红、交易、目标、索引和本地设置分表；可用 Excel / WPS 查看、筛选和编辑。导入会完整替换本机账本。请勿改动 ID 与关联编号列；旧版 JSON 备份仍可导入。", 11, MUTED, false), margin(0, 5, 0, 9));
        LinearLayout r = row(); r.addView(actionButton("导出 Excel", true, this::exportBackup), new LinearLayout.LayoutParams(0, dp(48), 1)); r.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1)); r.addView(actionButton("导入 Excel", false, this::importBackup), new LinearLayout.LayoutParams(0, dp(48), 1)); b.addView(r); content.addView(b, margin(0, 0, 0, 10));
    }
    private void showOpenSourceCard() {
        CardColumn card = card(); card.addView(text("开源组件与许可", 16, INK, true));
        card.addView(text("本应用集成 HiMiuix（LGPL-2.1）与 Material Components Android（Apache-2.0），并包含 Material Icons 的本地信息矢量图（Apache-2.0）。许可文本和源码入口可离线查阅。", 12, MUTED, false), margin(0, 5, 0, 9));
        card.addView(actionButton("查看许可与源码", false, this::showOpenSourceNotices));
        card.addView(actionButton("查看 Material Icons 许可", false, () -> showLicenseAsset("licenses/MaterialIcons-LICENSE.txt", "Material Icons Apache-2.0")), margin(0, 6, 0, 0));
        content.addView(card, margin(0, 0, 0, 10));
    }
    private void showOpenSourceNotices() {
        String notices = readAssetText("licenses/THIRD_PARTY_NOTICES.txt");
        TextView message = text(notices, 13, INK, false); message.setGravity(Gravity.TOP); message.setAutoLinkMask(Linkify.WEB_URLS); message.setMovementMethod(LinkMovementMethod.getInstance()); message.setLinkTextColor(ACCENT);
        ScrollView scroll = new ScrollView(this); scroll.setPadding(dp(20), dp(8), dp(20), dp(8)); scroll.addView(message);
        new MaterialAlertDialogBuilder(this).setTitle("开源组件与许可").setView(scroll)
                .setNegativeButton("Material Apache-2.0", (dialog, which) -> showLicenseAsset("licenses/MaterialComponentsAndroid-LICENSE.txt", "Material Components Apache-2.0"))
                .setNeutralButton("HiMiuix LGPL-2.1", (dialog, which) -> showLicenseAsset("licenses/HiMiuix-LICENSE.txt", "HiMiuix LGPL-2.1"))
                .setPositiveButton("关闭", null).show();
    }
    private void showLicenseAsset(String path, String title) {
        TextView license = text(readAssetText(path), 12, INK, false); license.setGravity(Gravity.TOP); license.setTextIsSelectable(true); license.setPadding(dp(18), dp(8), dp(18), dp(8)); license.setLineSpacing(dp(2), 1f);
        ScrollView scroll = new ScrollView(this); scroll.addView(license, new ScrollView.LayoutParams(-1, -2));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(-1, Math.max(dp(180), getResources().getDisplayMetrics().heightPixels * 2 / 3)));
        new MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll).setPositiveButton("关闭", null).show();
    }
    private String readAssetText(String path) {
        try (InputStream in = getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { return "无法读取许可文件：" + path; }
    }
    private void showTransactions() {
        sectionHeader("最近交易流水", "新增交易", () -> showTransactionDialog(0)); JSONArray transactions = db.transactions(selectedAccount, 25);
        if (transactions.length() == 0) content.addView(text("还没有交易记录。", 11, MUTED, false), margin(1, 0, 0, 6));
        for (int i = 0; i < transactions.length(); i++) { JSONObject t = transactions.optJSONObject(i); CardColumn item = card(); item.setOrientation(LinearLayout.HORIZONTAL); item.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(text(t.optString("side"), 12, INK, true), new LinearLayout.LayoutParams(dp(42), -2));
            item.addView(text(t.optString("holding_name") + " · " + shortDate(t.optString("trade_date")) + " · " + compact(t.optDouble("quantity")) + " × " + amount("", t.optDouble("price")), 11, INK, false), new LinearLayout.LayoutParams(0, -2, 1)); content.addView(item, margin(0, 3, 0, 4));
        }
    }
    private void showBoundaries() {
        CardColumn c = card(); c.setOrientation(LinearLayout.VERTICAL); c.addView(text("重要口径与边界", 15, INK, true));
        c.addView(text("• 自动税务模式在用户明示税务身份/渠道后估算；A股按本机初始持仓日期和交易流水 FIFO 估持有期。记录不完整或旧买入日期不准时结果不准，实际差别税扣缴可能在卖出时发生。\n" +
                "• 港股只在明确选择港股通时使用 20% 情景；普通港股账户不猜税率。美股只有明确选择协定适用且 W-8BEN 已被券商接受时才显示 10% 情景；未知不等于免税。基金/ETF 分配按具体产品和券商结单核对。\n" +
                "• 报价源：腾讯行情（GBK）；A股/港股分红与证券搜索：东方财富公开接口。当前验证仅代表抽样响应成功，不代表稳定 SLA、官方授权或数据准确保证。美股分红端点本轮超时，故不接入；汇率换算暂未实现。\n" +
                "• 公开请求仅包含证券代码或搜索词；用户账本数据保留本机。行情失效时显示缓存/未同步，手工录入、计算和完整 JSON 备份仍可用。数据仅供参考，不构成投资或税务建议。", 11, MUTED, false), margin(0, 7, 0, 0)); content.addView(c, margin(0, 10, 0, 4));
    }
    private void showAddAccount() {
        EditText name = edit("", "例如：长期账户"); LinearLayout f = form(); f.addView(field("账户名称", name));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("新建账户").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("创建", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> { if (name.getText().toString().trim().isEmpty()) { name.setError("请输入名称"); return; } db.addAccount(name.getText().toString()); d.dismiss(); render(); })); showFormDialog(d);
    }

    private void showStatsShortcut() { showStatsPage(); }
    private void showStatsButton() { }
    private void showStatistics() { showStatsPage(); }
    private void showStatisticsAction() { showStatsPage(); }
    private void showStats() { showStatsPage(); }
    private void showStatsPageAndRender() { showStatsPage(); }

    private void showHoldingEditor(JSONObject existing, JSONObject prefill) {
        JSONArray accounts = db.accounts(); if (accounts.length() == 0) { toast("请先创建账户"); return; }
        boolean editing = existing != null; JSONObject initial = editing ? existing : prefill;
        EditText name = edit(initial == null ? "" : initial.optString("name"), "名称");
        EditText code = edit(initial == null ? "" : initial.optString("code"), "代码，可留空后手工录入");
        MiuixListView market = spinner(MARKETS), currency = spinner(CURRENCIES);
        String initialMarket = initial == null ? "A股" : initial.optString("market", "A股");
        selectSpinner(market, initialMarket);
        selectSpinner(currency, initial == null ? defaultCurrency(initialMarket) : initial.optString("currency", defaultCurrency(initialMarket)));
        EditText quantity = numberEdit(editing ? compact(existing.optDouble("quantity")) : initial == null ? "" : initial.optString("quantity", ""), "持有数量");
        EditText cost = numberEdit(editing ? String.valueOf(existing.optDouble("cost")) : initial == null ? "" : initial.optString("cost", ""), "当前每份成本");
        MiuixListView costMethod = spinner(new String[]{"分红摊薄", "摊薄成本", "加权平均"});
        String initialCostMethod = initial == null ? FinanceMath.COST_WEIGHTED_AVERAGE : initial.optString("cost_method", FinanceMath.COST_WEIGHTED_AVERAGE);
        selectSpinner(costMethod, costMethodLabel(initialCostMethod));
        double initialPerUnit = editing ? existing.optDouble("annual_dividend_per_unit") : initial == null ? 0d : initial.optDouble("annual_dividend_per_unit", 0d);
        EditText annualDiv = numberEdit(initialPerUnit > 0d ? String.format(Locale.US, "%.6f", initialPerUnit) : "", "每份全年预计分红");
        boolean ocrDateUnparsed = !editing && initial != null && initial.optBoolean("ocr_date_unparsed", false);
        EditText openedOn = dateEdit(editing ? existing.optString("opened_on", LocalDate.now().toString()) : initial == null ? LocalDate.now().toString() : initial.optString("opened_on", LocalDate.now().toString()), false);
        MiuixListView taxMode = spinner(new String[]{"自动估算", "自定义税率"});
        String mode = initial == null ? "auto" : initial.optString("tax_mode", editing ? "manual" : "auto");
        selectSpinner(taxMode, "manual".equals(mode) ? "自定义税率" : "自动估算");
        double defaultTax = initial == null ? defaultTaxForMarket(initialMarket) : initial.optDouble("tax_rate", defaultTaxForMarket(initialMarket));
        EditText taxRate = numberEdit(String.valueOf(defaultTax * 100d), "税率百分比 0–100");
        taxRate.setEnabled("自定义税率".equals(selectedItem(taxMode)));
        MiuixListView account = accountSpinner(accounts, editing ? existing.optLong("account_id") : selectedAccount);
        ForecastUiState state = new ForecastUiState();
        JSONObject oldMeta = editing ? forecastMeta(existing.optLong("_id")) : new JSONObject();
        state.years = normalizedForecastYears(oldMeta.optInt("years", 3));
        state.sourceBefore = oldMeta.optString("source", ""); state.updatedAt = oldMeta.optLong("updated_at", 0L);
        state.stale = oldMeta.optBoolean("stale", false);
        state.automaticApplied = "historical_estimate".equals(state.sourceBefore) && initialPerUnit > 0d;
        state.manualEdited = initialPerUnit > 0d && !state.automaticApplied;
        state.assetKey = forecastAssetKey(initial == null ? "" : initial.optString("code"), initialMarket);
        Runnable[] requestForecast = {null};

        setSelectorListener(market, new OnChooseItemListener() {
            @Override public boolean onChooseBefore(CharSequence item, int which) { return true; }
            @Override public void onChooseAfter(CharSequence[] items, CharSequence[] selectedItems, Integer[] selectedValues) {
                String selectedMarket = selectedItem(market);
                selectSpinner(currency, defaultCurrency(selectedMarket, code.getText().toString()));
                if (!taxRate.isEnabled()) taxRate.setText(String.valueOf(defaultTaxForMarket(selectedMarket) * 100d));
                if (requestForecast[0] != null) requestForecast[0].run();
            }
        });
        setSelectorListener(taxMode, new OnChooseItemListener() {
            @Override public boolean onChooseBefore(CharSequence item, int which) { return true; }
            @Override public void onChooseAfter(CharSequence[] items, CharSequence[] selectedItems, Integer[] selectedValues) {
                taxRate.setEnabled("自定义税率".equals(selectedItem(taxMode)));
                if (!taxRate.isEnabled()) taxRate.setText(String.valueOf(defaultTaxForMarket(selectedItem(market)) * 100d));
            }
        });

        TextView securityLookupStatus = text("输入完整证券代码后自动查询公开名称和类别；数量、成本必须由你填写。", 11, MUTED, false);
        TextView quoteStatus = text("行情仅作参考，不会自动填写持仓数量或成本。", 11, MUTED, false);
        TextView forecastStatus = text("选中证券后自动读取 A 股/港股历史；美股分红可手工填写。", 11, MUTED, false);
        TextView forecastPreview = text("填写持有数量后显示预计年总额。历史推算不代表已公告或到账。", 12, ACCENT, true);
        Runnable updatePreview = () -> {
            double perUnit = parseNumber(annualDiv), shares = parseNumber(quantity);
            if (shares <= 0d) forecastPreview.setText("填写持有数量后显示预计年总额。");
            else if (perUnit <= 0d) forecastPreview.setText("暂无历史预测；可手工填写每份年分红。");
            else {
                double total = state.manualEdited ? FinanceMath.annualDividend(shares, perUnit) : DividendForecast.annualTotal(shares, state.estimate);
                if (Double.isNaN(total)) total = FinanceMath.annualDividend(shares, perUnit);
                forecastPreview.setText("预计年总额 " + amount(selectedItem(currency), total) + " = " + amount(selectedItem(currency), perUnit) + "/份 × " + compact(shares) + " 份");
            }
        };

        AlertDialog[] editorDialog = new AlertDialog[1];
        LinearLayout f = form();
        f.addView(actionButton("搜索证券并自动回填", true, () -> beginSecuritySearch(pre -> {
            name.setText(pre.optString("name")); code.setText(pre.optString("code"));
            selectSpinner(market, pre.optString("market", "A股")); selectSpinner(currency, pre.optString("currency", "CNY"));
            if ("自定义税率".equals(selectedItem(taxMode))) taxRate.setText(String.valueOf(defaultTaxForMarket(pre.optString("market")) * 100d));
            if (requestForecast[0] != null) requestForecast[0].run();
        }, editorDialog[0])), margin(0, 0, 0, 8));
        f.addView(field("名称", name)); f.addView(field("证券代码", code));
        f.addView(securityLookupStatus, margin(1, 0, 0, 3)); f.addView(quoteStatus, margin(1, 0, 0, 7));
        f.addView(field("持有数量", quantity)); f.addView(field("当前每份成本", cost));
        f.addView(field("成本计算方式", costMethod));
        f.addView(text("买入手续费计入成本。摊薄成本按成交净回款扣减；加权平均按当前均价出库；分红摊薄还会扣减你填写的实际净到账分红。切换方式时以当前成本为新起点，不回算切换前流水或分红。", 10, MUTED, false), margin(1, 0, 0, 6));
        f.addView(field(ocrDateUnparsed ? "首次买入日期（截图未识别；请选择）" : "首次买入日期（默认今天）", openedOn));
        f.addView(text("分红预测口径 · 历史平均", 13, INK, true), margin(1, 4, 0, 5));
        LinearLayout periods = row(); periods.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton[] periodButtons = new MaterialButton[3]; int[] spans = {1, 3, 5};
        for (int i = 0; i < spans.length; i++) {
            final int span = spans[i];
            MaterialButton button = actionButton(span + "年", span == state.years, () -> {
                state.years = span; updateForecastYearButtons(periodButtons, span);
                loadDividendForecast(code.getText().toString(), selectedItem(market), annualDiv, forecastStatus, forecastPreview, state, true);
            });
            button.setTextSize(13); button.setContentDescription("按近 " + span + " 年历史平均重新估算");
            periodButtons[i] = button;
            if (i > 0) periods.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
            periods.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        f.addView(periods, margin(0, 0, 0, 4));
        f.addView(field("每份年分红（可手动修改）", annualDiv));
        f.addView(forecastStatus, margin(1, 0, 0, 4)); f.addView(forecastPreview, margin(1, 0, 0, 9));

        LinearLayout advanced = form(); advanced.setVisibility(View.GONE);
        advanced.addView(actionButton("从离线标的索引回填", false, () -> beginCatalogSearch(pre -> {
            name.setText(pre.optString("name")); code.setText(pre.optString("code"));
            selectSpinner(market, pre.optString("market")); selectSpinner(currency, pre.optString("currency"));
            if (requestForecast[0] != null) requestForecast[0].run();
        }, editorDialog[0])), margin(0, 0, 0, 8));
        advanced.addView(field("市场类别", market)); advanced.addView(field("币种", currency)); advanced.addView(field("所属账户", account));
        advanced.addView(field("税务估算模式", taxMode)); advanced.addView(field("自定义税率（%）", taxRate));
        advanced.addView(text("税务批次估算依赖本机交易流水；港股通/美国协定状态需在全局税务设置明确选择。截图识别不会自动记交易。", 10, MUTED, false));
        MaterialButton advancedToggle = actionButton("更多设置", false, () -> { });
        advancedToggle.setContentDescription("展开市场、币种、账户和税务设置");
        advancedToggle.setOnClickListener(v -> {
            boolean show = advanced.getVisibility() != View.VISIBLE;
            advanced.setVisibility(show ? View.VISIBLE : View.GONE);
            advancedToggle.setText(show ? "收起更多设置" : "更多设置");
            advancedToggle.setContentDescription(show ? "收起市场、币种、账户和税务设置" : "展开市场、币种、账户和税务设置");
        });
        f.addView(advancedToggle, margin(0, 0, 0, 6)); f.addView(advanced);

        requestForecast[0] = () -> loadDividendForecast(code.getText().toString(), selectedItem(market), annualDiv, forecastStatus, forecastPreview, state, false);
        Handler forecastHandler = new Handler(Looper.getMainLooper());
        Runnable debouncedForecast = () -> { if (!code.hasFocus() && requestForecast[0] != null) requestForecast[0].run(); };
        code.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                forecastHandler.removeCallbacks(debouncedForecast); forecastHandler.postDelayed(debouncedForecast, 650L);
            }
        });
        code.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus && requestForecast[0] != null) requestForecast[0].run(); });
        Handler codeLookupHandler = new Handler(Looper.getMainLooper());
        int[] codeLookupSequence = {0};
        Runnable[] pendingCodeLookup = {null};
        String[] lastAutoFilledName = {""};
        boolean[] applyingAutoName = {false};
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { if (!applyingAutoName[0]) lastAutoFilledName[0] = ""; }
        });
        code.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (pendingCodeLookup[0] != null) codeLookupHandler.removeCallbacks(pendingCodeLookup[0]);
                final int requestId = ++codeLookupSequence[0];
                String entered = s == null ? "" : s.toString().trim();
                if (lastAutoFilledName[0].equals(name.getText().toString().trim()) && !lastAutoFilledName[0].isEmpty()) {
                    applyingAutoName[0] = true; name.setText(""); applyingAutoName[0] = false; lastAutoFilledName[0] = "";
                }
                quoteStatus.setText("行情仅作参考，不会自动填写持仓数量或成本。");
                quoteStatus.setTextColor(MUTED);
                if (entered.isEmpty()) {
                    securityLookupStatus.setText("输入完整证券代码后自动查询公开名称和类别；数量、成本必须由你填写。");
                    return;
                }
                if (!isSecurityCodeLookupCandidate(entered, selectedItem(market))) {
                    securityLookupStatus.setText("代码格式尚不完整；填入完整 5/6 位代码、港股代码或美股代码后查询。可继续手工录入。");
                    return;
                }
                securityLookupStatus.setText("正在查询公开证券名称与类别……");
                pendingCodeLookup[0] = () -> requestSecurityCodeLookup(entered, requestId, codeLookupSequence, code, name, market, currency,
                        securityLookupStatus, quoteStatus, requestForecast[0], editorDialog, name.getText().toString(),
                        selectedItem(market), selectedItem(currency), lastAutoFilledName, applyingAutoName);
                codeLookupHandler.postDelayed(pendingCodeLookup[0], 650L);
            }
        });
        annualDiv.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!state.applying) {
                    state.manualEdited = s != null && s.length() > 0 && parseNumber(annualDiv) > 0d;
                    if (state.manualEdited) state.automaticApplied = false;
                    forecastStatus.setText(state.manualEdited ? "手工修改会随持仓保存；正式公告与到账金额在分红日历单独显示。" : "每份金额为空时可点年限按钮重试历史读取。");
                }
                updatePreview.run();
            }
        });
        quantity.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) { updatePreview.run(); }
        });

        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle(editing ? "编辑持仓" : "新增持仓").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        editorDialog[0] = d;
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String n = name.getText().toString().trim(); double q = parseNumber(quantity), costValue = parseNumber(cost), div = parseNumber(annualDiv), rate = parseNumber(taxRate) / 100d;
            if (n.isEmpty()) { name.setError("请填写名称"); return; }
            String selectedCostMethod = costMethodValue(selectedItem(costMethod));
            if (!Double.isFinite(q) || !Double.isFinite(costValue) || !Double.isFinite(div) || q <= 0 ||
                    (FinanceMath.COST_WEIGHTED_AVERAGE.equals(selectedCostMethod) && costValue < 0) || div < 0 || rate < 0 || rate > 1) {
                quantity.setError("数量需大于 0；加权平均成本不能为负，其他成本可为负；派息不能为负；税率需在 0–100% 之间"); return;
            }
            LocalDate opened = parseDate(openedOn.getText().toString()); if (opened == null) { openedOn.setError("请选择有效日期"); return; }
            JSONObject a = selectedJson(account); String savedCode = code.getText().toString().trim(), savedMarket = selectedItem(market);
            String selectedTaxMode = "自定义税率".equals(selectedItem(taxMode)) ? "manual" : "auto";
            long holdingId = db.saveHolding(editing ? existing.optLong("_id") : 0, n, savedCode, savedMarket,
                    selectedItem(currency), q, costValue, div, rate, a.optLong("_id"), opened.toString(), selectedTaxMode, selectedCostMethod);
            state.savedHoldingId = holdingId; state.savedCode = savedCode; state.savedMarket = savedMarket;
            state.savedManualAtSave = state.manualEdited;
            if (div > 0d && state.manualEdited) saveForecastMeta(holdingId, "manual", savedCode, savedMarket, state.years, 0L, false);
            else if (div > 0d && state.automaticApplied) saveForecastMeta(holdingId, "historical_estimate", savedCode, savedMarket, state.years, state.updatedAt, state.stale);
            else if (div <= 0d) saveForecastMeta(holdingId, state.unavailableSource, savedCode, savedMarket, state.years, state.updatedAt, state.stale);
            hideKeyboard(d); d.dismiss(); render(); toast(editing ? "持仓已更新" : "持仓已添加");
        }));
        showFormDialog(d); updatePreview.run();
        if (!code.getText().toString().trim().isEmpty()) {
            requestForecast[0].run();
            if (!editing && isSecurityCodeLookupCandidate(code.getText().toString(), selectedItem(market))) {
                String initialCode = code.getText().toString().trim();
                int requestId = ++codeLookupSequence[0];
                pendingCodeLookup[0] = () -> requestSecurityCodeLookup(initialCode, requestId, codeLookupSequence, code, name, market, currency,
                        securityLookupStatus, quoteStatus, requestForecast[0], editorDialog, name.getText().toString(),
                        selectedItem(market), selectedItem(currency), lastAutoFilledName, applyingAutoName);
                codeLookupHandler.postDelayed(pendingCodeLookup[0], 250L);
            }
        }
    }

    private boolean isSecurityCodeLookupCandidate(String code, String market) {
        String value = code == null ? "" : code.trim();
        if (value.matches("[0-9]{5,6}") || value.matches("(?i)[0-9]{1,5}\\.HK")) return true;
        if ("港股".equals(market) && value.matches("[0-9]{1,5}")) return true;
        return value.matches("(?i)[A-Z][A-Z0-9.-]{1,7}");
    }

    private void requestSecurityCodeLookup(String requestedCode, int requestId, int[] sequence, EditText code,
                                          EditText name, MiuixListView market, MiuixListView currency,
                                          TextView identityStatus, TextView quoteStatus, Runnable requestForecast,
                                          AlertDialog[] editorDialog, String nameAtDispatch, String marketAtDispatch,
                                          String currencyAtDispatch, String[] lastAutoFilledName, boolean[] applyingAutoName) {
        if (requestId != sequence[0] || !MarketDataClient.normalizeSearchCode(code.getText().toString()).equals(MarketDataClient.normalizeSearchCode(requestedCode))) return;
        identityStatus.setText("正在查询东方财富公开证券搜索……");
        String fallbackMarket = marketAtDispatch;
        dataExecutor.execute(() -> {
            MarketDataClient.Snapshot<MarketDataClient.Security> result;
            try { result = marketData.search(requestedCode, fallbackMarket); }
            catch (Exception ignored) { result = new MarketDataClient.Snapshot<>(Collections.emptyList(), 0L, false, "公开证券查询暂不可用"); }
            final MarketDataClient.Snapshot<MarketDataClient.Security> snapshot = result;
            final MarketDataClient.Security found = MarketDataClient.findExactSecurity(snapshot.rows, requestedCode);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || requestId != sequence[0] || editorDialog[0] == null || !editorDialog[0].isShowing()
                        || !MarketDataClient.normalizeSearchCode(code.getText().toString()).equals(MarketDataClient.normalizeSearchCode(requestedCode))) return;
                if (found == null) {
                    String warning = snapshot.warning == null ? "公开搜索没有返回该代码" : snapshot.warning;
                    identityStatus.setText(warning + (snapshot.rows.isEmpty() ? "；" : "；返回了候选但没有精确代码匹配；") + "不自动填数量/成本，可继续手工填写或稍后重试。");
                    quoteStatus.setText("没有精确证券匹配，未查询行情；持仓数量和成本保持手填。");
                    return;
                }
                String discoveredMarket = MarketDataClient.marketFor(found, fallbackMarket);
                boolean canSetName = name.getText().toString().equals(nameAtDispatch);
                boolean canSetMarket = selectedItem(market).equals(marketAtDispatch);
                boolean canSetCurrency = selectedItem(currency).equals(currencyAtDispatch);
                if (canSetName) {
                    applyingAutoName[0] = true; name.setText(found.name); applyingAutoName[0] = false;
                    lastAutoFilledName[0] = found.name;
                }
                if (canSetMarket) selectSpinner(market, discoveredMarket);
                String effectiveMarket = canSetMarket ? discoveredMarket : selectedItem(market);
                if (canSetCurrency) selectSpinner(currency, defaultCurrency(effectiveMarket, found.code));
                String cacheLabel = snapshot.stale ? "本机旧缓存" : snapshot.cached ? "本机缓存" : "在线查询";
                identityStatus.setText("已匹配 " + found.name + " · " + discoveredMarket + " · " + defaultCurrency(discoveredMarket, found.code)
                        + " · 东方财富公开搜索 · " + cacheLabel + " · 更新于 " + timeLabel(snapshot.updatedAt)
                        + (snapshot.stale ? "；请核对缓存内容" : "") + "。数量与成本仍由你手填。");
                quoteStatus.setText("正在读取公开行情；行情不会回填数量或成本。");
                quoteStatus.setTextColor(MUTED);
                if (requestForecast != null) requestForecast.run();
                MarketDataClient.Security quoteSecurity = new MarketDataClient.Security(found.code, found.name, discoveredMarket, defaultCurrency(discoveredMarket, found.code));
                dataExecutor.execute(() -> {
                    MarketDataClient.Snapshot<MarketDataClient.Quote> quoteSnapshot;
                    try { quoteSnapshot = marketData.quotes(Collections.singletonList(quoteSecurity)); }
                    catch (Exception ignored) { quoteSnapshot = new MarketDataClient.Snapshot<>(Collections.emptyList(), 0L, false, "公开行情暂不可用"); }
                    final MarketDataClient.Snapshot<MarketDataClient.Quote> quoteResult = quoteSnapshot;
                    MarketDataClient.Quote foundQuote = null;
                    for (MarketDataClient.Quote q : quoteResult.rows) if (q.code.equalsIgnoreCase(found.code)) { foundQuote = q; break; }
                    final MarketDataClient.Quote quote = foundQuote;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || requestId != sequence[0] || editorDialog[0] == null || !editorDialog[0].isShowing()
                                || !MarketDataClient.normalizeSearchCode(code.getText().toString()).equals(MarketDataClient.normalizeSearchCode(requestedCode))) return;
                        if (quote == null) {
                            quoteStatus.setText((quoteResult.warning == null ? "公开行情暂不可用" : quoteResult.warning) + "；持仓数量和成本保持手填。");
                            quoteStatus.setTextColor(MUTED);
                            return;
                        }
                        String quoteCache = quote.stale ? "本机旧缓存" : quoteResult.cached ? "本机缓存" : "在线行情";
                        quoteStatus.setText("腾讯公开行情 " + amount(quote.currency, quote.price) + " · " + marketMoveLabel(quote.changePercent) + " · " + quoteCache + " · 更新于 "
                                + timeLabel(quote.updatedAt) + "；仅供参考，数量和成本未修改。");
                        quoteStatus.setTextColor(marketMoveColor(quote.changePercent));
                    });
                });
            });
        });
    }

    private static final class ForecastUiState {
        int years = 3, sequence;
        boolean loading, loaded, manualEdited, applying, automaticApplied, stale, savedManualAtSave;
        long updatedAt, savedHoldingId;
        String assetKey = "", requestKey = "", savedCode = "", savedMarket = "", sourceBefore = "", unavailableSource = "unavailable";
        DividendForecast.Estimate estimate;
    }

    private void updateForecastYearButtons(MaterialButton[] buttons, int selectedYears) {
        for (int i = 0; i < buttons.length; i++) {
            boolean selected = (i == 0 && selectedYears == 1) || (i == 1 && selectedYears == 3) || (i == 2 && selectedYears == 5);
            buttons[i].setBackgroundTintList(ColorStateList.valueOf(getColor(selected ? R.color.app_primary : R.color.app_primary_container)));
            buttons[i].setTextColor(getColor(selected ? R.color.app_on_primary : R.color.app_on_primary_container));
            buttons[i].setSelected(selected);
        }
    }

    private void loadDividendForecast(String codeValue, String marketValue, EditText annualDiv,
                                      TextView status, TextView preview, ForecastUiState state, boolean force) {
        String code = codeValue == null ? "" : codeValue.trim();
        String market = marketValue == null ? "" : marketValue.trim();
        String assetKey = forecastAssetKey(code, market);
        if (!assetKey.equals(state.assetKey)) {
            state.assetKey = assetKey; state.requestKey = ""; state.loaded = false; state.estimate = null; state.unavailableSource = "unavailable";
            if (!state.manualEdited && parseNumber(annualDiv) > 0d) {
                state.applying = true; annualDiv.setText(""); state.applying = false;
                state.automaticApplied = false; state.updatedAt = 0L; state.stale = false;
            }
        }
        if (code.isEmpty()) { state.loading = false; status.setText("选中证券后自动读取历史分红；也可手工填写。"); return; }
        if (!("A股".equals(market) || "港股".equals(market))) {
            state.loading = false; state.loaded = true;
            status.setText(("ETF".equals(market) || "基金".equals(market))
                    ? "当前已验证的公开分红接口不支持基金/ETF；不把缺少数据当作零分红，可手工填写。"
                    : "美股自动分红暂不支持；可手工填写。正式方案请在分红日历核对。"); return;
        }
        if (state.manualEdited) { state.loading = false; status.setText("保留手工预测值；正式公告/到账金额在分红日历单独显示。"); return; }
        if (!validForecastCode(code, market)) { state.loading = false; status.setText("代码格式需核对；确认市场和代码后自动读取，当前仍可手工填写。"); return; }
        String requestKey = assetKey + "|" + state.years;
        if (!force && requestKey.equals(state.requestKey) && (state.loading || state.loaded)) return;
        int requestId = ++state.sequence; state.requestKey = requestKey; state.loading = true; state.loaded = false;
        status.setText("正在读取近 " + state.years + " 年公开历史分红……");
        dataExecutor.execute(() -> {
            MarketDataClient.Snapshot<MarketDataClient.Dividend> snap;
            try { snap = marketData.dividends(market, code); }
            catch (Exception e) { snap = new MarketDataClient.Snapshot<>(Collections.emptyList(), 0L, false, "分红数据暂不可用；可核对代码后重试或手工填写"); }
            final MarketDataClient.Snapshot<MarketDataClient.Dividend> result = snap;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || requestId != state.sequence || !requestKey.equals(state.requestKey)) return;
                state.loading = false; state.loaded = true;
                if (result.rows.isEmpty()) {
                    state.estimate = null; state.unavailableSource = "fetch_unavailable";
                    String retained = state.automaticApplied && parseNumber(annualDiv) > 0d ? "；保留上次已保存的预测值" : "";
                    status.setText((result.warning == null ? "暂无可用历史分红" : result.warning) + retained + "；可手工填写或稍后重试。");
                    if (state.savedHoldingId > 0 && !state.savedManualAtSave && !state.automaticApplied)
                        saveForecastMeta(state.savedHoldingId, state.unavailableSource, state.savedCode, state.savedMarket, state.years, result.updatedAt, result.stale);
                    return;
                }
                ArrayList<DividendForecast.Report> reports = new ArrayList<>();
                for (MarketDataClient.Dividend event : result.rows) reports.add(new DividendForecast.Report(event.period, event.perShare));
                DividendForecast.Estimate estimate = DividendForecast.calculate(reports, state.years, LocalDate.now().getYear());
                state.estimate = estimate;
                if (!estimate.hasData) {
                    state.unavailableSource = "no_history";
                    String retained = state.automaticApplied && parseNumber(annualDiv) > 0d ? "；保留上次已保存的预测值" : "";
                    status.setText("公开数据已读取，但近 " + state.years + " 个完整年度没有可用分红记录" + retained + "；不是零分红结论，可手工填写。");
                    if (state.savedHoldingId > 0 && !state.savedManualAtSave && !state.automaticApplied)
                        saveForecastMeta(state.savedHoldingId, state.unavailableSource, state.savedCode, state.savedMarket, state.years, result.updatedAt, result.stale);
                    return;
                }
                if (state.manualEdited) {
                    state.updatedAt = result.updatedAt; state.stale = result.stale;
                    status.setText("公开历史已读取，但保留你刚输入的手工值；要改用历史均值，请先清空每份金额后再点年限。");
                    return;
                }
                state.updatedAt = result.updatedAt; state.stale = result.stale; state.unavailableSource = "historical_estimate";
                state.applying = true; annualDiv.setText(String.format(Locale.US, "%.6f", estimate.annualPerUnit)); state.applying = false;
                state.automaticApplied = true;
                status.setText("近 " + state.years + " 年历史推算 · 东方财富公开报表" + (result.stale ? " · 本机旧缓存" : "") +
                        " · 更新于 " + timeLabel(result.updatedAt) + "；不是已公告或已到账金额。");
                if (state.savedHoldingId > 0 && !state.savedManualAtSave && !state.savedCode.isEmpty()) {
                    if (db.updateEstimatedAnnualDividend(state.savedHoldingId, state.savedCode, state.savedMarket, estimate.annualPerUnit)) {
                        saveForecastMeta(state.savedHoldingId, "historical_estimate", state.savedCode, state.savedMarket, state.years, result.updatedAt, result.stale);
                        if ("home".equals(activeTab) || "holdings".equals(activeTab)) render();
                    }
                }
            });
        });
    }

    private boolean validForecastCode(String code, String market) {
        if ("A股".equals(market)) return code.replaceAll("[^0-9]", "").length() == 6;
        if ("港股".equals(market)) { String digits = code.toUpperCase(Locale.ROOT).replace(".HK", "").replaceAll("[^0-9]", ""); return digits.length() >= 1 && digits.length() <= 5; }
        return false;
    }

    private String forecastAssetKey(String code, String market) {
        return (market == null ? "" : market.trim()) + ":" + (code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
    }

    private int normalizedForecastYears(int years) { return years == 1 || years == 5 ? years : 3; }

    private JSONObject forecastMeta(long holdingId) {
        if (holdingId <= 0) return new JSONObject();
        try { return new JSONObject(db.setting("dividend_forecast_" + holdingId, "")); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private void saveForecastMeta(long holdingId, String source, String code, String market, int years, long updatedAt, boolean stale) {
        if (holdingId <= 0) return;
        JSONObject meta = new JSONObject();
        try {
            meta.put("source", source); meta.put("code", code == null ? "" : code); meta.put("market", market == null ? "" : market);
            meta.put("years", normalizedForecastYears(years)); meta.put("updated_at", updatedAt); meta.put("stale", stale);
            db.saveSetting("dividend_forecast_" + holdingId, meta.toString());
        } catch (JSONException ignored) { }
    }

    private String forecastSourceLabel(JSONObject holding) {
        double perUnit = holding.optDouble("annual_dividend_per_unit"); String market = holding.optString("market");
        JSONObject meta = forecastMeta(holding.optLong("_id")); String source = meta.optString("source", "");
        if ("fetch_unavailable".equals(source)) return "历史分红数据获取失败 · 可手工填写";
        if ("no_history".equals(source)) return "近 " + normalizedForecastYears(meta.optInt("years", 3)) + " 年无可用分红记录 · 不代表零分红";
        if (perUnit <= 0d) {
            if ("美股".equals(market)) return "美股自动分红暂不支持 · 可手工填写";
            if ("A股".equals(market) || "港股".equals(market)) return "尚无可用历史预测 · 可手工填写";
            return "暂无分红预测";
        }
        if ("historical_estimate".equals(source)) return "近 " + normalizedForecastYears(meta.optInt("years", 3)) + " 年历史推算 · " +
                (meta.optBoolean("stale", false) ? "旧缓存 " : "") + "更新于 " + timeLabel(meta.optLong("updated_at", 0L));
        if ("manual".equals(source)) return "手工预测（非公告金额）";
        return "已有预测 · 来源未记录";
    }

    private boolean hasAnyDividendEstimate() {
        JSONArray holdings = db.holdings(selectedAccount);
        for (int i = 0; i < holdings.length(); i++) if (annualOf(holdings.optJSONObject(i), false) > 0d) return true;
        return false;
    }

    private interface CatalogPickCallback { void onPicked(JSONObject item); }
    private void beginSecuritySearch(CatalogPickCallback callback) { beginSecuritySearch(callback, null); }
    private void beginSecuritySearch(CatalogPickCallback callback, AlertDialog returnTo) {
        if (returnTo != null && returnTo.isShowing()) returnTo.hide();
        EditText query = edit("", "证券名称或代码，如 600519 / 腾讯 / AAPL");
        MiuixListView market = spinner(MARKETS); selectSpinner(market, "A股");
        LinearLayout f = form(); f.addView(field("搜索词", query)); f.addView(field("预期证券类别（用于兜底）", market));
        f.addView(text("只查询公开代码/搜索词，不上传持仓数量、成本或交易数据。搜索服务不稳定，名称/代码/市场/币种需在保存前核对。", 10, MUTED, false));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("搜索证券").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("搜索", null).create();
        boolean[] handingOff = {false};
        d.setOnDismissListener(ignored -> { if (!handingOff[0]) restoreParentDialog(returnTo); });
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String q = query.getText().toString().trim(), fallbackMarket = selectedItem(market);
            if (q.isEmpty()) { query.setError("请输入名称或代码"); return; }
            handingOff[0] = true; d.dismiss(); toast("正在搜索公开证券信息…");
            dataExecutor.execute(() -> {
                MarketDataClient.Snapshot<MarketDataClient.Security> result;
                try { result = marketData.search(q, fallbackMarket); }
                catch (Exception ignored) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showSecuritySearchError(returnTo); });
                    return;
                }
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showSecurityResults(q, fallbackMarket, result, callback, returnTo); });
            });
        })); showFormDialog(d);
    }
    private void showSecuritySearchError(AlertDialog returnTo) {
        AlertDialog error = new MaterialAlertDialogBuilder(this).setTitle("搜索暂不可用").setMessage("公开证券搜索未能完成。可关闭后返回原表单，再手动录入或稍后重试。")
                .setPositiveButton("返回表单", null).create();
        restoreParentAfterDismiss(error, returnTo); error.show();
    }
    private void showSecurityResults(String query, String fallbackMarket,
                                    MarketDataClient.Snapshot<MarketDataClient.Security> result,
                                    CatalogPickCallback callback, AlertDialog returnTo) {
        if (result.rows.isEmpty()) {
            JSONArray local = db.assetIndex(query);
            if (local.length() > 0) { showCatalogResults(query, callback, returnTo); return; }
            String market = MarketDataClient.marketFromClassify("", query, fallbackMarket);
            JSONObject manual = new JSONObject(); try { manual.put("code", query); manual.put("name", query); manual.put("market", market); manual.put("currency", defaultCurrency(market, query)); } catch (JSONException ignored) { }
            AlertDialog noMatch = new MaterialAlertDialogBuilder(this).setTitle("没有可靠匹配").setMessage((result.warning == null ? "搜索无结果。" : result.warning + "\n\n") + "可以继续手动录入，保存前请核对代码、市场、名称和币种。")
                    .setNegativeButton("取消", null).setPositiveButton(callback == null ? "手动录入" : "回填当前表单", (dlg, which) -> {
                        if (callback == null) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, manual)); else callback.onPicked(manual);
                    }).create();
            restoreParentAfterDismiss(noMatch, returnTo); noMatch.show(); return;
        }
        String[] labels = new String[result.rows.size()];
        for (int i = 0; i < result.rows.size(); i++) { MarketDataClient.Security s = result.rows.get(i); String shownMarket = isFundCategory(fallbackMarket) ? fallbackMarket : s.market; labels[i] = s.name + " · " + s.code + " · " + shownMarket + " · " + defaultCurrency(shownMarket, s.code); }
        String title = (result.stale ? "证券搜索 · 旧缓存（请核对）" : "选择证券 · 在线结果需核对") + " · " + timeLabel(result.updatedAt);
        AlertDialog choices = new MaterialAlertDialogBuilder(this).setTitle(title).setItems(labels, (dlg, which) -> {
            MarketDataClient.Security s = result.rows.get(which); String selectedMarket = isFundCategory(fallbackMarket) ? fallbackMarket : s.market; JSONObject pre = new JSONObject();
            try { pre.put("name", s.name); pre.put("code", s.code); pre.put("market", selectedMarket); pre.put("currency", defaultCurrency(selectedMarket, s.code)); pre.put("tax_mode", "auto"); } catch (JSONException ignored) { }
            if (callback == null) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, pre)); else callback.onPicked(pre);
        }).setNegativeButton("取消", null).setNeutralButton("手动输入", (dlg, which) -> {
            String m = MarketDataClient.marketFromClassify("", query, fallbackMarket); JSONObject pre = new JSONObject();
            try { pre.put("name", query); pre.put("code", query); pre.put("market", m); pre.put("currency", defaultCurrency(m, query)); pre.put("tax_mode", "auto"); } catch (JSONException ignored) { }
            if (callback == null) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, pre)); else callback.onPicked(pre);
        }).create();
        restoreParentAfterDismiss(choices, returnTo); choices.show();
    }
    private boolean isFundCategory(String market) { return "基金".equals(market) || "ETF".equals(market); }
    private void chooseBrokerScreenshot() {
        Intent i = android.os.Build.VERSION.SDK_INT >= 33 ? new Intent(MediaStore.ACTION_PICK_IMAGES) : new Intent(Intent.ACTION_OPEN_DOCUMENT);
        if (android.os.Build.VERSION.SDK_INT < 33) i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try { startActivityForResult(i, 44); } catch (Exception e) { toast("当前设备无法打开系统照片选择器；仍可手工录入"); }
    }
    private void startLocalScreenshotOcr(Uri uri) {
        toast("正在本机识别；截图不会由穗账上传");
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            com.google.mlkit.vision.text.TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            int imageWidth = image.getWidth(), imageHeight = image.getHeight();
            recognizer.process(image).addOnSuccessListener(result -> showBrokerOcrReview(parseBrokerScreenshot(result, imageWidth, imageHeight)))
                    .addOnFailureListener(error -> {
                        toast("本地 OCR 失败；可在空白核对页手工修正");
                        showBrokerOcrReview(parseBrokerScreenshot(null, imageWidth, imageHeight));
                    })
                    .addOnCompleteListener(task -> recognizer.close());
        } catch (Exception e) {
            toast("无法读取图片；可在空白核对页手工录入");
            showBrokerOcrReview(parseBrokerScreenshot(null, 0, 0));
        }
    }
    private EasternFortuneHoldingParser.Result parseBrokerScreenshot(com.google.mlkit.vision.text.Text recognized, int imageWidth, int imageHeight) {
        ArrayList<EasternFortuneHoldingParser.Fragment> fragments = new ArrayList<>();
        if (recognized != null) for (com.google.mlkit.vision.text.Text.TextBlock block : recognized.getTextBlocks()) {
            for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                java.util.List<com.google.mlkit.vision.text.Text.Element> elements = line.getElements();
                if (elements == null || elements.isEmpty()) {
                    android.graphics.Rect box = line.getBoundingBox();
                    if (box != null) fragments.add(new EasternFortuneHoldingParser.Fragment(line.getText(), box.left, box.top, box.right, box.bottom));
                } else for (com.google.mlkit.vision.text.Text.Element element : elements) {
                    android.graphics.Rect box = element.getBoundingBox();
                    if (box != null) fragments.add(new EasternFortuneHoldingParser.Fragment(element.getText(), box.left, box.top, box.right, box.bottom));
                }
            }
        }
        return new EasternFortuneHoldingParser().parse(fragments, imageWidth, imageHeight);
    }
    private void showBrokerOcrReview(EasternFortuneHoldingParser.Result result) {
        if (result.layout == EasternFortuneHoldingParser.Layout.POSITION_LIST && result.holdings.size() > 1) {
            showBrokerOcrListReview(result); return;
        }
        showBrokerOcrSingleReview(result, result.holdings.isEmpty() ? new EasternFortuneHoldingParser.Holding() : result.holdings.get(0));
    }
    private void showBrokerOcrListReview(EasternFortuneHoldingParser.Result result) {
        String[] choices = new String[result.holdings.size()];
        for (int i = 0; i < result.holdings.size(); i++) {
            EasternFortuneHoldingParser.Holding h = result.holdings.get(i);
            choices[i] = h.displayName() + " · " + (h.code.value.isEmpty() ? "代码未显示" : h.code.value)
                    + " · 数量 " + (h.quantity.value.isEmpty() ? "待确认" : h.quantity.value)
                    + " · 成本 " + (h.cost.value.isEmpty() ? "待确认" : h.cost.value);
        }
        String message = result.notice + "\n\n识别到 " + result.holdings.size() + " 项股票候选。请选择一项进入可编辑核对；每次只进入一只持仓表单，需分别确认，不会批量写入。";
        new MaterialAlertDialogBuilder(this).setTitle("东方财富持仓列表 · 逐只核对").setMessage(message)
                .setItems(choices, (dialog, which) -> new Handler(Looper.getMainLooper()).post(() -> showBrokerOcrSingleReview(result, result.holdings.get(which))))
                .setNegativeButton("取消", null).setNeutralButton("手动新增", (dialog, which) -> new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, null))).show();
    }
    private void showBrokerOcrSingleReview(EasternFortuneHoldingParser.Result result, EasternFortuneHoldingParser.Holding parsed) {
        String parsedMarket = parsed.market == null || parsed.market.isEmpty() ? "A股" : parsed.market;
        EditText name = edit(parsed.name.value, parsed.name.value.isEmpty() ? "未可靠识别，请填写" : "识别值可编辑");
        EditText code = edit(parsed.code.value, parsed.code.value.isEmpty() ? "完整代码未识别，请填写" : "识别值可编辑");
        MiuixListView market = spinner(MARKETS); selectSpinner(market, parsedMarket);
        MiuixListView currency = spinner(CURRENCIES); selectSpinner(currency, defaultCurrency(parsedMarket, parsed.code.value));
        EditText quantity = numberEdit(parsed.quantity.value, parsed.quantity.value.isEmpty() ? "未可靠识别，请填写持仓数量" : "识别值可编辑");
        EditText cost = numberEdit(parsed.cost.value, parsed.cost.value.isEmpty() ? "未可靠识别，请填写成本价" : "识别值可编辑");
        EditText date = dateEdit("", false);
        LinearLayout f = form();
        f.addView(text("图片与 OCR 字符只在本机用于解析；穗账不上传图片/识别文字，也不显示股东号。ML Kit 可能发送服务使用/性能指标。历史交易/已到账记录不会从本次持仓识别导入。", 11, MUTED, false));
        f.addView(text(result.notice, 11, INK, false), margin(0, 7, 0, 7));
        String layout = result.layout == EasternFortuneHoldingParser.Layout.DETAIL ? "持仓明细" : result.layout == EasternFortuneHoldingParser.Layout.POSITION_LIST ? "持仓列表" : "未知版式";
        f.addView(text("版式：" + layout + " · 所有预填值仍需你确认；日期未解析", 12, ACCENT, true), margin(0, 0, 0, 8));
        addOcrReviewField(f, "证券名称（可编辑）", name, parsed.name);
        addOcrReviewField(f, "证券代码（可编辑）", code, parsed.code);
        f.addView(field("市场类别（可编辑）", market)); f.addView(field("币种（可编辑）", currency));
        addOcrReviewField(f, "持有数量（可编辑）", quantity, parsed.quantity);
        addOcrReviewField(f, "每份成本（可编辑）", cost, parsed.cost);
        f.addView(text("首次建仓日期：未从截图解析 · 请手动选择有效日期", 11, RED, false), margin(1, 0, 0, 4));
        f.addView(field("首次买入日期（未识别；保存前必选）", date));
        for (String note : parsed.notices) f.addView(text("• " + note, 11, MUTED, false), margin(1, 4, 0, 0));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("东方财富截图 · 逐项核对").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("继续到持仓表单", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            JSONObject pre = new JSONObject();
            try { pre.put("name", name.getText().toString().trim()); pre.put("code", code.getText().toString().trim()); pre.put("market", selectedItem(market));
                pre.put("currency", selectedItem(currency)); pre.put("quantity", quantity.getText().toString()); pre.put("cost", cost.getText().toString());
                pre.put("opened_on", date.getText().toString().trim()); pre.put("ocr_date_unparsed", date.getText().toString().trim().isEmpty());
                pre.put("annual_dividend_per_unit", "0"); pre.put("tax_mode", "auto");
            } catch (JSONException ignored) { }
            d.dismiss(); new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, pre));
        })); showFormDialog(d);
    }
    private void addOcrReviewField(LinearLayout form, String label, EditText input, EasternFortuneHoldingParser.Field source) {
        String state = source.state == EasternFortuneHoldingParser.State.AMBIGUOUS
                ? "存在冲突 · 待核对，请手动填写" : source.value.isEmpty() ? "未识别 · 请手动填写" : source.state == EasternFortuneHoldingParser.State.COLUMN_REVIEW
                ? "按列位匹配 · 仍需确认" : "标签明确 · 仍需确认";
        form.addView(text(state + "\n原标签/来源：" + source.evidence, 11, source.value.isEmpty() ? RED : MUTED, false), margin(1, 0, 0, 4));
        form.addView(field(label, input));
    }
    private void beginCatalogSearch() { beginCatalogSearch(null); }
    private void beginCatalogSearch(CatalogPickCallback callback) {
        beginCatalogSearch(callback, null);
    }
    private void beginCatalogSearch(CatalogPickCallback callback, AlertDialog returnTo) {
        if (returnTo != null && returnTo.isShowing()) returnTo.hide();
        EditText q = edit("", "输入代码或名称"); LinearLayout f = form(); f.addView(field("离线索引（无行情/分红数据）", q));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("搜索本地标的索引").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("搜索", null).create();
        boolean[] handingOff = {false};
        d.setOnDismissListener(ignored -> { if (!handingOff[0]) restoreParentDialog(returnTo); });
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty()) { q.setError("请输入名称或代码"); return; }
            handingOff[0] = true; d.dismiss(); showCatalogResults(query, callback, returnTo);
        })); showFormDialog(d);
    }
    private void showCatalogResults(String query, CatalogPickCallback callback) {
        showCatalogResults(query, callback, null);
    }
    private void showCatalogResults(String query, CatalogPickCallback callback, AlertDialog returnTo) {
        JSONArray rows = db.assetIndex(query); if (rows.length() == 0) {
            AlertDialog noMatch = new MaterialAlertDialogBuilder(this).setTitle("没有匹配项").setMessage("可手动输入标的名称和代码；新建持仓后代码会加入本地索引。")
                    .setNegativeButton("返回", null).setPositiveButton(callback == null ? "手动新增" : "返回表单", (d, w) -> { if (callback == null) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, null)); }).create();
            restoreParentAfterDismiss(noMatch, returnTo); noMatch.show(); return;
        }
        String[] labels = new String[rows.length()]; for (int i = 0; i < rows.length(); i++) { JSONObject r = rows.optJSONObject(i); labels[i] = r.optString("code") + " · " + r.optString("name") + " · " + r.optString("market"); }
        AlertDialog choices = new MaterialAlertDialogBuilder(this).setTitle("本地代码索引 · 仅供人工核对").setItems(labels, (d, which) -> {
            JSONObject r = rows.optJSONObject(which); JSONObject pre = new JSONObject(); try { pre.put("code", r.optString("code")); pre.put("name", r.optString("name")); pre.put("market", r.optString("market")); pre.put("currency", defaultCurrency(r.optString("market"), r.optString("code"))); } catch (JSONException ignored) { }
            if (callback == null) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, pre)); else callback.onPicked(pre);
        }).setNegativeButton("取消", null).setNeutralButton(callback == null ? "手动新增" : "关闭", (d, w) -> { if (callback == null) new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, null)); }).create();
        restoreParentAfterDismiss(choices, returnTo); choices.show();
    }
    private String defaultCurrency(String market) { return defaultCurrency(market, ""); }
    private String defaultCurrency(String market, String code) { if ("港股".equals(market)) return "HKD"; if ("美股".equals(market) || "VOO".equalsIgnoreCase(code) || "SCHD".equalsIgnoreCase(code)) return "USD"; return "CNY"; }
    private double defaultTaxForMarket(String market) {
        switch (market) {
            case "A股": return settingDouble("tax_a_mid", .10);
            case "港股": return "港股通".equals(setting("tax_hk_channel", "未确认")) ? .20d : 0d;
            case "美股": return setting("tax_us_status", "未确认").startsWith("符合协定") ? .10d : setting("tax_us_status", "未确认").startsWith("不适用协定") ? .30d : 0d;
            case "基金": return settingDouble("tax_default_fund", 0);
            case "ETF": return settingDouble("tax_default_etf", 0);
            default: return 0;
        }
    }
    private void beginAddDividend() {
        if (db.holdings(selectedAccount).length() == 0) {
            AlertDialog prompt = new MaterialAlertDialogBuilder(this).setTitle("先添加持仓").setMessage("分红事件需关联一项持仓记录。").setNegativeButton("取消", null).setPositiveButton("新增持仓", null).create();
            prompt.setOnShowListener(v -> prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                prompt.dismiss(); new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, null));
            })); prompt.show();
        } else showDividendDialog(0);
    }
    private void showDividendDialog(long preselectedId) {
        JSONArray holdings = db.holdings(selectedAccount); if (holdings.length() == 0) { beginAddDividend(); return; }
        MiuixListView holding = holdingSpinner(holdings, preselectedId); EditText perShare = numberEdit("", "本期每股/每份金额");
        EditText payDate = dateEdit(LocalDate.now().toString(), false), recordDate = dateEdit("", true), exDate = dateEdit("", true); MiuixListView status = spinner(new String[]{"预计", "已到账"}); EditText note = edit("", "备注（可选）");
        TextView estimate = text("金额按当前数量估算；实际到账额请以券商记录为准。", 10, ACCENT, false);
        EditText actualReceived = numberEdit("", "可选；按券商实际入账填写");
        Runnable update = () -> {
            JSONObject h = selectedJson(holding); if (h == null) return;
            double gross = parseNumber(perShare) * h.optDouble("quantity"); String taxDate = blankToNull(recordDate.getText().toString());
            if (taxDate == null) taxDate = LocalDate.now().toString(); boolean known = taxEstimateKnown(h, taxDate);
            String net = known ? amount(h.optString("currency"), FinanceMath.afterTax(gross, effectiveTaxRate(h, taxDate))) : "税务状态未知";
            estimate.setText("预计记录额 " + amount(h.optString("currency"), gross) + " · " + (known ? "税后情景 " + net : net + "（不等于免税）"));
        };
        actualReceived.setVisibility("已到账".equals(selectedItem(status)) ? View.VISIBLE : View.GONE);
        perShare.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int st, int c, int a) {} public void onTextChanged(CharSequence s, int st, int before, int count) { update.run(); } public void afterTextChanged(Editable e) {} });
        setSelectorListener(holding, new OnChooseItemListener() {
            @Override public boolean onChooseBefore(CharSequence item, int which) { return true; }
            @Override public void onChooseAfter(CharSequence[] items, CharSequence[] selectedItems, Integer[] selectedValues) { update.run(); }
        });
        setSelectorListener(status, new OnChooseItemListener() {
            @Override public boolean onChooseBefore(CharSequence item, int which) { return true; }
            @Override public void onChooseAfter(CharSequence[] items, CharSequence[] selectedItems, Integer[] selectedValues) {
                actualReceived.setVisibility("已到账".equals(selectedItem(status)) ? View.VISIBLE : View.GONE);
            }
        });
        LinearLayout f = form(); f.addView(field("关联持仓", holding)); f.addView(field("每股/每份分红金额", perShare)); f.addView(estimate, margin(3, -1, 0, 5)); f.addView(field("派息日", payDate)); f.addView(field("登记日（可选）", recordDate)); f.addView(field("除息日（可选）", exDate)); f.addView(field("状态", status)); f.addView(field("实际到账金额（可选，仅已到账时）", actualReceived)); f.addView(field("备注", note));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("添加分红事件").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> { JSONObject h = selectedJson(holding); double p = parseNumber(perShare); LocalDate date = parseDate(payDate.getText().toString());
            if (h == null || p <= 0) { perShare.setError("分红金额需大于 0"); return; } if (date == null) { payDate.setError("请选择有效日期"); return; }
            String state = "已到账".equals(selectedItem(status)) ? "received" : "expected"; String taxDate = blankToNull(recordDate.getText().toString()); if (taxDate == null) taxDate = LocalDate.now().toString();
            double rate = effectiveTaxRate(h, taxDate); boolean known = taxEstimateKnown(h, taxDate);
            String actualText = actualReceived.getText().toString().trim(); Double actual = null;
            if (!actualText.isEmpty()) { try { actual = Double.parseDouble(actualText); } catch (NumberFormatException ignored) { actual = -1d; } }
            if (actual != null && (!Double.isFinite(actual) || actual < 0d)) { actualReceived.setError("实际到账金额不能为负或无效"); return; }
            if (actual != null && !"received".equals(state)) { actualReceived.setError("请先将状态设为已到账"); return; }
            db.addDividend(h.optLong("_id"), h.optLong("account_id"), p, p * h.optDouble("quantity"), h.optString("currency"), blankToNull(recordDate.getText().toString()), blankToNull(exDate.getText().toString()), date.toString(), state, rate, known, actual, note.getText().toString().trim());
            hideKeyboard(d); d.dismiss(); render(); toast("分红事件已保存"); })); showFormDialog(d); update.run();
    }
    private void showTransactionDialog(long preselectedId) {
        JSONArray holdings = db.holdings(selectedAccount); if (holdings.length() == 0) {
            AlertDialog prompt = new MaterialAlertDialogBuilder(this).setTitle("还没有持仓").setMessage("交易需要关联持仓。").setNegativeButton("取消", null).setPositiveButton("新增持仓", null).create();
            prompt.setOnShowListener(v -> prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                prompt.dismiss(); new Handler(Looper.getMainLooper()).post(() -> showHoldingEditor(null, null));
            })); prompt.show(); return;
        }
        MiuixListView holding = holdingSpinner(holdings, preselectedId), side = spinner(new String[]{"买入", "卖出"}); EditText date = dateEdit(LocalDate.now().toString(), false);
        EditText quantity = numberEdit("", "数量"), price = numberEdit("", "成交单价"), fees = numberEdit("0", "手续费"); EditText note = edit("", "备注（可选）");
        LinearLayout f = form(); f.addView(field("持仓", holding)); f.addView(field("方向", side)); f.addView(field("日期", date)); f.addView(field("数量", quantity)); f.addView(field("成交单价", price)); f.addView(field("手续费", fees)); f.addView(field("备注", note)); f.addView(text("买入手续费并入加权成本；卖出减少股数并保持原每份成本，不能超卖。", 10, MUTED, false));
        AlertDialog d = new MaterialAlertDialogBuilder(this).setTitle("新增交易记录").setView(wrapForm(f)).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> { JSONObject h = selectedJson(holding); double q = parseNumber(quantity), p = parseNumber(price), fee = parseNumber(fees); LocalDate dt = parseDate(date.getText().toString());
            if (h == null || q <= 0 || p < 0 || fee < 0) { quantity.setError("数量需大于 0，价格/手续费不能为负"); return; } if (dt == null) { date.setError("日期无效"); return; }
            if (!db.addTransaction(h.optLong("_id"), h.optLong("account_id"), selectedItem(side), dt.toString(), q, p, fee, note.getText().toString())) { quantity.setError("卖出数量不能超过当前持仓，未保存"); return; }
            hideKeyboard(d); d.dismiss(); render(); toast("交易已保存"); })); showFormDialog(d);
    }

    private void chooseAccount() {
        JSONArray accounts = db.accounts(); String[] labels = new String[accounts.length() + 1]; long[] ids = new long[accounts.length() + 1]; labels[0] = "全部账户";
        for (int i = 0; i < accounts.length(); i++) { JSONObject a = accounts.optJSONObject(i); labels[i + 1] = a.optString("name"); ids[i + 1] = a.optLong("_id"); }
        int checked = 0; for (int i = 0; i < ids.length; i++) if (ids[i] == selectedAccount) checked = i;
        new MaterialAlertDialogBuilder(this).setTitle("筛选账户").setSingleChoiceItems(labels, checked, (d, w) -> { selectedAccount = ids[w]; d.dismiss(); render(); }).setNegativeButton("取消", null).show();
    }
    private void exportBackup() { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(ExcelBackup.MIME); i.putExtra(Intent.EXTRA_TITLE, "suizhang-backup-" + LocalDate.now() + ".xlsx"); try { startActivityForResult(i, 42); } catch (Exception e) { toast("当前设备无法打开保存界面"); } }
    private void importBackup() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json", "text/plain"}); try { startActivityForResult(i, 43); } catch (Exception e) { toast("当前设备无法打开文件选择器"); } }

    private MiuixListView accountSpinner(JSONArray rows, long preferred) {
        ArrayList<JSONObject> values = new ArrayList<>(); ArrayList<String> labels = new ArrayList<>(); int index = 0;
        for (int i = 0; i < rows.length(); i++) { JSONObject a = rows.optJSONObject(i); values.add(a); labels.add(a.optString("name")); if (a.optLong("_id") == preferred) index = i; }
        MiuixListView s = spinner(labels.toArray(new String[0])); s.setTag(values); selectSpinner(s, labels.get(index)); return s;
    }
    private MiuixListView holdingSpinner(JSONArray rows, long preferred) {
        ArrayList<JSONObject> values = new ArrayList<>(); ArrayList<String> labels = new ArrayList<>(); int index = 0;
        for (int i = 0; i < rows.length(); i++) { JSONObject h = rows.optJSONObject(i); values.add(h); labels.add(h.optString("name") + (h.optString("code").isEmpty() ? "" : " · " + h.optString("code")) + " · " + h.optString("currency")); if (h.optLong("_id") == preferred) index = i; }
        MiuixListView s = spinner(labels.toArray(new String[0])); s.setTag(values); selectSpinner(s, labels.get(index)); return s;
    }
    private JSONObject selectedJson(MiuixListView s) { Object tag = s.getTag(); if (!(tag instanceof ArrayList)) return null; ArrayList<?> rows = (ArrayList<?>) tag; int pos = selectedIndex(s); return pos >= 0 && pos < rows.size() ? (JSONObject) rows.get(pos) : null; }
    private MiuixListView spinner(String[] values) {
        MiuixListView selector = new MiuixListView(this);
        selector.setMultipleChoiceEnabled(false); selector.setItems(values);
        if (values.length > 0) { selector.setSelectedValues(new Integer[]{0}); selector.setTip(values[0]); }
        selector.setMinimumHeight(dimen(R.dimen.miuix_basic_min_height)); selector.setFocusable(true); selector.setOnClickListener(v -> { }); setSelectorListener(selector, null);
        return selector;
    }
    private void setSelectorListener(MiuixListView selector, OnChooseItemListener delegate) {
        selector.setOnChooseItemListener(new OnChooseItemListener() {
            @Override public boolean onChooseBefore(CharSequence item, int which) { return delegate == null || delegate.onChooseBefore(item, which); }
            @Override public void onChooseAfter(CharSequence[] items, CharSequence[] selectedItems, Integer[] selectedValues) {
                if (selectedValues != null && selectedValues.length > 0 && selectedValues[0] != null) {
                    int index = selectedValues[0]; CharSequence[] entries = selector.getItems();
                    if (entries != null && index >= 0 && index < entries.length) selector.setTip(entries[index]);
                }
                if (delegate != null) delegate.onChooseAfter(items, selectedItems, selectedValues);
            }
        });
    }
    private void selectSpinner(MiuixListView selector, String value) {
        CharSequence[] entries = selector.getItems();
        for (int i = 0; i < entries.length; i++) if (value.equals(String.valueOf(entries[i]))) {
            selector.setSelectedValues(new Integer[]{i}); selector.setTip(entries[i]); return;
        }
    }
    private int selectedIndex(MiuixListView selector) {
        Integer[] selected = selector.getSelectedValues();
        if (selected != null && selected.length > 0 && selected[0] != null) return selected[0];
        CharSequence[] entries = selector.getItems(); CharSequence tip = selector.getTip();
        if (entries != null && tip != null) for (int i = 0; i < entries.length; i++) if (tip.equals(entries[i])) return i;
        return entries == null || entries.length == 0 ? -1 : 0;
    }
    private String selectedItem(MiuixListView selector) {
        CharSequence[] entries = selector.getItems(); int index = selectedIndex(selector);
        return entries != null && index >= 0 && index < entries.length ? String.valueOf(entries[index]) : "";
    }

    private void sectionHeader(String title, String action, Runnable onClick) { LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL); r.setMinimumHeight(dimen(R.dimen.ds_touch_target_min)); r.addView(tokenText(title, R.dimen.ds_type_section_title, INK, true), new LinearLayout.LayoutParams(0, dimen(R.dimen.ds_touch_target_min), 1)); TextView a = tokenText(action + "  ›", R.dimen.ds_type_metadata, ACCENT, true); a.setGravity(Gravity.CENTER); a.setMinHeight(dimen(R.dimen.ds_touch_target_min)); a.setPadding(dp(8), 0, dp(8), 0); a.setContentDescription(action); a.setOnClickListener(v -> onClick.run()); r.addView(a); content.addView(r); }
    private int marketMoveColor(double changePercent) { return changePercent > 0d ? MARKET_UP : changePercent < 0d ? MARKET_DOWN : INK; }
    private String marketMoveLabel(double changePercent) {
        String value = String.format(Locale.US, "%.2f%%", changePercent);
        return changePercent > 0d ? "上涨 +" + value : changePercent < 0d ? "下跌 " + value : "平盘 0.00%";
    }
    private String accountScopeLabel() { return "账户范围：" + accountLabel() + "（在总览切换）"; }
    private String accessibilityPaneTitle() {
        if ("holdings".equals(activeTab)) return "持仓";
        if ("calendar".equals(activeTab)) return "日历";
        if ("more".equals(activeTab)) return "更多";
        if ("stats".equals(activeTab)) return "统计报表";
        return "总览";
    }
    private void pageTitle(String title, String subtitle) { content.addView(tokenText(title, R.dimen.ds_type_page_title, INK, true), margin(0, 2, 0, 3)); content.addView(tokenText(subtitle, R.dimen.ds_type_secondary, MUTED, false), margin(0, 0, 0, 11)); }
    private void emptyInfoCard(String title, String description) {
        CardColumn c = card(); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.addView(text(title, 14, INK, true));
        c.addView(text(description, 11, MUTED, false), margin(0, 4, 0, 0));
        content.addView(c, margin(0, 5, 0, 9));
    }
    private void emptyCard(String title, String description, String button, Runnable onClick) { CardColumn c = card(); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_HORIZONTAL); c.setPadding(dp(18), dp(20), dp(18), dp(16)); TextView t = text(title, 15, INK, true); t.setGravity(Gravity.CENTER); c.addView(t); TextView d = text(description, 11, MUTED, false); d.setGravity(Gravity.CENTER); c.addView(d, margin(0, 6, 0, 10)); c.addView(actionButton(button, false, onClick)); content.addView(c, margin(0, 6, 0, 10)); }
    private CardColumn card() { return new CardColumn(); }
    private View divider() { View v = new View(this); v.setBackgroundColor(LINE); v.setLayoutParams(margin(0, 7, 0, 7)); return v; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private MaterialButton actionButton(String label, boolean primary, Runnable onClick) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label); b.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ds_type_button)); b.setAllCaps(false); b.setTypeface(Typeface.create(appTypeface, Typeface.BOLD));
        b.setInsetTop(0); b.setInsetBottom(0); b.setMinHeight(dimen(R.dimen.ds_touch_target_min)); b.setMinimumHeight(dimen(R.dimen.ds_touch_target_min)); b.setMinWidth(dimen(R.dimen.ds_touch_target_min)); b.setMinimumWidth(dimen(R.dimen.ds_touch_target_min)); b.setGravity(Gravity.CENTER);
        b.setCornerRadius(dimen(R.dimen.ds_control_radius));
        b.setBackgroundTintList(ColorStateList.valueOf(getColor(primary ? R.color.app_primary : R.color.app_primary_container)));
        b.setTextColor(getColor(primary ? R.color.app_on_primary : R.color.app_on_primary_container));
        if (!primary) { b.setStrokeWidth(dp(1)); b.setStrokeColor(ColorStateList.valueOf(LINE)); }
        b.setContentDescription(label); b.setOnClickListener(v -> onClick.run()); return b;
    }
    private MaterialButton gridActionButton(String label, boolean primary, Runnable onClick) {
        MaterialButton button = actionButton(label, primary, onClick);
        button.setMaxLines(2);
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        float density = getResources().getDisplayMetrics().density;
        int twoLineHeight = (int) Math.ceil(button.getTextSize() * 2.4f / density) + dp(12);
        int height = Math.max(dp(56), twoLineHeight);
        button.setMinHeight(height); button.setMinimumHeight(height);
        return button;
    }
    private LinearLayout actionButtonRow(MaterialButton left, MaterialButton right) {
        LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setBaselineAligned(false);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        View gap = new View(this); gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(gap, new LinearLayout.LayoutParams(dimen(R.dimen.ds_space_compact), 1));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }
    private void setStartIcon(TextView view, int iconResource, int tint, int sizeDp) {
        android.graphics.drawable.Drawable icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, iconResource);
        if (icon == null) return;
        icon = icon.mutate(); icon.setTint(tint); int size = dp(sizeDp); icon.setBounds(0, 0, size, size);
        view.setCompoundDrawablesRelative(icon, null, null, null); view.setCompoundDrawablePadding(dp(5));
    }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); float metadataSize = getResources().getDimension(R.dimen.ds_type_metadata) / getResources().getDisplayMetrics().scaledDensity; t.setTextSize(Math.max(size, metadataSize)); t.setTextColor(color); t.setTypeface(bold ? Typeface.create(appTypeface, Typeface.BOLD) : appTypeface); t.setGravity(Gravity.CENTER_VERTICAL); t.setLineSpacing(dimen(size <= 11 ? R.dimen.ds_line_spacing_compact : R.dimen.ds_line_spacing_body), 1f); return t; }
    private TextView tokenText(String value, int sizeResource, int color, boolean bold) { TextView t = text(value, 12, color, bold); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(sizeResource)); return t; }
    private int dimen(int resource) { return getResources().getDimensionPixelSize(resource); }
    private LinearLayout form() { LinearLayout f = new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL); f.setPadding(dp(2), 0, dp(2), 0); return f; }
    private ScrollView wrapForm(LinearLayout f) { FormScrollView s = new FormScrollView(this); s.setFillViewport(false); s.setPadding(dp(20), dp(5), dp(20), dp(4)); s.setClipToPadding(false); s.addView(f); return s; }
    private final class FormScrollView extends ScrollView {
        FormScrollView(Context context) { super(context); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxHeight = Math.max(dp(220), Math.min(dp(680), (int) (getResources().getDisplayMetrics().heightPixels * .78f)));
            int mode = MeasureSpec.getMode(heightMeasureSpec), size = MeasureSpec.getSize(heightMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
    private void showFormDialog(AlertDialog dialog) {
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void restoreParentAfterDismiss(AlertDialog child, AlertDialog parent) {
        if (parent == null) return;
        child.setOnDismissListener(ignored -> restoreParentDialog(parent));
    }
    private void restoreParentDialog(AlertDialog parent) {
        if (parent == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isFinishing() && !isDestroyed() && !parent.isShowing()) parent.show();
        });
    }
    private LinearLayout field(String label, View input) {
        LinearLayout f = new LinearLayout(this); f.setOrientation(LinearLayout.VERTICAL);
        if (input instanceof EditText) {
            EditText e = (EditText) input; e.setTextSize(16); e.setSingleLine(true); e.setMinHeight(dp(48));
            CharSequence placeholder = e.getHint(); e.setHint(null);
            TextInputLayout box = new TextInputLayout(this);
            box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); box.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
            box.setHint(label); box.addView(e, new LinearLayout.LayoutParams(-1, -2));
            if (placeholder != null && placeholder.length() > 0 && !placeholder.toString().contentEquals(label)) box.setPlaceholderText(placeholder);
            f.addView(box, new LinearLayout.LayoutParams(-1, -2));
        } else if (input instanceof MiuixBasicView) {
            MiuixBasicView selector = (MiuixBasicView) input; selector.setTitle(label); selector.setMinimumHeight(dimen(R.dimen.miuix_basic_min_height)); selector.setFocusable(true);
            f.addView(selector, new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView labelView = text(label, 12, MUTED, true); labelView.setLabelFor(input.getId());
            f.addView(labelView, margin(0, 0, 0, 4)); input.setMinimumHeight(dp(48)); f.addView(input, new LinearLayout.LayoutParams(-1, -2));
        }
        return (LinearLayout) withMargins(f, 0, 0, 0, 9);
    }
    private EditText edit(String initial, String hint) { FormEditText e = new FormEditText(this); e.setId(View.generateViewId()); e.setText(initial); e.setHint(hint); return e; }
    private EditText numberEdit(String initial, String hint) { EditText e = edit(initial, hint); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED); return e; }
    private EditText dateEdit(String initial, boolean optional) {
        EditText e = edit(initial, optional ? "可留空" : "选择日期"); e.setFocusable(false); e.setClickable(true);
        e.setOnClickListener(v -> {
            Runnable chooseDate = () -> {
                LocalDate initialDate = parseDate(e.getText().toString()); if (initialDate == null) initialDate = LocalDate.now();
                long selection = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker().setTitleText("选择日期").setSelection(selection).build();
                picker.addOnPositiveButtonClickListener(selected -> e.setText(LocalDate.ofEpochDay(selected / 86_400_000L).toString()));
                picker.show(getSupportFragmentManager(), "suizhang-date-" + e.getId());
            };
            if (optional) new MaterialAlertDialogBuilder(this).setTitle("日期操作").setItems(new String[]{"选择日期", "清空日期"}, (dialog, which) -> { if (which == 0) new Handler(Looper.getMainLooper()).post(chooseDate); else e.setText(""); }).setNegativeButton("取消", null).show();
            else chooseDate.run();
        });
        return e;
    }
    private final class FormEditText extends TextInputEditText {
        FormEditText(Context context) { super(context); }
        @Override public void setError(CharSequence error) {
            ViewParent parent = getParent();
            while (parent instanceof View) {
                if (parent instanceof TextInputLayout) { ((TextInputLayout) parent).setError(error); return; }
                parent = ((View) parent).getParent();
            }
            super.setError(error);
        }
    }
    private final class CardColumn extends MiuixCardView {
        private final LinearLayout body;
        CardColumn() {
            super(MainActivity.this);
            body = new LinearLayout(MainActivity.this); body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dimen(R.dimen.ds_card_inset_horizontal), dimen(R.dimen.ds_card_inset_vertical), dimen(R.dimen.ds_card_inset_horizontal), dimen(R.dimen.ds_card_inset_vertical));
            super.addView(body, new FrameLayout.LayoutParams(-1, -2));
            setCardBackgroundColor(getColor(R.color.app_surface));
            setRadius(dimen(R.dimen.ds_card_radius)); setCardElevation(dimen(R.dimen.ds_card_elevation)); setUseCompatPadding(false); setPreventCornerOverlap(false);
        }
        @Override public void addView(View child) { if (body == null) super.addView(child); else body.addView(child); }
        @Override public void addView(View child, ViewGroup.LayoutParams params) { if (body == null) super.addView(child, params); else body.addView(child, params); }
        public void setOrientation(int orientation) { body.setOrientation(orientation); }
        public void setGravity(int gravity) { body.setGravity(gravity); }
        @Override public void setPadding(int left, int top, int right, int bottom) { if (body == null) super.setPadding(left, top, right, bottom); else body.setPadding(left, top, right, bottom); }
    }
    private View calendarLegendItem(String label, int color) {
        LinearLayout item = row(); item.setGravity(Gravity.CENTER);
        View dot = calendarMarkerDot(color, 8, false);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.setMargins(dp(1), 0, dp(5), 0); item.addView(dot, dotParams);
        TextView title = text(label, 10, MUTED, true); title.setGravity(Gravity.CENTER);
        item.addView(title, new LinearLayout.LayoutParams(-2, -2));
        return item;
    }
    private View calendarMarkerDot(int color, int sizeDp, boolean selected) {
        View marker = new View(this);
        GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL); dot.setColor(color);
        if (selected) dot.setStroke(dp(1), getColor(R.color.app_on_primary));
        marker.setBackground(dot); marker.setMinimumWidth(dp(sizeDp)); marker.setMinimumHeight(dp(sizeDp));
        marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return marker;
    }
    private GradientDrawable roundDp(int color, int radiusDp, int stroke, int strokeWidthDp) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); if (strokeWidthDp > 0) d.setStroke(dp(strokeWidthDp), stroke); return d; }
    private GradientDrawable roundPx(int color, int radiusPx, int stroke, int strokeWidthPx) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radiusPx); if (strokeWidthPx > 0) d.setStroke(strokeWidthPx, stroke); return d; }
    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private View withMargins(View v, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); v.setLayoutParams(p); return v; }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void refreshQuotes(JSONArray holdings) {
        long now = System.currentTimeMillis(); if (quoteRefreshInFlight) return;
        ArrayList<MarketDataClient.Security> securities = new ArrayList<>(); java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.optJSONObject(i); String code = h.optString("code"), market = h.optString("market");
            if (code.isEmpty() || !("A股".equals(market) || "港股".equals(market))) continue;
            String key = market + ":" + code.toUpperCase(Locale.ROOT); if (seen.add(key)) securities.add(new MarketDataClient.Security(code, h.optString("name"), market, h.optString("currency")));
        }
        if (securities.isEmpty()) return;
        Collections.sort(securities, Comparator.comparing(s -> s.market + ":" + s.code.toUpperCase(Locale.ROOT)));
        StringBuilder signature = new StringBuilder(); for (MarketDataClient.Security s : securities) signature.append(s.market).append(':').append(s.code.toUpperCase(Locale.ROOT)).append(';');
        String currentSignature = signature.toString(); if (currentSignature.equals(lastQuoteSignature) && now - lastQuoteRefresh < MarketDataClient.QUOTE_TTL_MS) return;
        lastQuoteSignature = currentSignature;
        quoteRefreshInFlight = true; lastQuoteRefresh = now;
        dataExecutor.execute(() -> {
            MarketDataClient.Snapshot<MarketDataClient.Quote> snap = marketData.quotes(securities);
            runOnUiThread(() -> {
                quoteRefreshInFlight = false; if (isFinishing()) return;
                for (MarketDataClient.Quote q : snap.rows) quotes.put(q.code.toUpperCase(Locale.ROOT), q);
                if ("holdings".equals(activeTab)) render();
                else if (snap.rows.isEmpty() && snap.warning != null) toast(snap.warning);
            });
        });
    }
    private MarketDataClient.Quote quoteFor(JSONObject h) {
        String code = h.optString("code"); if (code.isEmpty()) return null;
        String key = code.toUpperCase(Locale.ROOT); MarketDataClient.Quote q = quotes.get(key);
        if (q == null) { q = marketData.cachedQuote(code, h.optString("market")); if (q != null) quotes.put(key, q); }
        return q;
    }
    private String timeLabel(long time) { if (time <= 0) return "时间未知"; return java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(time)); }

    private double effectiveTaxRate(JSONObject h, String asOf) {
        if ("manual".equals(h.optString("tax_mode", "manual"))) return clampRate(h.optDouble("tax_rate"));
        String market = h.optString("market");
        if ("A股".equals(market)) {
            String taxDate = asOf == null || asOf.trim().isEmpty() ? LocalDate.now().toString() : asOf.trim();
            LocalDate date;
            try { date = LocalDate.parse(taxDate); } catch (RuntimeException invalidDate) { return 0d; }
            if (!date.toString().equals(taxDate) || !taxEstimateKnown(h, taxDate)) return 0d;
            JSONArray lots = db.taxLotsAt(h.optLong("_id"), taxDate); ArrayList<FinanceMath.Lot> taxLots = new ArrayList<>();
            for (int i = 0; i < lots.length(); i++) {
                JSONObject lot = lots.optJSONObject(i); if (lot == null) return 0d;
                double qty = lot.optDouble("quantity", Double.NaN); LocalDate opened = parseDate(lot.optString("opened_on")); if (opened == null) return 0d;
                taxLots.add(new FinanceMath.Lot(qty, opened));
            }
            return clampRate(FinanceMath.aShareRecordDateTaxRate(taxLots, date, settingDouble("tax_a_short", .2), settingDouble("tax_a_mid", .1), settingDouble("tax_a_long", 0)));
        }
        if ("港股".equals(market)) return "港股通".equals(setting("tax_hk_channel", "未确认")) ? .20d : 0d;
        if ("美股".equals(market)) {
            String status = setting("tax_us_status", "未确认");
            if (status.startsWith("符合协定")) return .10d;
            if (status.startsWith("不适用协定")) return .30d;
            return 0d;
        }
        return 0d;
    }
    private boolean taxEstimateKnown(JSONObject h, String asOf) {
        if ("manual".equals(h.optString("tax_mode", "manual"))) return true;
        String market = h.optString("market");
        if ("A股".equals(market)) {
            if (!setting("tax_identity", "未确认").startsWith("中国内地个人")) return false;
            String taxDate = asOf == null || asOf.trim().isEmpty() ? LocalDate.now().toString() : asOf.trim();
            LocalDate date;
            try { date = LocalDate.parse(taxDate); } catch (RuntimeException invalidDate) { return false; }
            if (!date.toString().equals(taxDate)) return false;
            return db.hasCompleteTaxLotCoverage(h.optLong("_id"), taxDate,
                    h.optDouble("quantity", Double.NaN), date.equals(LocalDate.now()));
        }
        if ("港股".equals(market)) return "港股通".equals(setting("tax_hk_channel", "未确认"));
        if ("美股".equals(market)) return !"未确认".equals(setting("tax_us_status", "未确认"));
        return false;
    }
    private Map<String, Double> estimatedIncomeByCurrency(boolean net) { Map<String, Double> sums = new TreeMap<>(); JSONArray hs = db.holdings(selectedAccount); for (int i = 0; i < hs.length(); i++) { JSONObject h = hs.optJSONObject(i); double value = annualOf(h, net); if (Double.isFinite(value) && value > 0d) { String c = h.optString("currency", "CNY"); sums.put(c, sums.getOrDefault(c, 0d) + value); } } return sums; }
    private double annualOf(JSONObject h, boolean net) { double gross = FinanceMath.annualDividend(h.optDouble("quantity"), h.optDouble("annual_dividend_per_unit")); if (!net) return gross; String today = LocalDate.now().toString(); if (!taxEstimateKnown(h, today)) return Double.NaN; return FinanceMath.afterTax(gross, clampRate(effectiveTaxRate(h, today))); }
    private double annualForSort(JSONObject h, boolean net) { double value = annualOf(h, net); return Double.isFinite(value) ? value : 0d; }
    private Map<String, Double> costByCurrency() { Map<String, Double> sums = new TreeMap<>(); JSONArray hs = db.holdings(selectedAccount); for (int i = 0; i < hs.length(); i++) { JSONObject h = hs.optJSONObject(i); String c = h.optString("currency", "CNY"); sums.put(c, sums.getOrDefault(c, 0d) + h.optDouble("quantity") * h.optDouble("cost")); } return sums; }
    private Map<String, Double> receivedAllTime() { Map<String, Double> sums = new TreeMap<>(); JSONArray rows = db.receivedAllTime(selectedAccount); for (int i = 0; i < rows.length(); i++) { JSONObject r = rows.optJSONObject(i); sums.put(r.optString("currency", "CNY"), r.optDouble("total", 0)); } return sums; }
    private String costMethodLabel(String method) {
        if (FinanceMath.COST_DIVIDEND_ADJUSTED.equals(method)) return "分红摊薄";
        if (FinanceMath.COST_DILUTED.equals(method)) return "摊薄成本";
        return "加权平均";
    }
    private String costMethodValue(String label) {
        if ("分红摊薄".equals(label)) return FinanceMath.COST_DIVIDEND_ADJUSTED;
        if ("摊薄成本".equals(label)) return FinanceMath.COST_DILUTED;
        return FinanceMath.COST_WEIGHTED_AVERAGE;
    }
    private long settingLong(String key, long fallback) { try { return Long.parseLong(db.setting(key, String.valueOf(fallback))); } catch (Exception e) { return fallback; } }
    private boolean hasUnknownTaxEstimate() {
        JSONArray hs = db.holdings(selectedAccount);
        for (int i = 0; i < hs.length(); i++) { JSONObject h = hs.optJSONObject(i); if (h != null && h.optDouble("quantity") > 0 && h.optDouble("annual_dividend_per_unit") > 0 && !taxEstimateKnown(h, LocalDate.now().toString())) return true; }
        return false;
    }
    private double holdingMarketValue(JSONObject h) {
        MarketDataClient.Quote quote = quoteFor(h); double quantity = h.optDouble("quantity");
        return quote == null ? Math.max(0d, quantity * h.optDouble("cost")) : Math.max(0d, quantity * quote.price);
    }
    private Map<String, Double> marketValueByCurrency(JSONArray holdings) {
        Map<String, Double> sums = new TreeMap<>();
        for (int i = 0; i < holdings.length(); i++) { JSONObject h = holdings.optJSONObject(i); if (h == null) continue; String c = h.optString("currency", "CNY"); sums.put(c, sums.getOrDefault(c, 0d) + holdingMarketValue(h)); }
        return sums;
    }
    private Map<String, Double> floatingProfitByCurrency(JSONArray holdings) {
        Map<String, Double> sums = new TreeMap<>();
        for (int i = 0; i < holdings.length(); i++) { JSONObject h = holdings.optJSONObject(i); if (h == null) continue; MarketDataClient.Quote quote = quoteFor(h); if (quote == null) continue; String c = h.optString("currency", "CNY"); double value = FinanceMath.floatingProfit(holdingMarketValue(h), h.optDouble("quantity") * h.optDouble("cost")); sums.put(c, sums.getOrDefault(c, 0d) + value); }
        return sums;
    }
    private double safeAnnualExpense(String period, double amount) { try { return FinanceMath.annualExpense(period, amount); } catch (Exception e) { return 0; } }
    private double totalGoalForCurrency(String currency) { double sum = 0; JSONArray goals = db.goals(); for (int i = 0; i < goals.length(); i++) { JSONObject g = goals.optJSONObject(i); if (currency.equals(g.optString("currency"))) sum += safeAnnualExpense(g.optString("period"), g.optDouble("amount")); } return sum; }
    private double settingDouble(String key, double fallback) { try { double v = Double.parseDouble(db.setting(key, String.valueOf(fallback))); return Double.isFinite(v) ? v : fallback; } catch (Exception e) { return fallback; } }
    private String setting(String key, String fallback) { return db.setting(key, fallback); }
    private double clampRate(double rate) { return Math.max(0, Math.min(1, rate)); }
    private Map<String, Double> scaleMap(Map<String, Double> source, double scale) { Map<String, Double> out = new TreeMap<>(); for (Map.Entry<String, Double> e : source.entrySet()) out.put(e.getKey(), e.getValue() * scale); return out; }
    private String formatAmounts(Map<String, Double> values) { if (values.isEmpty()) return "—"; ArrayList<String> parts = new ArrayList<>(); for (Map.Entry<String, Double> e : values.entrySet()) parts.add(amount(e.getKey(), e.getValue())); return android.text.TextUtils.join("  /  ", parts); }
    private String formatPercentMap(Map<String, Double> values) { if (values.isEmpty()) return "—"; ArrayList<String> parts = new ArrayList<>(); for (Map.Entry<String, Double> e : values.entrySet()) parts.add(e.getKey() + " " + percent(e.getValue())); return android.text.TextUtils.join("  /  ", parts); }
    private String percent(double rate) { return moneyFormat.format(rate * 100d) + "%"; }
    private String amount(String currency, double value) { String prefix; switch (currency == null ? "" : currency) { case "CNY": prefix = "¥"; break; case "HKD": prefix = "HK$"; break; case "USD": prefix = "$"; break; default: prefix = currency == null || currency.isEmpty() ? "" : currency + " "; } return prefix + moneyFormat.format(value); }
    private String compact(double value) { if (Math.abs(value - Math.rint(value)) < 1e-7) return String.format(Locale.US, "%.0f", value); return new DecimalFormat("#,##0.####").format(value); }
    private String shortDate(String value) { LocalDate d = parseDate(value); return d == null ? value : d.format(DateTimeFormatter.ofPattern("M月d日")); }
    private LocalDate parseDate(String value) { try { return LocalDate.parse(value == null ? "" : value.trim(), ISO); } catch (Exception ignored) { return null; } }
    private String blankToNull(String value) { String v = value == null ? "" : value.trim(); return v.isEmpty() ? null : v; }
    private double parseNumber(EditText e) { try { double v = Double.parseDouble(e.getText().toString().trim()); return Double.isFinite(v) ? v : 0; } catch (Exception ignored) { return 0; } }
    private String accountLabel() { if (selectedAccount == 0) return "全部账户"; JSONArray a = db.accounts(); for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optLong("_id") == selectedAccount) return a.optJSONObject(i).optString("name"); selectedAccount = 0; return "全部账户"; }
    private String safeMessage(Exception e) { String m = e.getMessage(); return m == null || m.trim().isEmpty() ? "请检查文件" : m; }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard(AlertDialog d) { try { InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null && d != null && d.getCurrentFocus() != null) imm.hideSoftInputFromWindow(d.getCurrentFocus().getWindowToken(), 0); } catch (Exception ignored) { } }
}
