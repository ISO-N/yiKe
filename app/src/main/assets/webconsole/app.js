const state = {
    currentSection: "study",
    selectedDeckId: null,
    selectedCardId: null,
    decks: [],
    cards: [],
    questions: [],
    selectedBackupFile: null,
    isExporting: false,
    isRestoring: false,
    studyWorkspace: null,
    studySession: null,
    practiceSelection: {
        selectedDeckIds: new Set(),
        selectedCardIds: new Set(),
        selectedQuestionIds: new Set(),
        cardsByDeckId: new Map(),
        questionsByCardId: new Map(),
        orderMode: "sequential",
        isLoadingCards: false,
        isLoadingQuestions: false,
    },
};

const loginView = document.querySelector("#login-view");
const appView = document.querySelector("#app-view");
const loginForm = document.querySelector("#login-form");
const loginError = document.querySelector("#login-error");
const sectionTitle = document.querySelector("#section-title");
const sessionSummary = document.querySelector("#session-summary");
const globalMessage = document.querySelector("#global-message");
const deckForm = document.querySelector("#deck-form");
const cardForm = document.querySelector("#card-form");
const questionForm = document.querySelector("#question-form");
const settingsForm = document.querySelector("#settings-form");
const restoreBackupFileInput = document.querySelector("#restore-backup-file");
const restoreBackupConfirmInput = document.querySelector("#restore-backup-confirm");
const restoreBackupFileMeta = document.querySelector("#restore-backup-file-meta");
const restoreBackupButton = document.querySelector("#restore-backup-button");
const clearRestoreFileButton = document.querySelector("#clear-restore-file-button");
const exportBackupButton = document.querySelector("#export-backup-button");
const contentSelectionSummary = document.querySelector("#content-selection-summary");
const newCardButton = document.querySelector("#new-card-button");
const newQuestionButton = document.querySelector("#new-question-button");
const studySessionCard = document.querySelector("#study-session-card");
const studySessionTitle = document.querySelector("#study-session-title");
const studySessionSubtitle = document.querySelector("#study-session-subtitle");
const studySessionContent = document.querySelector("#study-session-content");
const startReviewButton = document.querySelector("#start-review-button");
const startPracticeButton = document.querySelector("#start-practice-button");
const endStudySessionButton = document.querySelector("#end-study-session-button");
const practiceOrderModeSelect = document.querySelector("#practice-order-mode");

document.querySelectorAll(".nav-button").forEach((button) => {
    button.addEventListener("click", () => switchSection(button.dataset.section));
});

document.querySelector("#logout-button").addEventListener("click", () => logout());
document.querySelector("#refresh-button").addEventListener("click", refreshAll);
document.querySelector("#new-deck-button").addEventListener("click", () => openDeckForm());
newCardButton.addEventListener("click", () => openCardForm());
newQuestionButton.addEventListener("click", () => openQuestionForm());
startReviewButton.addEventListener("click", startReviewSession);
startPracticeButton.addEventListener("click", startPracticeSession);
endStudySessionButton.addEventListener("click", endStudySession);
practiceOrderModeSelect.addEventListener("change", (event) => {
    state.practiceSelection.orderMode = event.currentTarget.value;
    renderPracticeSelectionSummary();
});
deckForm.addEventListener("submit", submitDeckForm);
cardForm.addEventListener("submit", submitCardForm);
questionForm.addEventListener("submit", submitQuestionForm);
document.querySelector("#search-form").addEventListener("submit", submitSearchForm);
settingsForm.addEventListener("submit", submitSettingsForm);
exportBackupButton.addEventListener("click", exportBackup);
restoreBackupFileInput.addEventListener("change", handleRestoreBackupFileChange);
restoreBackupConfirmInput.addEventListener("change", updateRestoreControls);
restoreBackupButton.addEventListener("click", restoreBackup);
clearRestoreFileButton.addEventListener("click", clearRestoreSelection);
document.querySelectorAll("[data-close-form]").forEach((button) => {
    button.addEventListener("click", () => closeForm(button.dataset.closeForm));
});

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    loginError.hidden = true;
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
});

