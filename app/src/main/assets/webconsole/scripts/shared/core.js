/**
 * 共享状态放在单例里，是为了让壳层、工作区和反馈模块围绕同一份浏览器上下文协作。
 */
export const state = {
    currentSection: "study",
    selectedDeckId: null,
    selectedCardId: null,
    selectedQuestionId: null,
    lastSearchResults: [],
    decks: [],
    cards: [],
    questions: [],
    selectedBackupFile: null,
    isExporting: false,
    isRestoring: false,
    studyWorkspace: null,
    studySession: null,
    studyReturnContext: null,
    practiceSelection: {
        selectedDeckIds: new Set(),
        selectedCardIds: new Set(),
        selectedQuestionIds: new Set(),
        cardsByDeckId: new Map(),
        questionsByCardId: new Map(),
        orderMode: "sequential",
        isLoadingCards: false,
        isLoadingQuestions: false,
    },
};

/**
 * 共享 DOM 引用集中缓存，是为了让模块拆分后仍只在一处声明页面结构契约。
 */
export const elements = {
    loginView: document.querySelector("#login-view"),
    appView: document.querySelector("#app-view"),
    loginForm: document.querySelector("#login-form"),
    loginError: document.querySelector("#login-error"),
    sectionTitle: document.querySelector("#section-title"),
    sessionSummary: document.querySelector("#session-summary"),
    globalMessage: document.querySelector("#global-message"),
    shellStatus: document.querySelector("#shell-status"),
    contextTitle: document.querySelector("#context-title"),
    contextDescription: document.querySelector("#context-description"),
    contextMeta: document.querySelector("#context-meta"),
    contextActions: document.querySelector("#context-actions"),
    deckForm: document.querySelector("#deck-form"),
    cardForm: document.querySelector("#card-form"),
    questionForm: document.querySelector("#question-form"),
    settingsForm: document.querySelector("#settings-form"),
    restoreBackupFileInput: document.querySelector("#restore-backup-file"),
    restoreBackupConfirmInput: document.querySelector("#restore-backup-confirm"),
    restoreBackupFileMeta: document.querySelector("#restore-backup-file-meta"),
    restoreBackupButton: document.querySelector("#restore-backup-button"),
    clearRestoreFileButton: document.querySelector("#clear-restore-file-button"),
    exportBackupButton: document.querySelector("#export-backup-button"),
    searchFeedback: document.querySelector("#search-feedback"),
    analyticsFeedback: document.querySelector("#analytics-feedback"),
    settingsFeedback: document.querySelector("#settings-feedback"),
    backupFeedback: document.querySelector("#backup-feedback"),
    contentSelectionSummary: document.querySelector("#content-selection-summary"),
    contentDeckDetails: document.querySelector("#content-deck-details"),
    contentCardDetails: document.querySelector("#content-card-details"),
    contentQuestionDetails: document.querySelector("#content-question-details"),
    newCardButton: document.querySelector("#new-card-button"),
    newQuestionButton: document.querySelector("#new-question-button"),
    studySessionCard: document.querySelector("#study-session-card"),
    studySessionTitle: document.querySelector("#study-session-title"),
    studySessionSubtitle: document.querySelector("#study-session-subtitle"),
    studySessionContent: document.querySelector("#study-session-content"),
    startReviewButton: document.querySelector("#start-review-button"),
    startPracticeButton: document.querySelector("#start-practice-button"),
    endStudySessionButton: document.querySelector("#end-study-session-button"),
    practiceOrderModeSelect: document.querySelector("#practice-order-mode"),
    navButtons: [...document.querySelectorAll(".nav-button")],
    sectionNodes: [...document.querySelectorAll(".section")],
};

let unauthorizedHandler = null;
let shellRefreshHandler = null;

/**
 * 未授权回调可配置，是为了让共享请求工具无需直接依赖具体壳层实现。
 */
export function setUnauthorizedHandler(handler) {
    unauthorizedHandler = handler;
}

/**
 * 壳层刷新回调可配置，是为了让各工作区在状态变化后通知统一上下文栏重绘。
 */
export function setShellRefreshHandler(handler) {
    shellRefreshHandler = handler;
}

/**
 * 各模块通过统一入口请求壳层刷新，是为了避免 feature 直接依赖 shell 实现。
 */
export function requestShellRefresh() {
    shellRefreshHandler?.();
}

/**
 * 登录错误单独显示在登录卡片里，是为了让访问码问题和工作区内反馈保持不同层级。
 */
export function showLoginError(message) {
    elements.loginError.hidden = false;
    elements.loginError.textContent = message;
}

/**
 * 全局消息统一放在壳层顶部，是为了让不同工作区切换后仍能感知最近一次关键反馈。
 */
export function showMessage(message, isError = false) {
    elements.globalMessage.hidden = false;
    elements.globalMessage.textContent = message;
    elements.globalMessage.style.background = isError ? "rgba(180, 67, 53, 0.12)" : "rgba(11, 111, 105, 0.12)";
    elements.globalMessage.style.color = isError ? "#b44335" : "#0b6f69";
    window.clearTimeout(showMessage.timer);
    showMessage.timer = window.setTimeout(() => {
        elements.globalMessage.hidden = true;
    }, 3200);
}

/**
 * GET 请求帮助方法统一兜底未登录和错误反馈，是为了避免各工作区重复维护相同的协议分支。
 */
