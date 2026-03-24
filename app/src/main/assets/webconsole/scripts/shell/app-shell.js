import {
    elements,
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
    if (reason) {
        showLoginError(reason);
    }
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
