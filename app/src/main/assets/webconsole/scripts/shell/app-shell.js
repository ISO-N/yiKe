import {
    elements,
    escapeHtml,
    setCurrentSection,
    setShellRefreshHandler,
    setUnauthorizedHandler,
    showLoginError,
    state,
    syncElements,
} from "../shared/core.js";
import {
    bindContentFormEvents,
    loadDecks,
    updateCommandAvailability,
} from "../features/content.js";
import {
    bindMaintenanceEvents,
    bindSearchEvents,
    clearRestoreSelection,
    loadAnalytics,
    loadDashboard,
    loadSettings,
    setMaintenanceCallbacks,
    submitSearchForm,
    updateRestoreControls,
} from "../features/operations.js";
import {
    bindStudySessionEvents,
    loadPracticeDecks,
    loadStudySession,
    loadStudyWorkspace,
    renderPracticeSelection,
    renderStudySession,
} from "../features/study.js";
import {
    appPagePaths,
    consumeReturnContext,
    getQueryParam,
    normalizeInternalPath,
    readReturnContext,
    redirectToLogin,
    resolveCurrentAppPage,
    resolveNextPagePath,
} from "../shared/navigation.js";
import { renderPageTemplate } from "./page-templates.js";

const SECTION_TITLES = {
    overview: "概览",
    study: "学习工作区",
    content: "内容管理",
    search: "搜索",
    analytics: "统计",
    settings: "设置",
    backup: "备份",
};

/**
 * 多页面壳层初始化集中在这里，是为了让登录、登出、入口分发和应用页都围绕同一入口收口。
 */
export function initializeAppShell() {
    void bootstrapByPage();
}

async function bootstrapByPage() {
    syncElements();
    const pageType = document.body.dataset.page;
    if (pageType === "index") {
        await bootstrapEntryPage();
        return;
    }
    if (pageType === "login") {
        await bootstrapLoginPage();
        return;
    }
    if (pageType === "logout") {
        await bootstrapLogoutPage();
        return;
    }
    await bootstrapAppPage();
}

/**
 * 根入口单独分发，是为了让用户只输入 IP:端口 时也能自动落到正确的正式页面。
 */
async function bootstrapEntryPage() {
    const session = await getSession();
    if (session) {
        window.location.replace(appPagePaths.study);
        return;
    }
    window.location.replace("/login");
}

/**
 * 登录页在进入时优先判断已有会话，是为了避免用户明明已经登录却被迫再次输入访问码。
 */
async function bootstrapLoginPage() {
    const session = await getSession();
    if (session) {
        window.location.replace(resolveNextPagePath());
        return;
    }
    bindLoginPageEvents();
    renderLoginStateFromQuery();
}

/**
 * 退出页单独处理会话清理，是为了让壳层退出动作拥有稳定的地址和回跳目标。
 */
async function bootstrapLogoutPage() {
    elements.logoutMessage.textContent = "系统正在清理当前浏览器会话…";
    await fetch("/api/web-console/v1/auth/logout", {
        method: "POST",
        credentials: "include",
    });
    window.sessionStorage.removeItem("yike:web:return-context");
    window.location.replace("/login?logged_out=1");
}

/**
 * 应用页入口统一校验会话并渲染模板，是为了让所有工作区在同一玻璃壳层下共享行为基线。
 */
async function bootstrapAppPage() {
    const currentPage = resolveCurrentAppPage() ?? "study";
    setCurrentSection(currentPage);
    setUnauthorizedHandler(() => redirectToLogin({ expired: true }));
    setShellRefreshHandler(renderShellChrome);
    setMaintenanceCallbacks({
        onRestoreCompleted: async () => {
            await loadCurrentPage();
            renderShellChrome();
        },
    });
    renderCurrentPage(currentPage);
    bindGlobalEvents();
    bindCurrentPageEvents(currentPage);
    const session = await getSession();
    if (!session) {
        redirectToLogin();
        return;
    }
    state.session = session;
    elements.sessionSummary.textContent = `${session.displayName} · 端口 ${session.port} · 在线会话 ${session.activeSessionCount}`;
    await loadCurrentPage();
    renderShellChrome();
}

/**
 * 当前工作区模板切换集中在这里，是为了让页面 URL 和实际 DOM 结构始终保持一致。
 */
function renderCurrentPage(page) {
    elements.pageRoot.innerHTML = renderPageTemplate(page);
    syncElements();
    markActiveNav(page);
    setMobileNavOpen(false);
    updateCommandAvailability();
}

/**
 * 应用页通用事件统一绑定，是为了让导航、刷新和移动端折叠菜单不散落到各工作区实现里。
 */
function bindGlobalEvents() {
    elements.refreshButton?.addEventListener("click", () => {
        void loadCurrentPage();
    });
    elements.navMenuToggle?.addEventListener("click", () => {
        const nextOpen = !elements.primaryNav.classList.contains("is-open");
        setMobileNavOpen(nextOpen);
    });
    elements.navButtons.forEach((button) => {
        button.addEventListener("click", () => setMobileNavOpen(false));
    });
}

