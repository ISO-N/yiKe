import {
    escapeHtml,
    fetchJson,
    renderEmptyState,
    state,
} from "../shared/core.js";

/**
 * 练习范围刷新公开出来，是为了让内容树变化后可以主动同步学习入口上下文。
 */
export function renderPracticeSelection() {
    renderPracticeDeckOptions();
    renderPracticeCardOptions();
    renderPracticeQuestionOptions();
    renderPracticeSelectionSummary();
}

/**
 * 卡组范围修剪公开出来，是为了让内容对象变化后无效练习范围及时失效。
 */
export function prunePracticeDeckSelection() {
    const availableDeckIds = new Set(state.decks.map((deck) => deck.id));
    pruneSetByAvailability(state.practiceSelection.selectedDeckIds, availableDeckIds);
    if (!state.practiceSelection.selectedDeckIds.size) {
        state.practiceSelection.selectedCardIds.clear();
        state.practiceSelection.selectedQuestionIds.clear();
        state.practiceSelection.cardsByDeckId.clear();
        state.practiceSelection.questionsByCardId.clear();
        return;
    }
    for (const deckId of [...state.practiceSelection.cardsByDeckId.keys()]) {
        if (!state.practiceSelection.selectedDeckIds.has(deckId)) {
            state.practiceSelection.cardsByDeckId.delete(deckId);
        }
    }
    prunePracticeCardSelection();
}

/**
 * 练习范围摘要集中渲染，是为了让用户在进入会话前始终清楚本次作用范围。
 */
export function renderPracticeSelectionSummary() {
    const summary = document.querySelector("#practice-selection-summary");
    const deckCount = state.practiceSelection.selectedDeckIds.size;
    const cardCount = state.practiceSelection.selectedCardIds.size;
    const questionCount = state.practiceSelection.selectedQuestionIds.size;
    const modeLabel = state.practiceSelection.orderMode === "random" ? "单次随机" : "稳定顺序";
    if (!deckCount && !cardCount && !questionCount) {
        summary.textContent = "当前尚未选择练习范围。";
        return;
    }
    summary.textContent = `已选 ${deckCount} 个卡组、${cardCount} 张卡片、${questionCount} 道题，开始练习后将按“${modeLabel}”进入浏览器只读会话。`;
}

function renderPracticeDeckOptions() {
    const container = document.querySelector("#practice-deck-options");
    document.querySelector("#practice-deck-count").textContent = `${state.practiceSelection.selectedDeckIds.size} 已选`;
    if (!state.decks.length) {
        container.innerHTML = renderEmptyState("暂无卡组", "先在内容管理中创建卡组，才可以从浏览器端发起自由练习。");
        return;
    }
    container.innerHTML = state.decks.map((deck) => `
        <div class="option-item">
            <label>
                <input type="checkbox" data-practice-deck-id="${deck.id}" ${state.practiceSelection.selectedDeckIds.has(deck.id) ? "checked" : ""}>
                <span>
                    <strong>${escapeHtml(deck.name)}</strong>
                    <span class="muted">${deck.cardCount} 张卡片 · ${deck.questionCount} 个问题</span>
                </span>
            </label>
        </div>
    `).join("");
    container.querySelectorAll("[data-practice-deck-id]").forEach((input) => {
        input.addEventListener("change", () => togglePracticeDeck(input.dataset.practiceDeckId));
    });
}

function renderPracticeCardOptions() {
    const container = document.querySelector("#practice-card-options");
    const availableCards = collectPracticeCards();
    document.querySelector("#practice-card-count").textContent = `${state.practiceSelection.selectedCardIds.size} 已选`;
    if (!state.practiceSelection.selectedDeckIds.size) {
        container.innerHTML = renderEmptyState("先选择卡组", "至少选中一个卡组后，浏览器才会加载该范围内的卡片。");
        return;
    }
    if (state.practiceSelection.isLoadingCards) {
        container.innerHTML = renderEmptyState("正在读取卡片", "稍等片刻，系统正在汇总所选卡组下的可练习卡片。");
        return;
    }
    if (!availableCards.length) {
        container.innerHTML = renderEmptyState("当前范围没有卡片", "请换一个卡组，或先在内容管理中整理卡片。");
        return;
    }
    container.innerHTML = availableCards.map((card) => `
        <div class="option-item">
            <label>
                <input type="checkbox" data-practice-card-id="${card.id}" ${state.practiceSelection.selectedCardIds.has(card.id) ? "checked" : ""}>
                <span>
                    <strong>${escapeHtml(card.title)}</strong>
                    <span class="muted">${escapeHtml(resolveDeckName(card.deckId))} · ${card.questionCount} 个问题</span>
                </span>
            </label>
        </div>
    `).join("");
    container.querySelectorAll("[data-practice-card-id]").forEach((input) => {
        input.addEventListener("change", () => togglePracticeCard(input.dataset.practiceCardId));
    });
}

