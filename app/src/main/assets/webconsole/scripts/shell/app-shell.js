import {
    elements,
    escapeHtml,
    setShellRefreshHandler,
    setUnauthorizedHandler,
    showLoginError,
    state,
} from "../shared/core.js";
import { bindContentFormEvents, loadDecks, updateCommandAvailability } from "../features/content.js";
import {
    bindMaintenanceEvents,
    bindSearchEvents,
    clearRestoreSelection,
    loadAnalytics,
    loadDashboard,
    loadSettings,
    setMaintenanceCallbacks,
    updateRestoreControls,
} from "../features/operations.js";
import {
    bindStudySessionEvents,
    loadStudySession,
    loadStudyWorkspace,
    renderPracticeSelection,
    renderStudySession,
    startPracticeSession,
} from "../features/study.js";

const SECTION_TITLES = {
    study: "学习工作区",
    overview: "概览",
    content: "内容管理",
    search: "搜索",
    analytics: "统计",
    settings: "设置",
    backup: "备份",
};

/**
 * 壳层初始化集中在单点，是为了让登录、导航、工作区刷新和全局反馈围绕同一入口启动。
 */
export function initializeAppShell() {
    setUnauthorizedHandler(logout);
    setShellRefreshHandler(renderShellChrome);
    setMaintenanceCallbacks({
        onRestoreCompleted: async () => {
            await refreshAll();
            switchSection("overview");
        },
    });
    bindGlobalEvents();
    bindContentFormEvents();
    bindSearchEvents();
    bindStudySessionEvents();
    bindMaintenanceEvents();
    bootstrap();
    updateCommandAvailability();
    renderPracticeSelection();
    renderStudySession();
    clearRestoreSelection();
    updateRestoreControls();
    renderShellChrome();
}

/**
 * 登录成功后的壳层装配统一处理，是为了让页面刷新和首次进入后台走同一条会话恢复路径。
 */
async function bootstrap() {
    const session = await getSession();
    if (!session) {
        elements.loginView.hidden = false;
        elements.appView.hidden = true;
        return;
    }
    elements.loginView.hidden = true;
    elements.appView.hidden = false;
    elements.sessionSummary.textContent = `${session.displayName} · 端口 ${session.port} · 在线会话 ${session.activeSessionCount}`;
    await refreshAll();
    renderShellChrome();
}

/**
 * 当前登录态读取单独封装，是为了让壳层只通过一处判断浏览器是否仍在有效会话内。
 */
async function getSession() {
    const response = await fetch("/api/web-console/v1/session", { credentials: "include" });
    if (!response.ok) {
        return null;
    }
    return response.json();
}

/**
 * 全量刷新集中处理，是为了让壳层在任何工作区变动后都能快速回到一致状态。
 */
export async function refreshAll() {
    await Promise.all([
        loadStudyWorkspace(),
        loadStudySession(),
        loadDashboard(),
        loadDecks(),
        loadAnalytics(),
        loadSettings(),
    ]);
}

/**
 * 退出登录集中处理，是为了让未授权回调和显式退出都能走同一条壳层清理路径。
 */
export async function logout(reason) {
    await fetch("/api/web-console/v1/auth/logout", { method: "POST", credentials: "include" });
    elements.loginView.hidden = false;
    elements.appView.hidden = true;
    state.studyWorkspace = null;
    state.studySession = null;
    state.studyReturnContext = null;
    if (reason) {
        showLoginError(reason);
    }
    renderShellChrome();
}

/**
 * 工作区切换集中处理，是为了让侧栏导航和当前工作区标题始终围绕同一状态变化。
 */
export function switchSection(section) {
    state.currentSection = section;
    elements.navButtons.forEach((button) => {
        button.classList.toggle("is-active", button.dataset.section === section);
    });
    elements.sectionNodes.forEach((node) => {
        node.classList.toggle("is-active", node.id === `section-${section}`);
    });
    elements.sectionTitle.textContent = SECTION_TITLES[section];
    renderShellChrome();
}

/**
 * 全局事件绑定集中处理，是为了让模块拆分后仍保留同一套导航与登录入口行为。
 */
function bindGlobalEvents() {
    elements.navButtons.forEach((button) => {
        button.addEventListener("click", () => switchSection(button.dataset.section));
    });
    document.querySelector("#logout-button").addEventListener("click", () => logout());
    document.querySelector("#refresh-button").addEventListener("click", refreshAll);
    elements.loginForm.addEventListener("submit", submitLoginForm);
    window.addEventListener("yike:launch-practice", handlePracticeLaunch);
    window.addEventListener("yike:study-ended", handleStudyEnded);
}

/**
 * 登录表单提交集中处理，是为了让访问码校验和首次进入后台的反馈始终走同一入口。
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
    await bootstrap();
}

/**
 * 壳层上下文和全局状态集中渲染，是为了让不同工作区在同一后台骨架下保持一致的导航与反馈语义。
 */
