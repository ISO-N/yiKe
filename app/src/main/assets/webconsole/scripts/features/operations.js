import {
    elements,
    escapeHtml,
    fetchJson,
    formatDateTime,
    formatFileSize,
    getFieldChecked,
    getFieldValue,
    metricCard,
    postJson,
    requestShellRefresh,
    renderEmptyState,
    resolveDownloadFileName,
    showMessage,
    state,
    updateWorkspaceFeedback,
} from "../shared/core.js";

let restoreCompletedHandler = null;

/**
 * 维护动作回调可配置，是为了让模块内部不直接依赖壳层实现也能在恢复后触发全局刷新。
 */
export function setMaintenanceCallbacks(callbacks) {
    restoreCompletedHandler = callbacks?.onRestoreCompleted ?? null;
}

/**
 * 概览工作区读取集中处理，是为了让最近卡组和总览指标围绕同一份后台摘要更新。
 */
export async function loadDashboard() {
    const payload = await fetchJson("/api/web-console/v1/dashboard");
    if (!payload) return;
    document.querySelector("#overview-metrics").innerHTML = [
        metricCard("待复习卡片", payload.dueCardCount),
        metricCard("待复习问题", payload.dueQuestionCount),
        metricCard("最近卡组", payload.recentDecks.length),
    ].join("");
    const recentDecks = document.querySelector("#recent-decks");
    recentDecks.innerHTML = payload.recentDecks.length
        ? payload.recentDecks.map((item) => `
            <div class="item">
                <div class="item-head"><strong>${escapeHtml(item.name)}</strong><span class="muted">${item.dueQuestionCount} 题到期</span></div>
                <div class="muted">${item.cardCount} 张卡片 · ${item.questionCount} 个问题</div>
            </div>
        `).join("")
        : renderEmptyState("最近暂无卡组", "先在内容管理中创建一个卡组，网页端和手机端会共享同一批数据。");
}

/**
 * 搜索事件集中绑定，是为了让搜索工作区在壳层升级后继续使用独立的查询入口。
 */
export function bindSearchEvents() {
    document.querySelector("#search-form").addEventListener("submit", submitSearchForm);
    updateSearchFeedback("等待搜索条件", "输入关键词或标签后，结果会在同一工作区内展开，并保留继续练习的入口。");
}

/**
 * 设置和备份事件集中绑定，是为了让维护工作区的危险动作共用同一反馈层级。
 */
export function bindMaintenanceEvents() {
    elements.settingsForm.addEventListener("submit", submitSettingsForm);
    elements.exportBackupButton.addEventListener("click", exportBackup);
    elements.restoreBackupFileInput.addEventListener("change", handleRestoreBackupFileChange);
    elements.restoreBackupConfirmInput.addEventListener("change", updateRestoreControls);
    elements.restoreBackupButton.addEventListener("click", restoreBackup);
    elements.clearRestoreFileButton.addEventListener("click", clearRestoreSelection);
    syncBackupFeedback();
}

/**
 * 搜索提交集中处理，是为了让搜索结果结构和错误反馈在模块化后仍保持统一。
 */
export async function submitSearchForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const keyword = getFieldValue(form, "keyword").trim();
    const tag = getFieldValue(form, "tag").trim();
    if (!keyword && !tag) {
        showMessage("至少输入关键词或标签中的一项再开始搜索。", true);
        updateSearchFeedback("搜索条件不完整", "至少输入关键词或标签中的一项，系统才能保留这次搜索上下文。", "warning");
        return;
    }
    const payload = await postJson("/api/web-console/v1/search", {
        keyword,
        tag: tag || null,
    });
    if (!payload) {
        updateSearchFeedback("搜索暂时失败", "当前查询没有成功返回结果，请稍后重试或刷新登录状态。", "error");
        return;
    }
    state.lastSearchResults = payload;
    requestShellRefresh();
    document.querySelector("#search-results").innerHTML = payload.length
        ? payload.map((item) => `
            <div class="item">
                <div class="item-head"><strong>${escapeHtml(item.prompt)}</strong><span class="muted">${escapeHtml(item.deckName)} / ${escapeHtml(item.cardTitle)}</span></div>
                <div>${escapeHtml(item.answer)}</div>
                <div class="muted">阶段 ${item.stageIndex} · 复习 ${item.reviewCount} 次 · lapse ${item.lapseCount} 次</div>
                <div class="item-actions">
                    <button type="button" data-search-practice="${item.questionId}">练这题</button>
                </div>
            </div>
        `).join("")
        : renderEmptyState("没有找到结果", "换一个关键词、标签，或先确认目标问题是否已经同步到当前设备。");
    updateSearchFeedback(
        payload.length ? `找到 ${payload.length} 条结果` : "没有匹配结果",
        payload.length
            ? `当前筛选条件：${buildSearchCriteriaLabel(keyword, tag)}。可直接从结果进入题级练习。`
            : `当前筛选条件：${buildSearchCriteriaLabel(keyword, tag)}。可以改关键词、标签或回到内容工作台缩小范围。`,
        payload.length ? "success" : "empty"
    );
    document.querySelectorAll("[data-search-practice]").forEach((button) => {
        button.addEventListener("click", () => launchPracticeFromSearch(button.dataset.searchPractice));
    });
}