/**
 * 页面级事件绑定按工作区分发，是为了让各模块只在自己真正存在的 DOM 上注册行为。
 */
function bindCurrentPageEvents(page) {
    if (page === "study") {
        bindStudySessionEvents();
        renderPracticeSelection();
        renderStudySession();
        return;
    }
    if (page === "content") {
        bindContentFormEvents();
        return;
    }
    if (page === "search") {
        bindSearchEvents();
        return;
    }
    if (page === "settings" || page === "backup") {
        bindMaintenanceEvents();
        if (page === "backup") {
            clearRestoreSelection();
            updateRestoreControls();
        }
    }
}

/**
 * 当前工作区数据读取集中在这里，是为了让刷新按钮和首次进入页面走同一条载入路径。
 */
async function loadCurrentPage() {
    const currentPage = state.currentSection;
    if (currentPage === "overview") {
        await loadDashboard();
        renderShellChrome();
        return;
    }
    if (currentPage === "study") {
        await Promise.all([
            loadPracticeDecks(),
            loadStudyWorkspace(),
            loadStudySession(),
        ]);
        renderShellChrome();
        return;
    }
    if (currentPage === "content") {
        restoreContentContextFromQuery();
        await loadDecks();
        renderShellChrome();
        return;
    }
    if (currentPage === "search") {
        restoreSearchFormFromQuery();
        renderShellChrome();
        if (getQueryParam("keyword") || getQueryParam("tag")) {
            await submitSearchForm({
                preventDefault() {},
                currentTarget: elements.pageRoot.querySelector("#search-form"),
            });
            return;
        }
        return;
    }
    if (currentPage === "analytics") {
        await loadAnalytics();
        renderShellChrome();
        return;
    }
    if (currentPage === "settings") {
        await loadSettings();
        renderShellChrome();
        return;
    }
    if (currentPage === "backup") {
        renderShellChrome();
    }
}

/**
 * 当前登录态读取单独封装，是为了让登录页、入口页和应用页都通过同一处判断会话是否仍有效。
 */
async function getSession() {
    const response = await fetch("/api/web-console/v1/session", { credentials: "include" });
    if (!response.ok) {
        return null;
    }
    return response.json();
}

/**
 * 登录页事件集中绑定，是为了让访问码提交和错误反馈始终走同一入口。
 */
function bindLoginPageEvents() {
    elements.loginForm.addEventListener("submit", submitLoginForm);
}

/**
 * 登录表单提交集中处理，是为了让访问码校验和登录后回跳始终遵循同一条规则。
 */
async function submitLoginForm(event) {
    event.preventDefault();
    elements.loginError.hidden = true;
    const code = document.querySelector("#access-code").value.trim();
    if (!/^\d{6}$/.test(code)) {
        showLoginError("请输入手机页面展示的 6 位数字访问码。");
        return;
    }
    const response = await fetch("/api/web-console/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ code }),
    });
    if (!response.ok) {
        showLoginError("访问码不正确，或服务未处于可登录状态。");
        return;
    }
    document.querySelector("#access-code").value = "";
    window.location.replace(resolveNextPagePath());
}

/**
 * 登录页提示信息从查询参数恢复，是为了让退出登录和会话过期都能给出明确反馈。
 */
function renderLoginStateFromQuery() {
    if (getQueryParam("expired") === "1") {
        showLoginError("登录已失效，请重新输入手机上最新的访问码。");
        return;
    }
    if (getQueryParam("logged_out") === "1") {
        showLoginError("当前浏览器会话已退出，请重新输入访问码。");
    }
}

/**
 * 导航活跃态集中处理，是为了让正式多页面在视觉上仍保留清晰的当前工作区反馈。
 */
function markActiveNav(page) {
    elements.navButtons.forEach((button) => {
        button.classList.toggle("is-active", button.dataset.pageLink === page);
    });
}

/**
 * 顶部玻璃壳层统一渲染，是为了让状态芯片和上下文条在多页面中仍保持稳定表达。
 */
function renderShellChrome() {
    renderShellStatus();
    renderContextStrip();
}

/**
 * 顶部状态芯片统一承载当前页面和来源信息，是为了让切换页面后仍能保留全局状态提示。
 */
function renderShellStatus() {
    if (!elements.shellStatus) {
        return;
    }
    const chips = [
        `<span class="status-chip">当前页面：${escapeHtml(SECTION_TITLES[state.currentSection])}</span>`,
    ];
    if (state.studySession) {
        chips.push(`<span class="status-chip">学习会话：${escapeHtml(state.studySession.title)}</span>`);
    } else {
        chips.push("<span class=\"status-chip\">学习会话：空闲</span>");
    }
    const returnContext = readReturnContext();
    if (returnContext?.label && state.currentSection === "study") {
        chips.push(`<span class="status-chip">来源：${escapeHtml(returnContext.label)}</span>`);
    }
    elements.shellStatus.innerHTML = chips.join("");
}

/**
 * 上下文栏集中表达当前工作区上下文，是为了让 URL 恢复和跨页返回拥有可见的二级说明区。
 */
