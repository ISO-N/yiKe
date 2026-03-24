import {
    elements,
    getFieldValue,
    getOptionalFieldValue,
    postJson,
    setFieldValue,
    showMessage,
    splitTags,
    state,
} from "../shared/core.js";
import { loadCards, loadDecks, loadQuestions } from "./content-workspace.js";
import { loadStudyWorkspace } from "./study-workspace.js";

/**
 * 内容表单事件集中绑定，是为了让新建、编辑和关闭动作围绕同一表单边界演进。
 */
export function bindContentFormEvents() {
    document.querySelector("#new-deck-button").addEventListener("click", () => openDeckForm());
    elements.newCardButton.addEventListener("click", () => openCardForm());
    elements.newQuestionButton.addEventListener("click", () => openQuestionForm());
    elements.deckForm.addEventListener("submit", submitDeckForm);
    elements.cardForm.addEventListener("submit", submitCardForm);
    elements.questionForm.addEventListener("submit", submitQuestionForm);
    document.querySelectorAll("[data-close-form]").forEach((button) => {
        button.addEventListener("click", () => closeContentForm(button.dataset.closeForm));
    });
}

/**
 * 卡组保存统一走同一提交流程，是为了让创建和编辑都能复用相同的回写边界。
 */
export async function submitDeckForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const response = await postJson("/api/web-console/v1/decks/upsert", {
        id: getOptionalFieldValue(form, "id"),
        name: getFieldValue(form, "name").trim(),
        description: getFieldValue(form, "description").trim(),
        tags: splitTags(getFieldValue(form, "tags")),
        intervalStepCount: Number(getFieldValue(form, "intervalStepCount") || 4),
    });
    if (!response) return;
    showMessage(response.message);
    closeContentForm("deck-form");
    await loadDecks();
    await loadStudyWorkspace();
}

/**
 * 卡片保存集中处理，是为了让当前卡组上下文和卡片编辑路径始终保持一致。
 */
export async function submitCardForm(event) {
    event.preventDefault();
    if (!state.selectedDeckId) {
        showMessage("请先选择一个卡组。", true);
        return;
    }
    const form = event.currentTarget;
    const response = await postJson("/api/web-console/v1/cards/upsert", {
        id: getOptionalFieldValue(form, "id"),
        deckId: state.selectedDeckId,
        title: getFieldValue(form, "title").trim(),
        description: getFieldValue(form, "description").trim(),
    });
    if (!response) return;
    showMessage(response.message);
    closeContentForm("card-form");
    await loadCards(state.selectedDeckId);
    await loadStudyWorkspace();
}

/**
 * 问题保存集中处理，是为了让题目编辑完成后内容树和学习入口摘要一起保持最新。
 */
export async function submitQuestionForm(event) {
    event.preventDefault();
    if (!state.selectedCardId) {
        showMessage("请先选择一个卡片。", true);
        return;
    }
    const form = event.currentTarget;
    const response = await postJson("/api/web-console/v1/questions/upsert", {
        id: getOptionalFieldValue(form, "id"),
        cardId: state.selectedCardId,
        prompt: getFieldValue(form, "prompt").trim(),
        answer: getFieldValue(form, "answer").trim(),
        tags: splitTags(getFieldValue(form, "tags")),
    });
    if (!response) return;
    showMessage(response.message);
    closeContentForm("question-form");
    await loadQuestions(state.selectedCardId);
    await loadStudyWorkspace();
}

/**
 * 卡组表单回填单独处理，是为了让新建和编辑共享同一份表单结构而不复制字段映射。
 */
export function openDeckForm(deck) {
    document.querySelector("#deck-form-title").textContent = deck ? "编辑卡组" : "新建卡组";
    setFieldValue(elements.deckForm, "id", deck?.id || "");
    setFieldValue(elements.deckForm, "name", deck?.name || "");
    setFieldValue(elements.deckForm, "description", deck?.description || "");
    setFieldValue(elements.deckForm, "tags", deck?.tags?.join(", ") || "");
    setFieldValue(elements.deckForm, "intervalStepCount", String(deck?.intervalStepCount || 4));
    elements.deckForm.hidden = false;
}

/**
 * 卡片表单回填单独处理，是为了让卡片编辑继续依附当前卡组 drill-down 上下文。
 */
export function openCardForm(card) {
    if (!state.selectedDeckId) {
        showMessage("请先选择一个卡组。", true);
        return;
    }
    document.querySelector("#card-form-title").textContent = card ? "编辑卡片" : "新建卡片";
    setFieldValue(elements.cardForm, "id", card?.id || "");
    setFieldValue(elements.cardForm, "title", card?.title || "");
    setFieldValue(elements.cardForm, "description", card?.description || "");
    elements.cardForm.hidden = false;
}

/**
 * 问题表单回填单独处理，是为了让题目编辑始终绑定在当前卡片上下文之下。
 */
export function openQuestionForm(question) {
    if (!state.selectedCardId) {
        showMessage("请先选择一个卡片。", true);
        return;
    }
    document.querySelector("#question-form-title").textContent = question ? "编辑问题" : "新建问题";
    setFieldValue(elements.questionForm, "id", question?.id || "");
    setFieldValue(elements.questionForm, "prompt", question?.prompt || "");
    setFieldValue(elements.questionForm, "answer", question?.answer || "");
    setFieldValue(elements.questionForm, "tags", question?.tags?.join(", ") || "");
    elements.questionForm.hidden = false;
}

/**
 * 表单关闭集中处理，是为了避免上下文切换后遗留过期的编辑态。
 */
export function closeContentForm(formId) {
    const form = document.querySelector(`#${formId}`);
    if (!form) return;
    form.reset();
    form.hidden = true;
}
