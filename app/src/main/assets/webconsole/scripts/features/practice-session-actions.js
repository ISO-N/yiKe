import { postJson, requestShellRefresh, showMessage, state } from "../shared/core.js";
import { ensureStudySessionSwitchAllowed, loadStudyWorkspace } from "./study-workspace.js";

/**
 * 练习会话动作独立出来，是为了让范围选择和会话推进保持不同的模块职责。
 */
export async function startPracticeSession() {
    const selectedDeckIds = [...state.practiceSelection.selectedDeckIds];
    const selectedCardIds = [...state.practiceSelection.selectedCardIds];
    const selectedQuestionIds = [...state.practiceSelection.selectedQuestionIds];
    if (!selectedDeckIds.length && !selectedCardIds.length && !selectedQuestionIds.length) {
        showMessage("请至少选择一个练习范围。", true);
        return;
    }
    const allowed = await ensureStudySessionSwitchAllowed("practice");
    if (!allowed) return;
    const payload = await postJson("/api/web-console/v1/study/practice/start", {
        deckIds: selectedDeckIds,
        cardIds: selectedCardIds,
        questionIds: selectedQuestionIds,
        orderMode: state.practiceSelection.orderMode,
    });
    if (!payload) return;
    state.studySession = payload;
    showMessage("自由练习已开始。");
    await loadStudyWorkspace();
    requestShellRefresh();
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