function renderContextStrip() {
    if (!elements.contextTitle) {
        return;
    }
    const context = resolveContextPayload();
    elements.contextTitle.textContent = context.title;
    elements.contextDescription.textContent = context.description;
    elements.contextMeta.innerHTML = context.meta.map((item) => `<span class="context-chip${item.warn ? " is-warn" : ""}">${escapeHtml(item.label)}</span>`).join("");
    elements.contextActions.innerHTML = context.actions.map((action) => `
        <button
            type="button"
            class="${action.primary ? "primary-button" : "ghost-button"}"
            data-context-action="${action.id}"
        >${escapeHtml(action.label)}</button>
    `).join("");
    elements.contextActions.querySelectorAll("[data-context-action]").forEach((button) => {
        button.addEventListener("click", () => handleContextAction(button.dataset.contextAction));
    });
}

/**
 * 上下文模型集中推导，是为了让每个页面都能围绕 URL 与共享状态给出明确的当前工作语义。
 */
function resolveContextPayload() {
    const deck = state.decks.find((item) => item.id === state.selectedDeckId);
    const card = state.cards.find((item) => item.id === state.selectedCardId);
    const question = state.questions.find((item) => item.id === state.selectedQuestionId);
    const returnContext = readReturnContext();
    const base = {
        title: SECTION_TITLES[state.currentSection],
        description: "浏览器端会持续保留最近一次有效工作区上下文。",
        meta: [],
        actions: [],
    };

    if (state.currentSection === "content") {
        base.title = "内容 drill-down 工作台";
        base.description = "卡组、卡片和问题选择会写入地址栏，刷新后仍能恢复到同一路径。";
        if (deck) base.meta.push({ label: `卡组：${deck.name}` });
        if (card) base.meta.push({ label: `卡片：${card.title}` });
        if (question) base.meta.push({ label: `问题：${question.prompt}` });
        if (!deck) base.meta.push({ label: "尚未选择卡组", warn: true });
        return base;
    }

    if (state.currentSection === "study") {
        base.title = state.studySession ? "桌面学习工作区" : "学习工作区入口";
        base.description = state.studySession
            ? "当前学习会话保存在服务端内存中，刷新当前页仍会恢复到最近一次有效题位。"
            : "从这里可以恢复当前学习会话，或按卡组、卡片、题目范围发起自由练习。";
        if (returnContext?.label) {
            base.meta.push({ label: `返回：${returnContext.label}` });
            base.actions.push({ id: "return-origin", label: "返回来源页" });
        }
        if (state.studySession) {
            base.meta.push({ label: state.studySession.title });
        }
        return base;
    }

    if (state.currentSection === "search") {
        const keyword = getQueryParam("keyword");
        const tag = getQueryParam("tag");
        base.title = "搜索与上下文入口";
        base.description = "搜索条件会保留在地址栏，方便刷新、回退和再次进入相同结果页。";
        if (keyword) base.meta.push({ label: `关键词：${keyword}` });
        if (tag) base.meta.push({ label: `标签：${tag}` });
        base.meta.push({ label: `结果数：${state.lastSearchResults.length}` });
        return base;
    }

    if (state.currentSection === "backup") {
        base.title = "备份与恢复工作区";
        base.description = "导出与恢复属于高风险维护动作，会在顶部反馈条与当前卡片内双重提示。";
        if (state.selectedBackupFile) {
            base.meta.push({ label: `待恢复：${state.selectedBackupFile.name}`, warn: true });
        }
        return base;
    }

    return base;
}

/**
 * 上下文动作统一在壳层处理，是为了让返回来源页这类跨工作区动作保持稳定入口。
 */
function handleContextAction(actionId) {
    if (actionId === "return-origin") {
        const returnContext = consumeReturnContext();
        window.location.assign(normalizeInternalPath(returnContext?.path));
    }
}

/**
 * 内容工作区从地址栏恢复上下文，是为了让刷新、前进后退后仍能回到之前的下钻位置。
 */
function restoreContentContextFromQuery() {
    state.selectedDeckId = getQueryParam("deckId");
    state.selectedCardId = getQueryParam("cardId");
    state.selectedQuestionId = getQueryParam("questionId");
}

/**
 * 搜索页表单从地址栏恢复查询条件，是为了让同一搜索视图具备真正页面级可恢复性。
 */
function restoreSearchFormFromQuery() {
    const form = elements.pageRoot.querySelector("#search-form");
    if (!form) {
        return;
    }
    form.elements.namedItem("keyword").value = getQueryParam("keyword") ?? "";
    form.elements.namedItem("tag").value = getQueryParam("tag") ?? "";
}

/**
 * 移动端导航折叠开关集中处理，是为了让顶部玻璃导航在小屏下仍可操作且不遮挡主内容。
 */
function setMobileNavOpen(open) {
    if (!elements.primaryNav || !elements.navMenuToggle) {
        return;
    }
    elements.primaryNav.classList.toggle("is-open", open);
    elements.navMenuToggle.setAttribute("aria-expanded", String(open));
}