bootstrap();
updateCommandAvailability();
renderPracticeSelection();
renderStudySession();
updateRestoreControls();

async function bootstrap() {
    const session = await getSession();
    if (!session) {
        loginView.hidden = false;
        appView.hidden = true;
        return;
    }
    loginView.hidden = true;
    appView.hidden = false;
    sessionSummary.textContent = `${session.displayName} · 端口 ${session.port} · 在线会话 ${session.activeSessionCount}`;
    await refreshAll();
}

async function getSession() {
    const response = await fetch("/api/web-console/v1/session", { credentials: "include" });
    if (!response.ok) {
        return null;
    }
    return response.json();
}

async function refreshAll() {
    await Promise.all([
        loadStudyWorkspace(),
        loadStudySession(),
        loadDashboard(),
        loadDecks(),
        loadAnalytics(),
        loadSettings(),
    ]);
    updateContentSelection();
    updateCommandAvailability();
}

async function loadDashboard() {
    const payload = await fetchJson("/api/web-console/v1/dashboard");
    if (!payload) return;
    document.querySelector("#overview-metrics").innerHTML = [
        metricCard("待复习卡片", payload.dueCardCount),
        metricCard("待复习问题", payload.dueQuestionCount),
        metricCard("最近卡组", payload.recentDecks.length),
    ].join("");
    const recentDecks = document.querySelector("#recent-decks");
    recentDecks.innerHTML = payload.recentDecks.length
        ? payload.recentDecks.map(renderDeckItem).join("")
        : renderEmptyState("最近暂无卡组", "先在内容管理中创建一个卡组，网页端和手机端会共享同一批数据。");
    bindDeckActions(recentDecks, payload.recentDecks);
}

async function loadStudyWorkspace() {
    const payload = await fetchJson("/api/web-console/v1/study/workspace");
    if (!payload) return;
    state.studyWorkspace = payload;
    renderStudyWorkspace();
}

async function loadStudySession() {
    const payload = await fetchOptionalJson("/api/web-console/v1/study/session");
    if (payload === undefined) return;
    state.studySession = payload;
    renderStudyWorkspace();
    renderStudySession();
}

async function loadDecks() {
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
        closeForm("card-form");
        closeForm("question-form");
    }
    renderPracticeSelection();
    updateContentSelection();
    updateCommandAvailability();
}

async function loadCards(deckId) {
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
        closeForm("question-form");
    }
    updateContentSelection();
    updateCommandAvailability();
}