function renderPracticeQuestionOptions() {
    const container = document.querySelector("#practice-question-options");
    const availableQuestions = collectPracticeQuestions();
    document.querySelector("#practice-question-count").textContent = `${state.practiceSelection.selectedQuestionIds.size} 已选`;
    if (!state.practiceSelection.selectedCardIds.size) {
        container.innerHTML = renderEmptyState("先选择卡片", "题目级范围建立在已选卡片之上，这样刷新恢复后仍能清楚知道当前题来自哪张卡。");
        return;
    }
    if (state.practiceSelection.isLoadingQuestions) {
        container.innerHTML = renderEmptyState("正在读取题目", "稍等片刻，系统正在拉取所选卡片下的可练习题目。");
        return;
    }
    if (!availableQuestions.length) {
        container.innerHTML = renderEmptyState("当前范围没有题目", "这些卡片下暂时没有可练习题目，请调整选择范围。");
        return;
    }
    container.innerHTML = availableQuestions.map((question) => `
        <div class="option-item">
            <label>
                <input type="checkbox" data-practice-question-id="${question.id}" ${state.practiceSelection.selectedQuestionIds.has(question.id) ? "checked" : ""}>
                <span>
                    <strong>${escapeHtml(question.prompt)}</strong>
                    <span class="muted">${escapeHtml(resolveCardTitle(question.cardId))}</span>
                </span>
            </label>
        </div>
    `).join("");
    container.querySelectorAll("[data-practice-question-id]").forEach((input) => {
        input.addEventListener("change", () => togglePracticeQuestion(input.dataset.practiceQuestionId));
    });
}

async function togglePracticeDeck(deckId) {
    applyToggle(state.practiceSelection.selectedDeckIds, deckId);
    const availableDeckIds = new Set(state.decks.map((deck) => deck.id));
    pruneSetByAvailability(state.practiceSelection.selectedDeckIds, availableDeckIds);
    if (!state.practiceSelection.selectedDeckIds.size) {
        state.practiceSelection.selectedCardIds.clear();
        state.practiceSelection.selectedQuestionIds.clear();
        state.practiceSelection.cardsByDeckId.clear();
        state.practiceSelection.questionsByCardId.clear();
        renderPracticeSelection();
        return;
    }
    await syncPracticeCards();
}

async function togglePracticeCard(cardId) {
    applyToggle(state.practiceSelection.selectedCardIds, cardId);
    if (!state.practiceSelection.selectedCardIds.size) {
        state.practiceSelection.selectedQuestionIds.clear();
        state.practiceSelection.questionsByCardId.clear();
        renderPracticeSelection();
        return;
    }
    await syncPracticeQuestions();
}

function togglePracticeQuestion(questionId) {
    applyToggle(state.practiceSelection.selectedQuestionIds, questionId);
    renderPracticeSelection();
}

async function syncPracticeCards() {
    state.practiceSelection.isLoadingCards = true;
    renderPracticeSelection();
    const cardsByDeckId = new Map();
    for (const deckId of state.practiceSelection.selectedDeckIds) {
        const payload = await fetchJson(`/api/web-console/v1/cards?deckId=${encodeURIComponent(deckId)}`);
        if (!payload) continue;
        cardsByDeckId.set(deckId, payload);
    }
    state.practiceSelection.cardsByDeckId = cardsByDeckId;
    state.practiceSelection.isLoadingCards = false;
    prunePracticeCardSelection();
    if (state.practiceSelection.selectedCardIds.size) {
        await syncPracticeQuestions();
        return;
    }
    state.practiceSelection.selectedQuestionIds.clear();
    state.practiceSelection.questionsByCardId.clear();
    renderPracticeSelection();
}

async function syncPracticeQuestions() {
    state.practiceSelection.isLoadingQuestions = true;
    renderPracticeSelection();
    const questionsByCardId = new Map();
    for (const cardId of state.practiceSelection.selectedCardIds) {
        const payload = await fetchJson(`/api/web-console/v1/questions?cardId=${encodeURIComponent(cardId)}`);
        if (!payload) continue;
        questionsByCardId.set(cardId, payload);
    }
    state.practiceSelection.questionsByCardId = questionsByCardId;
    state.practiceSelection.isLoadingQuestions = false;
    prunePracticeQuestionSelection();
    renderPracticeSelection();
}

function collectPracticeCards() {
    return [...state.practiceSelection.cardsByDeckId.values()].flat();
}

function collectPracticeQuestions() {
    return [...state.practiceSelection.questionsByCardId.values()].flat();
}

function prunePracticeCardSelection() {
    const availableCardIds = new Set(collectPracticeCards().map((card) => card.id));
    pruneSetByAvailability(state.practiceSelection.selectedCardIds, availableCardIds);
    for (const cardId of [...state.practiceSelection.questionsByCardId.keys()]) {
        if (!state.practiceSelection.selectedCardIds.has(cardId)) {
            state.practiceSelection.questionsByCardId.delete(cardId);
        }
    }
    prunePracticeQuestionSelection();
}

function prunePracticeQuestionSelection() {
    const availableQuestionIds = new Set(collectPracticeQuestions().map((question) => question.id));
    pruneSetByAvailability(state.practiceSelection.selectedQuestionIds, availableQuestionIds);
}

function pruneSetByAvailability(targetSet, availableIds) {
    for (const value of [...targetSet]) {
        if (!availableIds.has(value)) {
            targetSet.delete(value);
        }
    }
}

function applyToggle(targetSet, value) {
    if (targetSet.has(value)) targetSet.delete(value);
    else targetSet.add(value);
}

function resolveDeckName(deckId) {
    return state.decks.find((deck) => deck.id === deckId)?.name ?? "未命名卡组";
}

function resolveCardTitle(cardId) {
    return collectPracticeCards().find((card) => card.id === cardId)?.title ?? "未命名卡片";
}
