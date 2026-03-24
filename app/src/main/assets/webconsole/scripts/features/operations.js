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
    renderEmptyState,
    resolveDownloadFileName,
    showMessage,
    state,
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
        return;
    }
    const payload = await postJson("/api/web-console/v1/search", {
        keyword,
        tag: tag || null,
    });
    if (!payload) return;
    document.querySelector("#search-results").innerHTML = payload.length
        ? payload.map((item) => `
            <div class="item">
                <div class="item-head"><strong>${escapeHtml(item.prompt)}</strong><span class="muted">${escapeHtml(item.deckName)} / ${escapeHtml(item.cardTitle)}</span></div>
                <div>${escapeHtml(item.answer)}</div>
                <div class="muted">阶段 ${item.stageIndex} · 复习 ${item.reviewCount} 次 · lapse ${item.lapseCount} 次</div>
            </div>
        `).join("")
        : renderEmptyState("没有找到结果", "换一个关键词、标签，或先确认目标问题是否已经同步到当前设备。");
}

/**
 * 统计读取集中处理，是为了让富后台壳层后续升级时仍只依赖一份稳定指标结构。
 */
export async function loadAnalytics() {
    const payload = await fetchJson("/api/web-console/v1/analytics");
    if (!payload) return;
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
}

/**
 * 设置读取集中处理，是为了让提醒和主题配置继续围绕同一维护工作区更新。
 */
export async function loadSettings() {
    const payload = await fetchJson("/api/web-console/v1/settings");
    if (!payload) return;
    elements.settingsForm.elements.namedItem("dailyReminderEnabled").checked = payload.dailyReminderEnabled;
    elements.settingsForm.elements.namedItem("dailyReminderHour").value = payload.dailyReminderHour;
    elements.settingsForm.elements.namedItem("dailyReminderMinute").value = payload.dailyReminderMinute;
    elements.settingsForm.elements.namedItem("themeMode").value = payload.themeMode;
    document.querySelector("#settings-backup-time").textContent = payload.backupLastAt
        ? `最近一次备份：${formatDateTime(payload.backupLastAt)}`
        : "当前还没有可记录的备份时间。";
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
        return;
    }
    if (!Number.isInteger(dailyReminderMinute) || dailyReminderMinute < 0 || dailyReminderMinute > 59) {
        showMessage("提醒分钟需在 0 到 59 之间。", true);
        return;
    }
    const response = await postJson("/api/web-console/v1/settings/update", {
        dailyReminderEnabled: getFieldChecked(elements.settingsForm, "dailyReminderEnabled"),
        dailyReminderHour,
        dailyReminderMinute,
        themeMode: getFieldValue(elements.settingsForm, "themeMode"),
    });
    if (!response) return;
    showMessage(response.message);
    await loadSettings();
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
    const response = await fetch("/api/web-console/v1/backup/export", { credentials: "include" });
    state.isExporting = false;
    updateRestoreControls();
    if (!response.ok) {
        const errorText = await response.text();
        showMessage(errorText || "导出备份失败。", true);
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
        return;
    }
    if (!elements.restoreBackupConfirmInput.checked) {
        showMessage("请先确认恢复会覆盖当前本地全部数据。", true);
        return;
    }
    if (!window.confirm(`确认从 ${state.selectedBackupFile.name} 恢复？当前本地数据将被覆盖且无法撤销。`)) {
        return;
    }
    state.isRestoring = true;
    updateRestoreControls();
    const content = await state.selectedBackupFile.text();
    const response = await postJson("/api/web-console/v1/backup/restore", {
        fileName: state.selectedBackupFile.name,
        content,
    });
    state.isRestoring = false;
    updateRestoreControls();
    if (!response) return;
    showMessage(response.message);
    clearRestoreSelection();
    if (restoreCompletedHandler) {
        await restoreCompletedHandler();
    }
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
}