/**
 * 统计读取集中处理，是为了让富后台壳层后续升级时仍只依赖一份稳定指标结构。
 */
export async function loadAnalytics() {
    const payload = await fetchJson("/api/web-console/v1/analytics");
    if (!payload) {
        updateAnalyticsFeedback("统计暂不可用", "当前无法读取复习统计，请稍后刷新或重新登录后再试。", "error");
        return;
    }
    document.querySelector("#analytics-metrics").innerHTML = [
        metricCard("总复习次数", payload.totalReviews),
        metricCard("遗忘率", `${Math.round(payload.forgettingRate * 100)}%`),
        metricCard("平均耗时", payload.averageResponseTimeMs ? `${Math.round(payload.averageResponseTimeMs)} ms` : "暂无"),
    ].join("");
    document.querySelector("#analytics-breakdowns").innerHTML = payload.deckBreakdowns.length
        ? payload.deckBreakdowns.map((item) => `
            <div class="item">
                <div class="item-head"><strong>${escapeHtml(item.deckName)}</strong><span class="muted">${item.reviewCount} 次</span></div>
                <div class="muted">遗忘率 ${Math.round(item.forgettingRate * 100)}% · 平均耗时 ${item.averageResponseTimeMs ? Math.round(item.averageResponseTimeMs) : "-"} ms</div>
            </div>
        `).join("")
        : renderEmptyState("暂无统计数据", "先完成几次复习，网页后台才会展示按卡组拆分的统计结果。");
    updateAnalyticsFeedback(
        payload.deckBreakdowns.length ? "统计已同步" : "统计样本不足",
        payload.deckBreakdowns.length
            ? `已按 ${payload.deckBreakdowns.length} 个卡组整理复习表现，可继续定位最需要补课的主题。`
            : "当前还没有足够的复习样本来形成按卡组拆分的洞察。",
        payload.deckBreakdowns.length ? "success" : "empty"
    );
}

/**
 * 设置读取集中处理，是为了让提醒和主题配置继续围绕同一维护工作区更新。
 */
export async function loadSettings() {
    const payload = await fetchJson("/api/web-console/v1/settings");
    if (!payload) {
        updateSettingsFeedback("设置暂不可用", "提醒与主题配置暂时没有成功载入，请稍后重试。", "error");
        return;
    }
    elements.settingsForm.elements.namedItem("dailyReminderEnabled").checked = payload.dailyReminderEnabled;
    elements.settingsForm.elements.namedItem("dailyReminderHour").value = payload.dailyReminderHour;
    elements.settingsForm.elements.namedItem("dailyReminderMinute").value = payload.dailyReminderMinute;
    elements.settingsForm.elements.namedItem("themeMode").value = payload.themeMode;
    document.querySelector("#settings-backup-time").textContent = payload.backupLastAt
        ? `最近一次备份：${formatDateTime(payload.backupLastAt)}`
        : "当前还没有可记录的备份时间。";
    updateSettingsFeedback(
        "设置已同步",
        payload.dailyReminderEnabled
            ? `当前提醒时间为 ${payload.dailyReminderHour}:${String(payload.dailyReminderMinute).padStart(2, "0")}，主题模式为 ${payload.themeModeLabel}。`
            : `当前提醒已关闭，主题模式为 ${payload.themeModeLabel}。`,
        "success"
    );
    requestShellRefresh();
}

/**
 * 设置提交集中处理，是为了让设置工作区在模块化后仍复用同一条验证和保存路径。
 */