function renderShellChrome() {
    renderShellStatus();
    renderContextStrip();
}

/**
 * 顶部状态芯片统一承载登录、学习和恢复信息，是为了让全局级状态不会被局部工作区淹没。
 */
function renderShellStatus() {
    const chips = [
        `<span class="status-chip">当前工作区：${escapeHtml(SECTION_TITLES[state.currentSection])}</span>`,
    ];
    if (state.studySession) {
        chips.push(`<span class="status-chip">学习会话：${escapeHtml(state.studySession.title)}</span>`);
    } else {
        chips.push(`<span class="status-chip">学习会话：空闲</span>`);
    }
    if (state.selectedBackupFile) {
        chips.push(`<span class="status-chip is-warn">待恢复文件：${escapeHtml(state.selectedBackupFile.name)}</span>`);
    }
    elements.shellStatus.innerHTML = chips.join("");
}

/**
 * 上下文栏集中表达当前工作区的最近有效上下文，是为了让内容、学习和维护工作流都不再只靠标题提示。
 */
function renderContextStrip() {
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
 * 上下文模型集中推导，是为了让每个工作区都能在同一壳层下拥有明确的标题、元信息和下一步动作。
 */
function resolveContextPayload() {
    const deck = state.decks.find((item) => item.id === state.selectedDeckId);
    const card = state.cards.find((item) => item.id === state.selectedCardId);
    const question = state.questions.find((item) => item.id === state.selectedQuestionId);
    const base = {
        title: SECTION_TITLES[state.currentSection],
        description: "浏览器端会持续保留最近一次有效工作区上下文。",
        meta: [],
        actions: [],
    };

    if (state.currentSection === "content") {
        base.title = "内容 drill-down 工作台";
        base.description = "围绕当前卡组和卡片上下文整理内容，并为后续学习入口保留返回路径。";
        if (deck) base.meta.push({ label: `卡组：${deck.name}` });
        if (card) base.meta.push({ label: `卡片：${card.title}` });
        if (question) base.meta.push({ label: `问题：${question.prompt}` });
        if (!deck) base.meta.push({ label: "尚未选择卡组", warn: true });
        if (deck) {
            base.actions.push({ id: "jump-study", label: "去学习工作区", primary: true });
        }
        return base;
    }

    if (state.currentSection === "study") {
        base.title = state.studySession ? "桌面学习工作区" : "学习工作区入口";
        base.description = state.studySession
            ? "当前学习会话会在同一有效登录内保持恢复，并在离开后提供明确返回路径。"
            : "从这里可以恢复当前学习会话，或基于内容上下文继续发起复习与练习。";
        if (state.studySession) {
            base.meta.push({ label: state.studySession.title });
            if (state.studyReturnContext?.label) {
                base.meta.push({ label: `返回：${state.studyReturnContext.label}` });
                base.actions.push({ id: "return-origin", label: "返回来源工作区" });
            }
        } else {
            base.meta.push({ label: "当前无活动学习会话" });
        }
        return base;
    }

    if (state.currentSection === "search") {
        base.title = "搜索与上下文入口";
        base.description = "搜索结果可以直接回到原对象上下文，并衔接后续学习动作。";
        base.meta.push({ label: `结果数：${state.lastSearchResults.length}` });
        return base;
    }

    if (state.currentSection === "backup") {
        base.title = "备份与恢复工作区";
        base.description = "高风险操作会在壳层和当前工作区同时给出分层反馈。";
        if (state.selectedBackupFile) {
            base.meta.push({ label: `待恢复：${state.selectedBackupFile.name}`, warn: true });
        }
        return base;
    }

    return base;
}

/**
 * 上下文动作统一在壳层处理，是为了让跨工作区跳转和返回路径保持一致。
 */
function handleContextAction(actionId) {
    if (actionId === "jump-study") {
        state.studyReturnContext = {
            section: "content",
            label: "内容工作台",
        };
        switchSection("study");
        return;
    }
    if (actionId === "return-origin" && state.studyReturnContext?.section) {
        const targetSection = state.studyReturnContext.section;
        state.studyReturnContext = null;
        switchSection(targetSection);
    }
}

/**
 * 跨工作区发起练习统一在壳层承接，是为了把返回路径和工作区切换保持在同一层协调。
 */
async function handlePracticeLaunch(event) {
    state.studyReturnContext = {
        section: event.detail?.returnSection || "study",
        label: event.detail?.label || "学习工作区",
    };
    switchSection("study");
    renderPracticeSelection();
    await startPracticeSession();
    renderShellChrome();
}

/**
 * 学习结束后的返回统一在壳层处理，是为了让来源工作区恢复逻辑不散落进具体学习模块。
 */
function handleStudyEnded() {
    if (!state.studyReturnContext?.section) {
        return;
    }
    const targetSection = state.studyReturnContext.section;
    state.studyReturnContext = null;
    switchSection(targetSection);
}
