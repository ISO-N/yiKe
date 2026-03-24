import { postJson, requestShellRefresh, showMessage, state } from "../shared/core.js";
import { ensureStudySessionSwitchAllowed, loadStudyWorkspace } from "./study-workspace.js";
import { normalizeInternalPath, saveReturnContext } from "../shared/navigation.js";

/**
 * 练习会话动作独立出来，是为了让范围选择和会话推进保持不同的模块职责。
 */
export async function startPracticeSession() {
    const payload = await startPracticeSessionBySelection({
        deckIds: [...state.practiceSelection.selectedDeckIds],
        cardIds: [...state.practiceSelection.selectedCardIds],
        questionIds: [...state.practiceSelection.selectedQuestionIds],
        orderMode: state.practiceSelection.orderMode,
    });
    if (!payload) {
        return;
    }
    state.studySession = payload;
    showMessage("自由练习已开始。");
    await loadStudyWorkspace();
    requestShellRefresh();
}

/**
 * 跨页发起练习复用同一请求入口，是为了让内容页和搜索页不再各自复制一套启动协议。
 */
export async function launchPracticeSession(selection, returnContext) {
    const payload = await startPracticeSessionBySelection(selection);
    if (!payload) {
        return false;
    }
    if (returnContext?.path) {
        saveReturnContext({
            path: normalizeInternalPath(returnContext.path),
            label: returnContext.label,
        });
    }
    window.location.assign("/study");
    return true;
}

/**
 * 真正的练习启动请求抽成单点，是为了让本页启动和跨页启动共享完全一致的边界与校验。
 */
async function startPracticeSessionBySelection(selection) {
    const selectedDeckIds = selection.deckIds ?? [];
    const selectedCardIds = selection.cardIds ?? [];
    const selectedQuestionIds = selection.questionIds ?? [];
    if (!selectedDeckIds.length && !selectedCardIds.length && !selectedQuestionIds.length) {
        showMessage("请至少选择一个练习范围。", true);
        return null;
    }
    const allowed = await ensureStudySessionSwitchAllowed("practice");
    if (!allowed) {
        return null;
    }
    const payload = await postJson("/api/web-console/v1/study/practice/start", {
        deckIds: selectedDeckIds,
        cardIds: selectedCardIds,
        questionIds: selectedQuestionIds,
        orderMode: selection.orderMode ?? state.practiceSelection.orderMode,
    });
    return payload ?? null;
}

/**
 * 练习切题动作独立出来，是为了让只读练习推进和范围编辑彼此解耦。
 */
export async function navigatePracticeSession(action) {
    const payload = await postJson("/api/web-console/v1/study/practice/navigate", { action });
    if (!payload) return;
    state.studySession = payload;
    requestShellRefresh();
}
