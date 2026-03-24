import {
    elements,
    escapeHtml,
    fetchJson,
    formatDateTime,
    postJson,
    requestShellRefresh,
    renderEmptyState,
    showMessage,
    state,
} from "../shared/core.js";
import { prunePracticeDeckSelection, renderPracticeSelection } from "./practice-selection.js";
import { loadStudyWorkspace } from "./study-workspace.js";
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
        state.selectedQuestionId = null;
    }
    if (state.selectedDeckId) {
        await loadCards(state.selectedDeckId);
    } else {
        state.cards = [];
        state.questions = [];
        state.selectedQuestionId = null;
        document.querySelector("#card-list").innerHTML = renderEmptyState("先选择卡组", "创建卡片前需要先确定它属于哪个卡组。");
        document.querySelector("#question-list").innerHTML = renderEmptyState("先选择卡片", "问题会挂在当前选中的卡片下。");
        closeContentForm("card-form");
        closeContentForm("question-form");
    }
    renderPracticeSelection();
    updateContentSelection();
    updateCommandAvailability();
    requestShellRefresh();
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
        state.selectedQuestionId = null;
    }
    if (state.selectedCardId) {
        await loadQuestions(state.selectedCardId);
    } else {
        state.questions = [];
        state.selectedQuestionId = null;
        document.querySelector("#question-list").innerHTML = renderEmptyState("这个卡组还没有问题", "先选择或创建一张卡片，随后就能录入题目。");
        closeContentForm("question-form");
    }
    updateContentSelection();
    updateCommandAvailability();
    requestShellRefresh();
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
    if (!payload.some((item) => item.id === state.selectedQuestionId)) {
        state.selectedQuestionId = payload[0]?.id ?? null;
        list.innerHTML = payload.length
            ? payload.map(renderQuestionItem).join("")
            : renderEmptyState("这个卡片还没有问题", "先新建一条问题，后续就能在手机端直接参与复习。");
    }
    bindQuestionActions(list, payload);
    updateContentSelection();
    updateCommandAvailability();
    requestShellRefresh();
}

/**
 * 当前内容上下文摘要集中生成，是为了让用户始终知道自己正在维护哪组内容。
 */
export function updateContentSelection() {
    const deck = state.decks.find((item) => item.id === state.selectedDeckId);
    const card = state.cards.find((item) => item.id === state.selectedCardId);
    const question = state.questions.find((item) => item.id === state.selectedQuestionId);
    elements.contentSelectionSummary.innerHTML = `
        <div>
            <strong>当前上下文</strong>
            <div class="muted">${escapeHtml(deck?.name || "未选择卡组")} / ${escapeHtml(card?.title || "未选择卡片")} / ${escapeHtml(question?.prompt || "未选择问题")}</div>
        </div>
        <div class="item-actions">
            ${deck ? `<button type="button" data-content-action="practice-deck">练当前卡组</button>` : ""}
            ${card ? `<button type="button" data-content-action="practice-card">练当前卡片</button>` : ""}
            ${question ? `<button type="button" data-content-action="practice-question">练这题</button>` : ""}
        </div>
    `;
    elements.contentSelectionSummary.querySelectorAll("[data-content-action]").forEach((button) => {
        button.addEventListener("click", () => launchPracticeFromContent(button.dataset.contentAction));
    });
    renderContentWorkbench(deck, card, question);
    requestShellRefresh();
}

/**
 * 内容命令可用性集中计算，是为了让新建按钮随 drill-down 上下文变化即时反馈。
 */
export function updateCommandAvailability() {
    elements.newCardButton.disabled = !state.selectedDeckId;
    elements.newQuestionButton.disabled = !state.selectedCardId;
}

/**
 * 卡组列表项带出简要统计，是为了让用户在切换上下文前先判断当前卡组是否值得继续下钻。
 */
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

/**
 * 卡片列表项需要显式带出当前选中态，是为了让用户在 drill-down 列表里能看清自己正在维护哪张卡片。
 */
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

/**
 * 问题列表项支持单独选中，是为了让右侧工作台能稳定绑定到一条明确的问题上下文。
 */
function renderQuestionItem(item) {
    return `
        <div class="item ${item.id === state.selectedQuestionId ? "is-selected" : ""}">
            <div class="item-head">
                <strong>${escapeHtml(item.prompt)}</strong>
                <span class="muted">阶段 ${item.stageIndex}</span>
            </div>
            <div>${escapeHtml(item.answer || "暂无答案")}</div>
            <div class="muted">${item.tags.length ? item.tags.map(escapeHtml).join(" / ") : "暂无标签"}</div>
            <div class="item-actions">
                <button type="button" data-question-select="${item.id}">查看</button>
                <button type="button" data-question-edit="${item.id}">编辑</button>
                <button type="button" data-question-delete="${item.id}">删除</button>
            </div>
        </div>
    `;
}