async function loadQuestions(cardId) {
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

async function loadAnalytics() {
    const payload = await fetchJson("/api/web-console/v1/analytics");
    if (!payload) return;
    document.querySelector("#analytics-metrics").innerHTML = [
        metricCard("总复习次数", payload.totalReviews),
        metricCard("遗忘率", `${Math.round(payload.forgettingRate * 100)}%`),
        metricCard("平均耗时", payload.averageResponseTimeMs ? `${Math.round(payload.averageResponseTimeMs)} ms` : "暂无"),
    ].join("");
    document.querySelector("#analytics-breakdowns").innerHTML = payload.deckBreakdowns.length
        ? payload.deckBreakdowns.map((item) => `
            <div class="item">
                <div class="item-head"><strong>${escapeHtml(item.deckName)}</strong><span class="muted">${item.reviewCount} 次</span></div>
                <div class="muted">遗忘率 ${Math.round(item.forgettingRate * 100)}% · 平均耗时 ${item.averageResponseTimeMs ? Math.round(item.averageResponseTimeMs) : "-"} ms</div>
            </div>
        `).join("")
        : renderEmptyState("暂无统计数据", "先完成几次复习，网页后台才会展示按卡组拆分的统计结果。");
}

async function loadSettings() {
    const payload = await fetchJson("/api/web-console/v1/settings");
    if (!payload) return;
    setFieldChecked(settingsForm, "dailyReminderEnabled", payload.dailyReminderEnabled);
    setFieldValue(settingsForm, "dailyReminderHour", String(payload.dailyReminderHour));
    setFieldValue(settingsForm, "dailyReminderMinute", String(payload.dailyReminderMinute));
    setFieldValue(settingsForm, "themeMode", payload.themeMode);
    document.querySelector("#settings-backup-time").textContent = payload.backupLastAt
        ? `最近备份：${formatDateTime(payload.backupLastAt)}`
        : "最近备份：暂无记录";
}

async function submitDeckForm(event) {
    event.preventDefault();
    const name = getFieldValue(deckForm, "name").trim();
    const intervalStepCount = Number(getFieldValue(deckForm, "intervalStepCount") || 4);
    if (!name) {
        showMessage("卡组名称不能为空。", true);
        return;
    }
    if (!Number.isInteger(intervalStepCount) || intervalStepCount < 2 || intervalStepCount > 12) {
        showMessage("间隔步数需为 2 到 12 之间的整数。", true);
        return;
    }
    const response = await postJson("/api/web-console/v1/decks/upsert", {
        id: getOptionalFieldValue(deckForm, "id"),
        name,
        description: getFieldValue(deckForm, "description").trim(),
        tags: splitTags(getFieldValue(deckForm, "tags")),
        intervalStepCount,
    });
    if (!response) return;
    showMessage(response.message);
    closeForm("deck-form");
    await loadDecks();
}

async function submitCardForm(event) {
    event.preventDefault();
    if (!state.selectedDeckId) {
        showMessage("请先选择卡组。", true);
        return;
    }
    const title = getFieldValue(cardForm, "title").trim();
    if (!title) {
        showMessage("卡片标题不能为空。", true);
        return;
    }
    const response = await postJson("/api/web-console/v1/cards/upsert", {
        id: getOptionalFieldValue(cardForm, "id"),
        deckId: state.selectedDeckId,
        title,
        description: getFieldValue(cardForm, "description").trim(),
    });
    if (!response) return;
    showMessage(response.message);
    closeForm("card-form");
    await loadCards(state.selectedDeckId);
    await loadStudyWorkspace();
}

async function submitQuestionForm(event) {
    event.preventDefault();
    if (!state.selectedCardId) {
        showMessage("请先选择卡片。", true);
        return;
    }
    const prompt = getFieldValue(questionForm, "prompt").trim();
    if (!prompt) {
        showMessage("问题题面不能为空。", true);
        return;
    }
    const response = await postJson("/api/web-console/v1/questions/upsert", {
        id: getOptionalFieldValue(questionForm, "id"),
        cardId: state.selectedCardId,
        prompt,
        answer: getFieldValue(questionForm, "answer").trim(),
        tags: splitTags(getFieldValue(questionForm, "tags")),
    });
    if (!response) return;
    showMessage(response.message);
    closeForm("question-form");
    await loadQuestions(state.selectedCardId);
    await loadStudyWorkspace();
}

async function submitSearchForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const keyword = getFieldValue(form, "keyword").trim();
    const tag = getFieldValue(form, "tag").trim();
    if (!keyword && !tag) {
        showMessage("至少输入关键词或标签中的一项再开始搜索。", true);
        return;
    }
    const payload = await postJson("/api/web-console/v1/search", {
        keyword,
        tag: tag || null,
    });
    if (!payload) return;
    document.querySelector("#search-results").innerHTML = payload.length
        ? payload.map((item) => `
            <div class="item">
                <div class="item-head"><strong>${escapeHtml(item.prompt)}</strong><span class="muted">${escapeHtml(item.deckName)} / ${escapeHtml(item.cardTitle)}</span></div>
                <div>${escapeHtml(item.answer)}</div>
                <div class="muted">阶段 ${item.stageIndex} · 复习 ${item.reviewCount} 次 · lapse ${item.lapseCount} 次</div>
            </div>
        `).join("")
        : renderEmptyState("没有找到结果", "换一个关键词、标签，或先确认目标问题是否已经同步到当前设备。");
}

