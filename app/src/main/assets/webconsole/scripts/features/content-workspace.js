import {
    elements,
    escapeHtml,
    fetchJson,
    postJson,
    renderEmptyState,
    showMessage,
    state,
} from "../shared/core.js";
import { loadStudyWorkspace, prunePracticeDeckSelection, renderPracticeSelection } from "./practice-selection.js";
import { closeContentForm, openCardForm, openDeckForm, openQuestionForm } from "./content-forms.js";

/**
 * 内容树读取集中在工作区模块，是为了让卡组、卡片和问题 drill-down 按同一节奏刷新。
 */
export async function loadDecks() {
    const payload = await fetchJson("/api/web-console/v1/decks");
    if (!payload) return;
    state.decks = payload;
    prunePracticeDeckSelection();
    const list = document.querySelector("#deck-list");
    list.innerHTML = payload.length
        ? payload.map(renderDeckItem).join("")
        : renderEmptyState("还没有卡组", "先新建一个卡组，再继续创建卡片和问题。");
    bindDeckActions(list, payload);
    if (!payload.some((item) => item.id === state.selectedDeckId)) {
        state.selectedDeckId = payload[0]?.id ?? null;
        state.selectedCardId = null;
    }
    if (state.selectedDeckId) {
        await loadCards(state.selectedDeckId);
    } else {
        state.cards = [];
        state.questions = [];
        document.querySelector("#card-list").innerHTML = renderEmptyState("先选择卡组", "创建卡片前需要先确定它属于哪个卡组。");
        document.querySelector("#question-list").innerHTML = renderEmptyState("先选择卡片", "问题会挂在当前选中的卡片下。");
        closeContentForm("card-form");
        closeContentForm("question-form");
    }
    renderPracticeSelection();
    updateContentSelection();
    updateCommandAvailability();
}

/**
 * 卡片读取集中在工作区模块，是为了让当前卡组切换后下一级上下文自动同步。
 */
export async function loadCards(deckId) {
    const payload = await fetchJson(`/api/web-console/v1/cards?deckId=${encodeURIComponent(deckId)}`);
    if (!payload) return;
    state.cards = payload;
    const list = document.querySelector("#card-list");
    list.innerHTML = payload.length
        ? payload.map(renderCardItem).join("")
        : renderEmptyState("这个卡组还没有卡片", "可以先新建一张卡片，再把问题逐条整理进去。");
    bindCardActions(list, payload);
    if (!payload.some((item) => item.id === state.selectedCardId)) {
        state.selectedCardId = payload[0]?.id ?? null;
    }
    if (state.selectedCardId) {
        await loadQuestions(state.selectedCardId);
    } else {
        state.questions = [];
        document.querySelector("#question-list").innerHTML = renderEmptyState("这个卡组还没有问题", "先选择或创建一张卡片，随后就能录入题目。");
        closeContentForm("question-form");
    }
    updateContentSelection();
    updateCommandAvailability();
}

/**
 * 问题读取集中在工作区模块，是为了让题目编辑总是依附在当前卡片上下文下。
 */
export async function loadQuestions(cardId) {
    const payload = await fetchJson(`/api/web-console/v1/questions?cardId=${encodeURIComponent(cardId)}`);
    if (!payload) return;
    state.questions = payload;
    const list = document.querySelector("#question-list");
    list.innerHTML = payload.length
        ? payload.map(renderQuestionItem).join("")
        : renderEmptyState("这个卡片还没有问题", "先新建一条问题，后续就能在手机端直接参与复习。");
    bindQuestionActions(list, payload);
    updateContentSelection();
    updateCommandAvailability();
}

/**
 * 当前内容上下文摘要集中生成，是为了让用户始终知道自己正在维护哪组内容。
 */
export function updateContentSelection() {
    const deck = state.decks.find((item) => item.id === state.selectedDeckId);
    const card = state.cards.find((item) => item.id === state.selectedCardId);
    elements.contentSelectionSummary.innerHTML = `
        <strong>当前上下文</strong>
        <div class="muted">${escapeHtml(deck?.name || "未选择卡组")} / ${escapeHtml(card?.title || "未选择卡片")}</div>
    `;
}

/**
 * 内容命令可用性集中计算，是为了让新建按钮随 drill-down 上下文变化即时反馈。
 */
export function updateCommandAvailability() {
    elements.newCardButton.disabled = !state.selectedDeckId;
    elements.newQuestionButton.disabled = !state.selectedCardId;
}