/**
 * 卡组绑定集中处理，是为了让切换、编辑和归档都遵循同一条上下文刷新路径。
 */
function bindDeckActions(container, decks) {
    container.querySelectorAll("[data-deck-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.selectedDeckId = button.dataset.deckId;
            state.selectedCardId = null;
            state.selectedQuestionId = null;
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

/**
 * 卡片绑定集中处理，是为了让下钻后的卡片动作继续留在当前卡组上下文中完成。
 */
function bindCardActions(container, cards) {
    container.querySelectorAll("[data-card-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.selectedCardId = button.dataset.cardId;
            state.selectedQuestionId = null;
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

/**
 * 问题绑定集中处理，是为了让题目选中态、编辑态和删除动作围绕同一上下文协作。
 */
function bindQuestionActions(container, questions) {
    container.querySelectorAll("[data-question-select]").forEach((button) => {
        button.addEventListener("click", () => {
            state.selectedQuestionId = button.dataset.questionSelect;
            updateContentSelection();
            container.innerHTML = questions.map(renderQuestionItem).join("");
            bindQuestionActions(container, questions);
        });
    });
    container.querySelectorAll("[data-question-edit]").forEach((button) => {
        button.addEventListener("click", () => {
            const question = questions.find((item) => item.id === button.dataset.questionEdit);
            state.selectedQuestionId = question?.id ?? state.selectedQuestionId;
            updateContentSelection();
            openQuestionForm(question);
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

/**
 * 内容工作区可直接把当前上下文送入练习，是为了让 drill-down 管理路径和学习路径真正连起来。
 */
function launchPracticeFromContent(action) {
    const deck = state.decks.find((item) => item.id === state.selectedDeckId);
    const card = state.cards.find((item) => item.id === state.selectedCardId);
    const question = state.questions.find((item) => item.id === state.selectedQuestionId);
    if (!deck) {
        return;
    }
    state.practiceSelection.selectedDeckIds = new Set([deck.id]);
    state.practiceSelection.cardsByDeckId = new Map([[deck.id, state.cards]]);
    if ((action === "practice-card" || action === "practice-question") && card) {
        state.practiceSelection.selectedCardIds = new Set([card.id]);
        state.practiceSelection.questionsByCardId = new Map([[card.id, state.questions]]);
    } else {
        state.practiceSelection.selectedCardIds = new Set();
        state.practiceSelection.questionsByCardId = new Map();
    }
    state.practiceSelection.selectedQuestionIds = action === "practice-question" && question
        ? new Set([question.id])
        : new Set();
    requestShellRefresh();
    window.dispatchEvent(new CustomEvent("yike:launch-practice", {
        detail: {
            returnSection: "content",
            label: resolvePracticeLaunchLabel(action, deck, card, question),
        },
    }));
}

/**
 * 内容工作台右侧详情面板集中渲染，是为了让编辑入口和 drill-down 上下文始终出现在同一视觉区域。
 */
function renderContentWorkbench(deck, card, question) {
    elements.contentDeckDetails.innerHTML = renderDeckDetails(deck);
    elements.contentCardDetails.innerHTML = renderCardDetails(card);
    elements.contentQuestionDetails.innerHTML = renderQuestionDetails(question);
    bindWorkbenchActions(deck, card, question);
}

/**
 * 卡组详情需要带出统计、标签和下一步动作，是为了让用户无需离开当前工作区就能继续下钻或学习。
 */
function renderDeckDetails(deck) {
    if (!deck) {
        return `
            <strong>等待选择卡组</strong>
            <p class="muted">先在左侧确定一个卡组，再继续整理卡片与问题。</p>
        `;
    }
    return `
        <div class="item-head">
            <strong>${escapeHtml(deck.name)}</strong>
            <span class="muted">卡组上下文</span>
        </div>
        <p class="muted">${escapeHtml(deck.description || "当前卡组还没有描述。")}</p>
        <div class="content-detail-metrics">
            <span class="content-detail-chip">${deck.cardCount} 张卡片</span>
            <span class="content-detail-chip">${deck.questionCount} 个问题</span>
            <span class="content-detail-chip">${deck.dueQuestionCount} 题到期</span>
            <span class="content-detail-chip">${deck.intervalStepCount} 步间隔</span>
        </div>
        <div class="content-detail-tags">
            ${(deck.tags.length ? deck.tags : ["暂无标签"]).map((tag) => `<span class="content-detail-chip">${escapeHtml(tag)}</span>`).join("")}
        </div>
        <div class="item-actions">
            <button type="button" data-workbench-action="edit-deck">编辑卡组</button>
            <button type="button" data-workbench-action="new-card">新建卡片</button>
            <button type="button" data-workbench-action="practice-deck">练当前卡组</button>
        </div>
    `;
}

/**
 * 卡片详情显式绑定当前卡组，是为了让用户在编辑或发起学习前再次确认自己没有跳出原路径。
 */
function renderCardDetails(card) {
    if (!card) {
        return `
            <strong>等待选择卡片</strong>
            <p class="muted">卡片详情会跟随当前卡组自动同步。</p>
        `;
    }
    return `
        <div class="item-head">
            <strong>${escapeHtml(card.title)}</strong>
            <span class="muted">卡片上下文</span>
        </div>
        <p class="muted">${escapeHtml(card.description || "当前卡片还没有描述。")}</p>
        <div class="content-detail-metrics">
            <span class="content-detail-chip">${card.questionCount} 个问题</span>
            <span class="content-detail-chip">${card.dueQuestionCount} 题到期</span>
        </div>
        <div class="item-actions">
            <button type="button" data-workbench-action="edit-card">编辑卡片</button>
            <button type="button" data-workbench-action="new-question">新建问题</button>
            <button type="button" data-workbench-action="practice-card">练当前卡片</button>
        </div>
    `;
}

/**
 * 问题详情单独呈现题面和答案，是为了让题目级编辑与练习入口拥有稳定的就地工作台语义。
 */
function renderQuestionDetails(question) {
    if (!question) {
        return `
            <strong>等待选择问题</strong>
            <p class="muted">选中题目后，这里会保留题面、答案和练习入口。</p>
        `;
    }
    return `
        <div class="item-head">
            <strong>${escapeHtml(question.prompt)}</strong>
            <span class="muted">${escapeHtml(question.status)}</span>
        </div>
        <div class="content-detail-answer">${escapeHtml(question.answer || "当前问题还没有答案。")}</div>
        <div class="content-detail-metrics">
            <span class="content-detail-chip">阶段 ${question.stageIndex}</span>
            <span class="content-detail-chip">复习 ${question.reviewCount} 次</span>
            <span class="content-detail-chip">lapse ${question.lapseCount} 次</span>
            <span class="content-detail-chip">到期 ${formatDateTime(question.dueAt)}</span>
            ${question.lastReviewedAt ? `<span class="content-detail-chip">上次复习 ${formatDateTime(question.lastReviewedAt)}</span>` : ""}
        </div>
        <div class="content-detail-tags">
            ${(question.tags.length ? question.tags : ["暂无标签"]).map((tag) => `<span class="content-detail-chip">${escapeHtml(tag)}</span>`).join("")}
        </div>
        <div class="item-actions">
            <button type="button" data-workbench-action="edit-question">编辑问题</button>
            <button type="button" data-workbench-action="practice-question">练这题</button>
        </div>
    `;
}

/**
 * 工作台动作统一在单点绑定，是为了让右侧详情面板不会复制一套独立于列表的业务逻辑。
 */
function bindWorkbenchActions(deck, card, question) {
    elements.contentDeckDetails.classList.toggle("is-active", Boolean(deck));
    elements.contentCardDetails.classList.toggle("is-active", Boolean(card));
    elements.contentQuestionDetails.classList.toggle("is-active", Boolean(question));
    document.querySelectorAll("[data-workbench-action]").forEach((button) => {
        button.addEventListener("click", () => {
            if (button.dataset.workbenchAction === "edit-deck" && deck) {
                openDeckForm(deck);
                return;
            }
            if (button.dataset.workbenchAction === "new-card") {
                openCardForm();
                return;
            }
            if (button.dataset.workbenchAction === "practice-deck") {
                launchPracticeFromContent("practice-deck");
                return;
            }
            if (button.dataset.workbenchAction === "edit-card" && card) {
                openCardForm(card);
                return;
            }
            if (button.dataset.workbenchAction === "new-question") {
                openQuestionForm();
                return;
            }
            if (button.dataset.workbenchAction === "practice-card") {
                launchPracticeFromContent("practice-card");
                return;
            }
            if (button.dataset.workbenchAction === "edit-question" && question) {
                openQuestionForm(question);
                return;
            }
            if (button.dataset.workbenchAction === "practice-question") {
                launchPracticeFromContent("practice-question");
            }
        });
    });
}

/**
 * 练习入口文案统一集中，是为了让卡组、卡片和问题三级来源都能给出清楚的返回标签。
 */
function resolvePracticeLaunchLabel(action, deck, card, question) {
    if (action === "practice-question" && question) {
        return `内容工作台 / ${question.prompt}`;
    }
    if (action === "practice-card" && card) {
        return `内容工作台 / ${card.title}`;
    }
    return `内容工作台 / ${deck.name}`;
}