async function submitSettingsForm(event) {
    event.preventDefault();
    const dailyReminderHour = Number(getFieldValue(settingsForm, "dailyReminderHour") || 20);
    const dailyReminderMinute = Number(getFieldValue(settingsForm, "dailyReminderMinute") || 0);
    if (!Number.isInteger(dailyReminderHour) || dailyReminderHour < 0 || dailyReminderHour > 23) {
        showMessage("提醒小时需在 0 到 23 之间。", true);
        return;
    }
    if (!Number.isInteger(dailyReminderMinute) || dailyReminderMinute < 0 || dailyReminderMinute > 59) {
        showMessage("提醒分钟需在 0 到 59 之间。", true);
        return;
    }
    const response = await postJson("/api/web-console/v1/settings/update", {
        dailyReminderEnabled: getFieldChecked(settingsForm, "dailyReminderEnabled"),
        dailyReminderHour,
        dailyReminderMinute,
        themeMode: getFieldValue(settingsForm, "themeMode"),
    });
    if (!response) return;
    showMessage(response.message);
    await loadSettings();
}

async function exportBackup() {
    if (state.isExporting || state.isRestoring) {
        return;
    }
    state.isExporting = true;
    updateRestoreControls();
    const response = await fetch("/api/web-console/v1/backup/export", { credentials: "include" });
    state.isExporting = false;
    updateRestoreControls();
    if (!response.ok) {
        const errorText = await response.text();
        showMessage(errorText || "导出备份失败。", true);
        return;
    }
    const text = await response.text();
    const blob = new Blob([text], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = resolveDownloadFileName(response.headers.get("Content-Disposition"));
    link.click();
    URL.revokeObjectURL(link.href);
    showMessage("备份文件已开始下载。");
}

async function restoreBackup() {
    if (state.isRestoring || state.isExporting) {
        return;
    }
    if (!state.selectedBackupFile) {
        showMessage("请先选择要恢复的备份文件。", true);
        return;
    }
    if (!restoreBackupConfirmInput.checked) {
        showMessage("请先确认恢复会覆盖当前本地全部数据。", true);
        return;
    }
    if (!window.confirm(`确认从 ${state.selectedBackupFile.name} 恢复？当前本地数据将被覆盖且无法撤销。`)) {
        return;
    }
    state.isRestoring = true;
    updateRestoreControls();
    const content = await state.selectedBackupFile.text();
    const response = await postJson("/api/web-console/v1/backup/restore", {
        fileName: state.selectedBackupFile.name,
        content,
    });
    state.isRestoring = false;
    updateRestoreControls();
    if (!response) return;
    showMessage(response.message);
    clearRestoreSelection();
    await refreshAll();
    switchSection("overview");
}

async function logout(reason) {
    await fetch("/api/web-console/v1/auth/logout", { method: "POST", credentials: "include" });
    loginView.hidden = false;
    appView.hidden = true;
    state.studyWorkspace = null;
    state.studySession = null;
    if (reason) {
        showLoginError(reason);
    }
}

function switchSection(section) {
    state.currentSection = section;
    document.querySelectorAll(".nav-button").forEach((button) => {
        button.classList.toggle("is-active", button.dataset.section === section);
    });
    document.querySelectorAll(".section").forEach((node) => {
        node.classList.toggle("is-active", node.id === `section-${section}`);
    });
    sectionTitle.textContent = {
        study: "学习工作区",
        overview: "概览",
        content: "内容管理",
        search: "搜索",
        analytics: "统计",
        settings: "设置",
        backup: "备份",
    }[section];
}

function bindDeckActions(container, decks) {
    container.querySelectorAll("[data-deck-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            const deck = decks.find((item) => item.id === button.dataset.deckId);
            if (!deck) return;
            state.selectedDeckId = deck.id;
            state.selectedCardId = null;
            openDeckForm(deck);
            await loadCards(deck.id);
        });
    });
    container.querySelectorAll("[data-deck-archive]").forEach((button) => {
        button.addEventListener("click", async () => {
            const deck = decks.find((item) => item.id === button.dataset.deckId);
            if (!deck) return;
            const archived = button.dataset.deckArchive === "true";
            if (!confirmDanger(`确认要${archived ? "归档" : "恢复"}卡组“${deck.name}”吗？`)) {
                return;
            }
            const response = await postJson("/api/web-console/v1/decks/archive", { id: button.dataset.deckId, archived });
            if (!response) return;
            showMessage(response.message);
            await loadDecks();
        });
    });
}