export async function fetchJson(url) {
    const response = await fetch(url, { credentials: "include" });
    if (!response.ok) {
        if (response.status === 401 && unauthorizedHandler) {
            await unauthorizedHandler("登录已失效，请重新输入手机上最新的访问码。");
            return null;
        }
        const errorText = await response.text();
        showMessage(errorText || "请求失败，请稍后重试。", true);
        return null;
    }
    return response.json();
}

/**
 * 可空 GET 请求保留 204 分支，是为了让“当前没有活动会话”这类状态不被误判成接口失败。
 */
export async function fetchOptionalJson(url) {
    const response = await fetch(url, { credentials: "include" });
    if (response.status === 204) {
        return null;
    }
    if (!response.ok) {
        if (response.status === 401 && unauthorizedHandler) {
            await unauthorizedHandler("登录已失效，请重新输入手机上最新的访问码。");
            return undefined;
        }
        const errorText = await response.text();
        showMessage(errorText || "请求失败，请稍后重试。", true);
        return undefined;
    }
    return response.json();
}

/**
 * POST 请求帮助方法统一处理错误和未登录，是为了让工作区动作可以只关心业务成功路径。
 */
export async function postJson(url, payload) {
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(payload ?? {}),
    });
    if (!response.ok) {
        if (response.status === 401 && unauthorizedHandler) {
            await unauthorizedHandler("登录已失效，请重新输入手机上最新的访问码。");
            return null;
        }
        const errorText = await response.text();
        showMessage(errorText || "操作失败。", true);
        return null;
    }
    if (response.status === 204) {
        return {};
    }
    return response.json();
}

/**
 * 指标卡 HTML 集中拼装，是为了让不同工作区继续共享相同的统计视觉语义。
 */
export function metricCard(label, value) {
    return `<article class="metric"><span class="muted">${label}</span><strong>${value}</strong></article>`;
}

/**
 * 空状态模板集中维护，是为了让富后台后续统一空态层级时不必逐个工作区回收文案结构。
 */
export function renderEmptyState(title, description) {
    return `
        <div class="empty-state-block">
            <strong>${escapeHtml(title)}</strong>
            <span class="muted">${escapeHtml(description)}</span>
        </div>
    `;
}

/**
 * 工作区反馈条统一生成，是为了让搜索、统计、设置和备份在同一壳层下共享一致的状态层级。
 */
export function updateWorkspaceFeedback(element, title, description, variant = "empty") {
    if (!element) {
        return;
    }
    element.className = `workspace-feedback is-${variant}`;
    element.innerHTML = `
        <strong>${escapeHtml(title)}</strong>
        <span class="muted">${escapeHtml(description)}</span>
    `;
}

/**
 * 表单字段读取集中封装，是为了让模块拆分后仍保持同一套字段校验入口。
 */
export function getFieldValue(form, name) {
    return getFormField(form, name).value;
}

/**
 * 可空字段读取保留空值归一化，是为了让请求体协议不夹带无意义空字符串。
 */
export function getOptionalFieldValue(form, name) {
    const value = getFieldValue(form, name).trim();
    return value || null;
}

/**
 * 复选框读取统一转布尔值，是为了让设置和危险确认请求保持稳定类型。
 */
export function getFieldChecked(form, name) {
    return Boolean(getFormField(form, name).checked);
}

/**
 * 表单回填集中处理，是为了让对象编辑和新建态共享同一套字段同步入口。
 */
export function setFieldValue(form, name, value) {
    getFormField(form, name).value = value;
}

/**
 * 复选框回填集中处理，是为了让设置页读取和恢复动作按钮状态保持一致。
 */
export function setFieldChecked(form, name, checked) {
    getFormField(form, name).checked = checked;
}

/**
 * 表单字段解析在单点兜底，是为了让模块拆分后页面结构缺失能尽早暴露为显式错误。
 */
export function getFormField(form, name) {
    const field = form.elements.namedItem(name);
    if (!field) {
        throw new Error(`表单字段不存在：${name}`);
    }
    return field;
}

/**
 * 标签分割保留去重前的清洗逻辑，是为了让内容工作区与服务端对标签输入格式理解一致。
 */
export function splitTags(value) {
    return value.split(",").map((item) => item.trim()).filter(Boolean);
}

/**
 * 下载文件名从响应头解析，是为了保留服务端建议名称而不是退回浏览器临时名。
 */
export function resolveDownloadFileName(contentDisposition) {
    const matchedFileName = contentDisposition?.match(/filename="([^"]+)"/i)?.[1];
    return matchedFileName || `yike-backup-${Date.now()}.json`;
}

/**
 * 危险确认继续统一走浏览器 confirm，是为了在工作区拆分后仍维持同一风险提示基线。
 */
export function confirmDanger(message) {
    return window.confirm(message);
}

/**
 * 时间格式化集中在共享层，是为了让设置和备份等工作区使用同一中文时间表达。
 */
export function formatDateTime(epochMillis) {
    return new Date(epochMillis).toLocaleString("zh-CN");
}

/**
 * 文件体积格式化集中处理，是为了让恢复流程对上传文件元信息保持一致表达。
 */
export function formatFileSize(size) {
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

/**
 * HTML 转义集中处理，是为了让所有工作区在渲染用户内容时共享同一安全边界。
 */
export function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}