function renderDeckItem(item) {
    return `
        <div class="item ${item.id === state.selectedDeckId ? "is-selected" : ""}">
            <div class="item-head">
                <strong>${escapeHtml(item.name)}</strong>
                <span class="muted">${item.dueQuestionCount} 题到期</span>
            </div>
            <div class="muted">${item.cardCount} 张卡片 · ${item.questionCount} 个问题</div>
            <div class="muted">${item.description ? escapeHtml(item.description) : "暂无描述"}</div>
            <div class="item-actions">
                <button type="button" data-deck-id="${item.id}">查看</button>
                <button type="button" data-deck-edit="${item.id}">编辑</button>
                <button type="button" data-deck-archive-id="${item.id}" data-deck-archive="${item.archived ? "false" : "true"}">${item.archived ? "恢复" : "归档"}</button>
            </div>
        </div>
    `;
}

function renderCardItem(item) {
    return `
        <div class="item ${item.id === state.selectedCardId ? "is-selected" : ""}">
            <div class="item-head">
                <strong>${escapeHtml(item.title)}</strong>
                <span class="muted">${item.dueQuestionCount} 题到期</span>
            </div>
            <div class="muted">${item.questionCount} 个问题</div>
            <div class="muted">${item.description ? escapeHtml(item.description) : "暂无描述"}</div>
            <div class="item-actions">
                <button type="button" data-card-id="${item.id}">查看</button>
                <button type="button" data-card-edit="${item.id}">编辑</button>
                <button type="button" data-card-archive-id="${item.id}" data-card-archive="${item.archived ? "false" : "true"}">${item.archived ? "恢复" : "归档"}</button>
            </div>
        </div>
    `;
}

function renderQuestionItem(item) {
    return `
        <div class="item">
            <div class="item-head">
                <strong>${escapeHtml(item.prompt)}</strong>
                <span class="muted">阶段 ${item.stageIndex}</span>
            </div>
            <div>${escapeHtml(item.answer || "暂无答案")}</div>
            <div class="muted">${item.tags.length ? item.tags.map(escapeHtml).join(" / ") : "暂无标签"}</div>
            <div class="item-actions">
                <button type="button" data-question-id="${item.id}">编辑</button>
                <button type="button" data-question-delete="${item.id}">删除</button>
            </div>
        </div>
    `;
}

function bindDeckActions(container, decks) {
    container.querySelectorAll("[data-deck-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.selectedDeckId = button.dataset.deckId;
            state.selectedCardId = null;
            await loadCards(button.dataset.deckId);
            updateContentSelection();
        });
    });
    container.querySelectorAll("[data-deck-edit]").forEach((button) => {
        button.addEventListener("click", () => {
            openDeckForm(decks.find((item) => item.id === button.dataset.deckEdit));
        });
    });
    container.querySelectorAll("[data-deck-archive-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            const response = await postJson("/api/web-console/v1/decks/archive", {
                id: button.dataset.deckArchiveId,
                archived: button.dataset.deckArchive === "true",
            });
            if (!response) return;
            showMessage(response.message);
            await loadDecks();
            await loadStudyWorkspace();
        });
    });
}

function bindCardActions(container, cards) {
    container.querySelectorAll("[data-card-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.selectedCardId = button.dataset.cardId;
            await loadQuestions(button.dataset.cardId);
            updateContentSelection();
        });
    });
    container.querySelectorAll("[data-card-edit]").forEach((button) => {
        button.addEventListener("click", () => {
            openCardForm(cards.find((item) => item.id === button.dataset.cardEdit));
        });
    });
    container.querySelectorAll("[data-card-archive-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            const response = await postJson("/api/web-console/v1/cards/archive", {
                id: button.dataset.cardArchiveId,
                archived: button.dataset.cardArchive === "true",
            });
            if (!response) return;
            showMessage(response.message);
            await loadCards(state.selectedDeckId);
            await loadStudyWorkspace();
        });
    });
}

function bindQuestionActions(container, questions) {
    container.querySelectorAll("[data-question-id]").forEach((button) => {
        button.addEventListener("click", () => {
            openQuestionForm(questions.find((item) => item.id === button.dataset.questionId));
        });
    });
    container.querySelectorAll("[data-question-delete]").forEach((button) => {
        button.addEventListener("click", async () => {
            const response = await postJson("/api/web-console/v1/questions/delete", {
                id: button.dataset.questionDelete,
            });
            if (!response) return;
            showMessage(response.message);
            await loadQuestions(state.selectedCardId);
            await loadStudyWorkspace();
        });
    });
}
