import {
    confirmDanger,
    elements,
    escapeHtml,
    fetchOptionalJson,
    postJson,
    requestShellRefresh,
    renderEmptyState,
    showMessage,
    state,
} from "../shared/core.js";
import {
    renderPracticeSelectionSummary,
} from "./practice-selection.js";
import { navigatePracticeSession, startPracticeSession } from "./practice-session-actions.js";
import {
    ensureStudySessionSwitchAllowed,
    loadStudyWorkspace,
    renderStudyWorkspace,
    setStudyWorkspaceCallbacks,
} from "./study-workspace.js";

/**
 * 学习会话事件绑定集中在这里，是为了让正式复习和会话结束动作围绕同一模块收口。
 */
export function bindStudySessionEvents() {
    setStudyWorkspaceCallbacks({ renderStudySession });
    elements.startReviewButton.addEventListener("click", startReviewSession);
    elements.startPracticeButton.addEventListener("click", startPracticeSession);
    elements.endStudySessionButton.addEventListener("click", endStudySession);
    elements.practiceOrderModeSelect.addEventListener("change", (event) => {
        state.practiceSelection.orderMode = event.currentTarget.value;
        renderPracticeSelectionSummary();
    });
}

/**
 * 学习会话读取集中在这里，是为了让刷新恢复与显式进入工作区共用同一恢复入口。
 */
export async function loadStudySession() {
    const payload = await fetchOptionalJson("/api/web-console/v1/study/session");
    if (payload === undefined) return;
    state.studySession = payload;
    renderStudyWorkspace();
    renderStudySession();
    requestShellRefresh();
}

/**
 * 正式复习入口集中处理，是为了让恢复已有会话与开始新会话共享同一边界。
 */
export async function startReviewSession() {
    const allowed = await ensureStudySessionSwitchAllowed("review");
    if (!allowed) return;
    const payload = await postJson("/api/web-console/v1/study/review/start", {});
    if (!payload) return;
    state.studySession = payload;
    showMessage("已进入今日复习。");
    renderStudySession();
    await loadStudyWorkspace();
    requestShellRefresh();
}

/**
 * 当前题显示答案动作集中处理，是为了让刷新恢复后仍能准确表达当前是否已解锁下一步。
 */
export async function revealStudyAnswer() {
    const payload = await postJson("/api/web-console/v1/study/answer/reveal", {});
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
    requestShellRefresh();
}

/**
 * 复习评分集中处理，是为了让桌面端复习推进继续走正式复习语义。
 */
export async function submitReviewRating(rating) {
    const payload = await postJson("/api/web-console/v1/study/review/rate", { rating });
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
    await loadStudyWorkspace();
    requestShellRefresh();
}

/**
 * 下一张卡动作集中处理，是为了让卡片完成态与会话完成态共享同一推进逻辑。
 */
export async function continueReviewSession() {
    const payload = await postJson("/api/web-console/v1/study/review/next-card", {});
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
    await loadStudyWorkspace();
    requestShellRefresh();
}

/**
 * 当前学习会话结束集中处理，是为了让返回学习工作区的后果始终明确。
 */
export async function endStudySession() {
    if (state.studySession && !confirmDanger(buildStudyExitConfirmMessage(state.studySession))) {
        return;
    }
    const response = await postJson("/api/web-console/v1/study/session/end", {});
    if (!response) return;
    state.studySession = null;
    renderStudySession();
    await loadStudyWorkspace();
    showMessage(response.message);
    requestShellRefresh();
    window.dispatchEvent(new CustomEvent("yike:study-ended"));
}

/**
 * 学习会话主卡片渲染集中在这里，是为了让复习和练习共享同一工作区主视图容器。
 */
export function renderStudySession() {
    if (!state.studySession) {
        elements.studySessionCard.hidden = true;
        elements.studySessionContent.innerHTML = "";
        return;
    }
    elements.studySessionCard.hidden = false;
    elements.studySessionTitle.textContent = state.studySession.title;
    elements.studySessionSubtitle.textContent = state.studySession.summary;
    elements.studySessionContent.innerHTML = state.studySession.type === "review"
        ? renderReviewStudySession(state.studySession.review)
        : renderPracticeStudySession(state.studySession.practice);
    bindRenderedStudySessionActions();
}