function bindCardActions(container, cards) {
    container.querySelectorAll("[data-card-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            const card = cards.find((item) => item.id === button.dataset.cardId);
            if (!card) return;
            state.selectedCardId = card.id;
            openCardForm(card);
            await loadQuestions(card.id);
        });
    });
    container.querySelectorAll("[data-card-archive]").forEach((button) => {
        button.addEventListener("click", async () => {
            const card = cards.find((item) => item.id === button.dataset.cardId);
            if (!card) return;
            const archived = button.dataset.cardArchive === "true";
            if (!confirmDanger(`确认要${archived ? "归档" : "恢复"}卡片“${card.title}”吗？`)) {
                return;
            }
            const response = await postJson("/api/web-console/v1/cards/archive", { id: button.dataset.cardId, archived });
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
            const question = questions.find((item) => item.id === button.dataset.questionId);
            if (!question) return;
            openQuestionForm(question);
        });
    });
    container.querySelectorAll("[data-question-delete]").forEach((button) => {
        button.addEventListener("click", async () => {
            const question = questions.find((item) => item.id === button.dataset.questionDelete);
            if (!question) return;
            if (!confirmDanger(`确认删除问题“${question.prompt}”吗？`)) {
                return;
            }
            const response = await postJson("/api/web-console/v1/questions/delete", { id: button.dataset.questionDelete });
            if (!response) return;
            showMessage(response.message);
            await loadQuestions(state.selectedCardId);
            await loadStudyWorkspace();
        });
    });
}

function openDeckForm(deck) {
    document.querySelector("#deck-form-title").textContent = deck ? "编辑卡组" : "新建卡组";
    deckForm.hidden = false;
    setFieldValue(deckForm, "id", deck?.id ?? "");
    setFieldValue(deckForm, "name", deck?.name ?? "");
    setFieldValue(deckForm, "description", deck?.description ?? "");
    setFieldValue(deckForm, "tags", deck?.tags?.join(", ") ?? "");
    setFieldValue(deckForm, "intervalStepCount", String(deck?.intervalStepCount ?? 4));
}

function openCardForm(card) {
    if (!state.selectedDeckId) {
        showMessage("请先选择卡组，再创建卡片。", true);
        return;
    }
    document.querySelector("#card-form-title").textContent = card ? "编辑卡片" : "新建卡片";
    cardForm.hidden = false;
    setFieldValue(cardForm, "id", card?.id ?? "");
    setFieldValue(cardForm, "title", card?.title ?? "");
    setFieldValue(cardForm, "description", card?.description ?? "");
}

function openQuestionForm(question) {
    if (!state.selectedCardId) {
        showMessage("请先选择卡片，再创建问题。", true);
        return;
    }
    document.querySelector("#question-form-title").textContent = question ? "编辑问题" : "新建问题";
    questionForm.hidden = false;
    setFieldValue(questionForm, "id", question?.id ?? "");
    setFieldValue(questionForm, "prompt", question?.prompt ?? "");
    setFieldValue(questionForm, "answer", question?.answer ?? "");
    setFieldValue(questionForm, "tags", question?.tags?.join(", ") ?? "");
}

function closeForm(formId) {
    const form = document.querySelector(`#${formId}`);
    if (!form) return;
    form.hidden = true;
    form.reset();
    if (formId === "deck-form") {
        setFieldValue(deckForm, "intervalStepCount", "4");
        document.querySelector("#deck-form-title").textContent = "新建卡组";
    }
    if (formId === "card-form") {
        document.querySelector("#card-form-title").textContent = "新建卡片";
    }
    if (formId === "question-form") {
        document.querySelector("#question-form-title").textContent = "新建问题";
    }
}

function updateContentSelection() {
    const selectedDeck = state.decks.find((item) => item.id === state.selectedDeckId);
    const selectedCard = state.cards.find((item) => item.id === state.selectedCardId);
    contentSelectionSummary.innerHTML = `
        <strong>当前上下文</strong>
        <span class="muted">卡组：${escapeHtml(selectedDeck?.name ?? "未选择")} · 卡片：${escapeHtml(selectedCard?.title ?? "未选择")}</span>
    `;
}