export async function submitSettingsForm(event) {
    event.preventDefault();
    const dailyReminderHour = Number(getFieldValue(elements.settingsForm, "dailyReminderHour") || 20);
    const dailyReminderMinute = Number(getFieldValue(elements.settingsForm, "dailyReminderMinute") || 0);
    if (!Number.isInteger(dailyReminderHour) || dailyReminderHour < 0 || dailyReminderHour > 23) {
        showMessage("提醒小时需在 0 到 23 之间。", true);
        updateSettingsFeedback("提醒时间不合法", "提醒小时需在 0 到 23 之间，请修正后再保存。", "warning");
        return;
    }
    if (!Number.isInteger(dailyReminderMinute) || dailyReminderMinute < 0 || dailyReminderMinute > 59) {
        showMessage("提醒分钟需在 0 到 59 之间。", true);
        updateSettingsFeedback("提醒时间不合法", "提醒分钟需在 0 到 59 之间，请修正后再保存。", "warning");
        return;
    }
    const response = await postJson("/api/web-console/v1/settings/update", {
        dailyReminderEnabled: getFieldChecked(elements.settingsForm, "dailyReminderEnabled"),
        dailyReminderHour,
        dailyReminderMinute,
        themeMode: getFieldValue(elements.settingsForm, "themeMode"),
    });
    if (!response) {
        updateSettingsFeedback("设置保存失败", "当前更改没有成功写入，请检查输入后再试。", "error");
        return;
    }
    showMessage(response.message);
    await loadSettings();
    updateSettingsFeedback("设置已保存", response.message, "success");
}

/**
 * 备份导出集中处理，是为了让下载流程和维护工作区的全局反馈继续共用同一入口。
 */