function renderReviewStudySession(review) {
    if (!review) {
        return renderEmptyState("复习会话不可用", "当前会话内容丢失，请返回学习工作区重新进入。");
    }
    if (review.isSessionCompleted) {
        return `
            <div class="study-session-empty">
                <strong>本轮复习已完成</strong>
                <div class="muted">今日待复习卡片已经全部处理完毕，你可以回到工作区继续自由练习，或结束当前会话。</div>
            </div>
        `;
    }
    if (review.isCardCompleted) {
        return `
            <div class="study-status-row">
                <span class="study-badge">${escapeHtml(review.cardProgressText)}</span>
                <span class="study-badge">${escapeHtml(review.questionProgressText)}</span>
            </div>
            <div class="study-session-empty">
                <strong>${escapeHtml(review.cardTitle || "当前卡片")} 已完成</strong>
                <div class="muted">${review.nextCardTitle ? `下一张待复习卡片是 ${escapeHtml(review.nextCardTitle)}。` : "当前已经没有下一张待复习卡片。"} </div>
                <div class="study-inline-actions">
                    <button id="continue-review-button" class="primary-button" type="button">${review.nextCardTitle ? "继续下一张" : "确认完成"}</button>
                </div>
            </div>
        `;
    }
    const question = review.currentQuestion;
    return `
        <div class="study-status-row">
            <span class="study-badge">${escapeHtml(review.cardProgressText)}</span>
            <span class="study-badge">${escapeHtml(review.questionProgressText)}</span>
        </div>
        <div class="muted">${escapeHtml(review.deckName || "未分组")} / ${escapeHtml(review.cardTitle || "当前卡片")}</div>
        <div class="study-question-card">
            <div class="item-head">
                <strong>题面</strong>
                <span class="muted">阶段 ${question?.stageIndex ?? "—"}</span>
            </div>
            <div>${escapeHtml(question?.prompt || "当前题目不存在")}</div>
        </div>
        <div class="study-question-card">
            <div class="item-head">
                <strong>答案</strong>
                ${review.answerVisible ? `<span class="muted">已展开</span>` : `<button id="reveal-study-answer-button" class="ghost-button" type="button">显示答案</button>`}
            </div>
            <div class="study-answer">${review.answerVisible ? escapeHtml(question?.answerText || "无答案") : "显示答案后才会解锁评分动作。"}</div>
        </div>
        <div class="study-rating-grid">
            ${renderRatingButton("AGAIN", "再来", "is-danger", !review.answerVisible)}
            ${renderRatingButton("HARD", "偏难", "is-primary", !review.answerVisible)}
            ${renderRatingButton("GOOD", "良好", "is-primary", !review.answerVisible)}
            ${renderRatingButton("EASY", "轻松", "is-strong", !review.answerVisible)}
        </div>
    `;
}

function renderPracticeStudySession(practice) {
    if (!practice) {
        return renderEmptyState("练习会话不可用", "当前会话内容丢失，请返回学习工作区重新进入。");
    }
    const question = practice.currentQuestion;
    return `
        <div class="study-status-row">
            <span class="study-badge">${escapeHtml(practice.progressText)}</span>
            <span class="study-badge">${escapeHtml(practice.orderModeLabel)}</span>
        </div>
        <div class="muted">${escapeHtml(question?.deckName || "未分组")} / ${escapeHtml(question?.cardTitle || "当前卡片")}</div>
        <div class="study-question-card">
            <div class="item-head">
                <strong>题面</strong>
                <span class="muted">${escapeHtml(practice.progressText)}</span>
            </div>
            <div>${escapeHtml(question?.prompt || "当前题目不存在")}</div>
        </div>
        <div class="study-question-card">
            <div class="item-head">
                <strong>答案</strong>
                ${practice.answerVisible ? `<span class="muted">已展开</span>` : `<button id="reveal-study-answer-button" class="ghost-button" type="button">显示答案</button>`}
            </div>
            <div class="study-answer">${practice.answerVisible ? escapeHtml(question?.answerText || "无答案") : "显示答案后，可继续上一题或下一题。"}</div>
        </div>
        <div class="study-action-row">
            <button id="practice-previous-button" class="ghost-button" type="button" ${practice.canGoPrevious ? "" : "disabled"}>上一题</button>
            <button id="practice-next-button" class="primary-button" type="button" ${practice.canGoNext ? "" : "disabled"}>下一题</button>
        </div>
    `;
}

function bindRenderedStudySessionActions() {
    const revealButton = document.querySelector("#reveal-study-answer-button");
    if (revealButton) {
        revealButton.addEventListener("click", revealStudyAnswer);
    }
    const continueButton = document.querySelector("#continue-review-button");
    if (continueButton) {
        continueButton.addEventListener("click", continueReviewSession);
    }
    document.querySelectorAll("[data-review-rating]").forEach((button) => {
        button.addEventListener("click", () => submitReviewRating(button.dataset.reviewRating));
    });
    const previousButton = document.querySelector("#practice-previous-button");
    if (previousButton) {
        previousButton.addEventListener("click", () => navigatePracticeSession("previous"));
    }
    const nextButton = document.querySelector("#practice-next-button");
    if (nextButton) {
        nextButton.addEventListener("click", () => navigatePracticeSession("next"));
    }
}

function renderRatingButton(rating, label, className, disabled) {
    return `
        <button class="study-rating-button ${className}" type="button" data-review-rating="${rating}" ${disabled ? "disabled" : ""}>
            ${escapeHtml(label)}
        </button>
    `;
}

function buildStudyExitConfirmMessage(studySession) {
    if (studySession.type === "review") {
        if (studySession.review?.isSessionCompleted) {
            return "确认结束当前复习会话吗？结束后会回到学习工作区。";
        }
        return "当前今日复习尚未完成。确认结束会话并返回学习工作区吗？";
    }
    return "当前自由练习尚未结束。确认结束会话并返回学习工作区吗？";
}