function updateCommandAvailability() {
    newCardButton.disabled = !state.selectedDeckId;
    newQuestionButton.disabled = !state.selectedCardId;
}

function handleRestoreBackupFileChange(event) {
    const file = event.currentTarget.files?.[0] ?? null;
    state.selectedBackupFile = file;
    if (file) {
        restoreBackupFileMeta.textContent = `已选择：${file.name} · ${formatFileSize(file.size)}`;
    } else {
        restoreBackupFileMeta.textContent = "尚未选择备份文件。";
    }
    updateRestoreControls();
}

function clearRestoreSelection() {
    state.selectedBackupFile = null;
    restoreBackupFileInput.value = "";
    restoreBackupConfirmInput.checked = false;
    restoreBackupFileMeta.textContent = "尚未选择备份文件。";
    updateRestoreControls();
}

function updateRestoreControls() {
    exportBackupButton.disabled = state.isExporting || state.isRestoring;
    restoreBackupButton.disabled = !state.selectedBackupFile || !restoreBackupConfirmInput.checked || state.isExporting || state.isRestoring;
    clearRestoreFileButton.disabled = !state.selectedBackupFile || state.isRestoring;
    exportBackupButton.textContent = state.isExporting ? "导出中…" : "导出备份 JSON";
    restoreBackupButton.textContent = state.isRestoring ? "恢复中…" : "确认恢复";
}

async function fetchJson(url) {
    const response = await fetch(url, { credentials: "include" });
    if (!response.ok) {
        if (response.status === 401) {
            await logout("登录已失效，请重新输入手机上最新的访问码。");
            return null;
        }
        const errorText = await response.text();
        showMessage(errorText || "请求失败，请稍后重试。", true);
        return null;
    }
    return response.json();
}

async function fetchOptionalJson(url) {
    const response = await fetch(url, { credentials: "include" });
    if (response.status === 204) {
        return null;
    }
    if (!response.ok) {
        if (response.status === 401) {
            await logout("登录已失效，请重新输入手机上最新的访问码。");
            return undefined;
        }
        const errorText = await response.text();
        showMessage(errorText || "请求失败，请稍后重试。", true);
        return undefined;
    }
    return response.json();
}

async function postJson(url, payload) {
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(payload ?? {}),
    });
    if (!response.ok) {
        if (response.status === 401) {
            await logout("登录已失效，请重新输入手机上最新的访问码。");
            return null;
        }
        const errorText = await response.text();
        showMessage(errorText || "操作失败。", true);
        return null;
    }
    if (response.status === 204) {
        return {};
    }
    return response.json();
}