export async function exportBackup() {
    if (state.isExporting || state.isRestoring) {
        return;
    }
    state.isExporting = true;
    updateRestoreControls();
    updateBackupFeedback("正在导出备份", "系统正在准备下载文件，导出期间会暂时锁住恢复入口。", "warning");
    const response = await fetch("/api/web-console/v1/backup/export", { credentials: "include" });
    state.isExporting = false;
    updateRestoreControls();
    if (!response.ok) {
        const errorText = await response.text();
        showMessage(errorText || "导出备份失败。", true);
        updateBackupFeedback("导出备份失败", errorText || "当前备份没有成功导出，请稍后再试。", "error");
        return;
    }
    const text = await response.text();
    const blob = new Blob([text], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = resolveDownloadFileName(response.headers.get("Content-Disposition"));
    link.click();
    URL.revokeObjectURL(link.href);
    showMessage("备份文件已开始下载。");
    updateBackupFeedback("备份已开始下载", "导出成功后不会修改本地数据，你可以继续浏览其他工作区。", "success");
}

/**
 * 备份恢复集中处理，是为了让上传文件、危险确认和结果反馈围绕同一维护边界执行。
 */
export async function restoreBackup() {
    if (state.isRestoring || state.isExporting) {
        return;
    }
    if (!state.selectedBackupFile) {
        showMessage("请先选择要恢复的备份文件。", true);
        updateBackupFeedback("缺少备份文件", "恢复前需要先选择一个 JSON 备份文件。", "warning");
        return;
    }
    if (!elements.restoreBackupConfirmInput.checked) {
        showMessage("请先确认恢复会覆盖当前本地全部数据。", true);
        updateBackupFeedback("等待高风险确认", "恢复会覆盖当前本地全部数据，请先勾选确认项再继续。", "warning");
        return;
    }
    if (!window.confirm(`确认从 ${state.selectedBackupFile.name} 恢复？当前本地数据将被覆盖且无法撤销。`)) {
        updateBackupFeedback("恢复已取消", "你已取消本次恢复，当前本地数据保持不变。", "warning");
        return;
    }
    state.isRestoring = true;
    updateRestoreControls();
    updateBackupFeedback("正在恢复备份", "恢复期间会锁住导出和清空动作，完成后会自动刷新网页状态。", "warning");
    const content = await state.selectedBackupFile.text();
    const response = await postJson("/api/web-console/v1/backup/restore", {
        fileName: state.selectedBackupFile.name,
        content,
    });
    state.isRestoring = false;
    updateRestoreControls();
    if (!response) {
        updateBackupFeedback("恢复备份失败", "当前备份没有成功恢复，请确认文件格式和访问状态后重试。", "error");
        return;
    }
    showMessage(response.message);
    clearRestoreSelection();
    if (restoreCompletedHandler) {
        await restoreCompletedHandler();
    }
    updateBackupFeedback("备份已恢复", response.message, "success");
}

/**
 * 备份文件选择集中处理，是为了让维护工作区在模块化后仍保留统一的上传元信息展示。
 */
export function handleRestoreBackupFileChange(event) {
    const file = event.currentTarget.files?.[0] ?? null;
    state.selectedBackupFile = file;
    elements.restoreBackupConfirmInput.checked = false;
    elements.restoreBackupFileMeta.textContent = file
        ? `${file.name} · ${formatFileSize(file.size)}`
        : "尚未选择备份文件。";
    updateRestoreControls();
    syncBackupFeedback();
    requestShellRefresh();
}

/**
 * 清空恢复选择集中处理，是为了让高风险动作在用户取消后彻底回到初始态。
 */
export function clearRestoreSelection() {
    state.selectedBackupFile = null;
    elements.restoreBackupFileInput.value = "";
    elements.restoreBackupConfirmInput.checked = false;
    elements.restoreBackupFileMeta.textContent = "尚未选择备份文件。";
    updateRestoreControls();
    syncBackupFeedback();
    requestShellRefresh();
}

/**
 * 备份按钮状态集中计算，是为了让导出和恢复这两个互斥动作始终保持清晰反馈。
 */
export function updateRestoreControls() {
    elements.exportBackupButton.disabled = state.isExporting || state.isRestoring;
    elements.restoreBackupButton.disabled = !state.selectedBackupFile || !elements.restoreBackupConfirmInput.checked || state.isExporting || state.isRestoring;
    elements.clearRestoreFileButton.disabled = !state.selectedBackupFile || state.isRestoring;
    elements.exportBackupButton.textContent = state.isExporting ? "导出中…" : "导出备份 JSON";
    elements.restoreBackupButton.textContent = state.isRestoring ? "恢复中…" : "确认恢复";
    if (!state.isExporting && !state.isRestoring) {
        syncBackupFeedback();
    }
}

/**
 * 搜索结果可直接进入题级练习，是为了让搜索上下文真正成为学习入口而不是只读结果页。
 */
function launchPracticeFromSearch(questionId) {
    const result = state.lastSearchResults.find((item) => item.questionId === questionId);
    if (!result) {
        return;
    }
    state.practiceSelection.selectedDeckIds = new Set([result.deckId]);
    state.practiceSelection.selectedCardIds = new Set([result.cardId]);
    state.practiceSelection.selectedQuestionIds = new Set([result.questionId]);
    state.practiceSelection.cardsByDeckId = new Map();
    state.practiceSelection.questionsByCardId = new Map();
    requestShellRefresh();
    window.dispatchEvent(new CustomEvent("yike:launch-practice", {
        detail: {
            returnSection: "search",
            label: `搜索结果 / ${result.cardTitle}`,
        },
    }));
}

/**
 * 搜索反馈统一走共享工作区条，是为了让搜索输入校验、结果为空和命中结果拥有同一层级。
 */
function updateSearchFeedback(title, description, variant = "empty") {
    updateWorkspaceFeedback(elements.searchFeedback, title, description, variant);
}

/**
 * 统计反馈统一在工作区顶部表达，是为了让“暂无样本”和“接口失败”不会混进数据列表里。
 */
function updateAnalyticsFeedback(title, description, variant = "empty") {
    updateWorkspaceFeedback(elements.analyticsFeedback, title, description, variant);
}

/**
 * 设置反馈独立成条，是为了让表单校验失败和真正保存成功拥有清晰的不同层级。
 */
function updateSettingsFeedback(title, description, variant = "empty") {
    updateWorkspaceFeedback(elements.settingsFeedback, title, description, variant);
}

/**
 * 备份反馈集中推导，是为了让导出、待确认恢复和恢复中的高风险状态始终留在同一可见区域。
 */
function syncBackupFeedback() {
    if (state.selectedBackupFile && !elements.restoreBackupConfirmInput.checked) {
        updateBackupFeedback("等待高风险确认", `已选择 ${state.selectedBackupFile.name}，恢复前还需要确认会覆盖当前本地全部数据。`, "warning");
        return;
    }
    if (state.selectedBackupFile) {
        updateBackupFeedback("已准备恢复备份", `当前文件：${state.selectedBackupFile.name}。确认后将覆盖本地全部数据。`, "warning");
        return;
    }
    updateBackupFeedback("高风险操作提示", "导出可直接下载当前备份，恢复会覆盖本地全部数据，执行前请先确认文件来源。", "warning");
}

/**
 * 备份反馈单独封装，是为了让维护工作区的危险状态和成功结果继续共用同一反馈入口。
 */
function updateBackupFeedback(title, description, variant = "warning") {
    updateWorkspaceFeedback(elements.backupFeedback, title, description, variant);
}

/**
 * 搜索条件文案集中拼装，是为了让搜索反馈和结果面板始终对同一组筛选条件给出一致表达。
 */
function buildSearchCriteriaLabel(keyword, tag) {
    const parts = [];
    if (keyword) {
        parts.push(`关键词“${keyword}”`);
    }
    if (tag) {
        parts.push(`标签“${tag}”`);
    }
    return parts.join("，") || "无筛选条件";
}
