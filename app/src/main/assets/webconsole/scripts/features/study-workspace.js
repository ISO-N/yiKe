import {
    confirmDanger,
    escapeHtml,
    fetchJson,
    metricCard,
    postJson,
    requestShellRefresh,
    renderEmptyState,
    state,
} from "../shared/core.js";

let studySessionRenderer = null;

/**
 * 学习工作区回调可配置，是为了让概览模块不直接绑定具体的会话渲染实现。
 */
export function setStudyWorkspaceCallbacks(callbacks) {
    studySessionRenderer = callbacks?.renderStudySession ?? null;
}

/**
 * 学习工作区概览读取集中在这里，是为了让 due 统计与恢复入口围绕同一模块更新。
 */
export async function loadStudyWorkspace() {
    const payload = await fetchJson("/api/web-console/v1/study/workspace");
    if (!payload) return;
    state.studyWorkspace = payload;
    renderStudyWorkspace();
    requestShellRefresh();
}

/**
 * 当前会话切换确认集中在这里，是为了让复习与练习共享同一条中断边界。
 */
export async function ensureStudySessionSwitchAllowed(targetType) {
    if (!state.studySession || state.studySession.type === targetType) {
        return true;
    }
    const targetLabel = targetType === "review" ? "今日复习" : "自由练习";
    const currentLabel = state.studySession.type === "review" ? "今日复习" : "自由练习";
    if (!confirmDanger(`当前存在未结束的${currentLabel}会话。确认结束它并切换到${targetLabel}吗？`)) {
        return false;
    }
    const response = await postJson("/api/web-console/v1/study/session/end", {});
    if (!response) return false;
    state.studySession = null;
    studySessionRenderer?.();
    requestShellRefresh();
    return true;
}

/**
 * 学习工作区概览渲染集中在这里，是为了让壳层统一表达今日规模和恢复入口。
 */
export function renderStudyWorkspace() {
    const payload = state.studyWorkspace;
    const reviewSummary = document.querySelector("#review-workspace-summary");
    const practiceSummary = document.querySelector("#practice-workspace-summary");
    const metrics = document.querySelector("#study-overview-metrics");
    const startReviewButton = document.querySelector("#start-review-button");
    const startPracticeButton = document.querySelector("#start-practice-button");
    if (!reviewSummary || !practiceSummary || !metrics || !startReviewButton || !startPracticeButton) {
        return;
    }
    metrics.innerHTML = [
        metricCard("今日待复习卡片", payload?.dueCardCount ?? "—"),
        metricCard("今日待复习问题", payload?.dueQuestionCount ?? "—"),
        metricCard("当前会话", state.studySession ? (state.studySession.type === "review" ? "复习中" : "练习中") : "空闲"),
    ].join("");

    startReviewButton.textContent = state.studySession?.type === "review" ? "恢复复习" : "开始复习";
    startPracticeButton.textContent = state.studySession?.type === "practice" ? "恢复练习" : "开始练习";

    if (!payload) {
        reviewSummary.innerHTML = renderEmptyState("正在读取复习信息", "稍等片刻，服务端正在汇总今日待复习规模。");
        practiceSummary.innerHTML = renderEmptyState("正在读取练习入口", "内容列表准备好后，就可以在浏览器端直接进入自由练习。");
        return;
    }

    reviewSummary.innerHTML = payload.dueQuestionCount > 0
        ? `
            <div class="study-status-row">
                <div>
                    <strong>今天还有 ${payload.dueCardCount} 张卡、${payload.dueQuestionCount} 道题待复习</strong>
                    <div class="muted">浏览器端会按卡片组织逐题推进，显示答案后才能提交四档评分。</div>
                </div>
                ${payload.activeSession?.type === "review" ? `<span class="study-badge">${escapeHtml(payload.activeSession.title)}</span>` : ""}
            </div>
        `
        : renderEmptyState("今日暂无待复习", "当前没有满足 due 条件的问题。你仍然可以直接发起自由练习。");

    practiceSummary.innerHTML = `
        <strong>按卡组、卡片或题目范围发起只读练习</strong>
        <div class="muted">刷新后可恢复当前题位；服务停止、访问码刷新或登录失效后，需要重新进入。</div>
        ${payload.activeSession?.type === "practice" ? `<div class="study-inline-actions"><span class="study-badge">${escapeHtml(payload.activeSession.title)}</span><span class="muted">${escapeHtml(payload.activeSession.detail)}</span></div>` : ""}
    `;
}