function metricCard(label, value) {
    return `<article class="metric"><span class="muted">${label}</span><strong>${value}</strong></article>`;
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
                <button type="button" data-deck-id="${item.id}" data-deck-archive="${item.archived ? "false" : "true"}">${item.archived ? "恢复" : "归档"}</button>
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
                <button type="button" data-card-id="${item.id}" data-card-archive="${item.archived ? "false" : "true"}">${item.archived ? "恢复" : "归档"}</button>
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

function renderEmptyState(title, description) {
    return `
        <div class="empty-state-block">
            <strong>${escapeHtml(title)}</strong>
            <span class="muted">${escapeHtml(description)}</span>
        </div>
    `;
}

function getFieldValue(form, name) {
    return getFormField(form, name).value;
}

function getOptionalFieldValue(form, name) {
    const value = getFieldValue(form, name).trim();
    return value || null;
}

function getFieldChecked(form, name) {
    return Boolean(getFormField(form, name).checked);
}

function setFieldValue(form, name, value) {
    getFormField(form, name).value = value;
}

function setFieldChecked(form, name, checked) {
    getFormField(form, name).checked = checked;
}

function getFormField(form, name) {
    const field = form.elements.namedItem(name);
    if (!field) {
        throw new Error(`表单字段不存在：${name}`);
    }
    return field;
}

function splitTags(value) {
    return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function resolveDownloadFileName(contentDisposition) {
    const matchedFileName = contentDisposition?.match(/filename="([^"]+)"/i)?.[1];
    return matchedFileName || `yike-backup-${Date.now()}.json`;
}

function confirmDanger(message) {
    return window.confirm(message);
}

function formatDateTime(epochMillis) {
    return new Date(epochMillis).toLocaleString("zh-CN");
}

function formatFileSize(size) {
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

function showLoginError(message) {
    loginError.hidden = false;
    loginError.textContent = message;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function showMessage(message, isError = false) {
    globalMessage.hidden = false;
    globalMessage.textContent = message;
    globalMessage.style.background = isError ? "rgba(180, 67, 53, 0.12)" : "rgba(11, 111, 105, 0.12)";
    globalMessage.style.color = isError ? "#b44335" : "#0b6f69";
    window.clearTimeout(showMessage.timer);
    showMessage.timer = window.setTimeout(() => {
        globalMessage.hidden = true;
    }, 3200);
}

async function startReviewSession() {
    const allowed = await ensureStudySessionSwitchAllowed("review");
    if (!allowed) return;
    const payload = await postJson("/api/web-console/v1/study/review/start", {});
    if (!payload) return;
    state.studySession = payload;
    showMessage("已进入今日复习。");
    renderStudySession();
    await loadStudyWorkspace();
}

async function startPracticeSession() {
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
    renderStudySession();
    await loadStudyWorkspace();
}

async function revealStudyAnswer() {
    const payload = await postJson("/api/web-console/v1/study/answer/reveal", {});
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
}

async function submitReviewRating(rating) {
    const payload = await postJson("/api/web-console/v1/study/review/rate", { rating });
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
    await loadStudyWorkspace();
}

async function continueReviewSession() {
    const payload = await postJson("/api/web-console/v1/study/review/next-card", {});
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
    await loadStudyWorkspace();
}

async function navigatePracticeSession(action) {
    const payload = await postJson("/api/web-console/v1/study/practice/navigate", { action });
    if (!payload) return;
    state.studySession = payload;
    renderStudySession();
}

async function endStudySession() {
    if (state.studySession && !confirmDanger(buildStudyExitConfirmMessage(state.studySession))) {
        return;
    }
    const response = await postJson("/api/web-console/v1/study/session/end", {});
    if (!response) return;
    state.studySession = null;
    renderStudySession();
    await loadStudyWorkspace();
    showMessage(response.message);
}

function renderStudyWorkspace() {
    const payload = state.studyWorkspace;
    document.querySelector("#study-overview-metrics").innerHTML = [
        metricCard("今日待复习卡片", payload?.dueCardCount ?? "—"),
        metricCard("今日待复习问题", payload?.dueQuestionCount ?? "—"),
        metricCard("当前会话", state.studySession ? (state.studySession.type === "review" ? "复习中" : "练习中") : "空闲"),
    ].join("");

    const reviewSummary = document.querySelector("#review-workspace-summary");
    const practiceSummary = document.querySelector("#practice-workspace-summary");
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

    renderPracticeSelectionSummary();
}

function renderStudySession() {
    if (!state.studySession) {
        studySessionCard.hidden = true;
        studySessionContent.innerHTML = "";
        return;
    }
    studySessionCard.hidden = false;
    studySessionTitle.textContent = state.studySession.title;
    studySessionSubtitle.textContent = state.studySession.summary;
    studySessionContent.innerHTML = state.studySession.type === "review"
        ? renderReviewStudySession(state.studySession.review)
        : renderPracticeStudySession(state.studySession.practice);
    bindStudySessionActions();
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

function bindStudySessionActions() {
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

function renderPracticeSelection() {
    renderPracticeDeckOptions();
    renderPracticeCardOptions();
    renderPracticeQuestionOptions();
    renderPracticeSelectionSummary();
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

function renderPracticeSelectionSummary() {
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

async function ensureStudySessionSwitchAllowed(targetType) {
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
    renderStudySession();
    return true;
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

function collectPracticeCards() {
    return [...state.practiceSelection.cardsByDeckId.values()].flat();
}

function collectPracticeQuestions() {
    return [...state.practiceSelection.questionsByCardId.values()].flat();
}

function prunePracticeDeckSelection() {
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
