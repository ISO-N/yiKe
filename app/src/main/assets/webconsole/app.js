const state = {
    currentSection: "overview",
    selectedDeckId: null,
    selectedCardId: null,
    decks: [],
    cards: [],
    questions: [],
};

const loginView = document.querySelector("#login-view");
const appView = document.querySelector("#app-view");
const loginForm = document.querySelector("#login-form");
const loginError = document.querySelector("#login-error");
const sectionTitle = document.querySelector("#section-title");
const sessionSummary = document.querySelector("#session-summary");
const globalMessage = document.querySelector("#global-message");

document.querySelectorAll(".nav-button").forEach((button) => {
    button.addEventListener("click", () => switchSection(button.dataset.section));
});

document.querySelector("#logout-button").addEventListener("click", logout);
document.querySelector("#refresh-button").addEventListener("click", refreshAll);
document.querySelector("#new-deck-button").addEventListener("click", () => openDeckForm());
document.querySelector("#new-card-button").addEventListener("click", () => openCardForm());
document.querySelector("#new-question-button").addEventListener("click", () => openQuestionForm());
document.querySelector("#deck-form").addEventListener("submit", submitDeckForm);
document.querySelector("#card-form").addEventListener("submit", submitCardForm);
document.querySelector("#question-form").addEventListener("submit", submitQuestionForm);
document.querySelector("#search-form").addEventListener("submit", submitSearchForm);
document.querySelector("#settings-form").addEventListener("submit", submitSettingsForm);
document.querySelector("#export-backup-button").addEventListener("click", exportBackup);

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    loginError.hidden = true;
    const code = document.querySelector("#access-code").value.trim();
    const response = await fetch("/api/web-console/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ code }),
    });
    if (!response.ok) {
        loginError.hidden = false;
        loginError.textContent = "访问码不正确，或服务未处于可登录状态。";
        return;
    }
    document.querySelector("#access-code").value = "";
    await bootstrap();
});

bootstrap();

async function bootstrap() {
    const session = await getSession();
    if (!session) {
        loginView.hidden = false;
        appView.hidden = true;
        return;
    }
    loginView.hidden = true;
    appView.hidden = false;
    sessionSummary.textContent = `端口 ${session.port} · 在线会话 ${session.activeSessionCount}`;
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
        loadDashboard(),
        loadDecks(),
        loadAnalytics(),
        loadSettings(),
    ]);
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
    recentDecks.innerHTML = payload.recentDecks.map(renderDeckItem).join("");
    bindDeckActions(recentDecks, payload.recentDecks);
}

async function loadDecks() {
    const payload = await fetchJson("/api/web-console/v1/decks");
    if (!payload) return;
    state.decks = payload;
    const list = document.querySelector("#deck-list");
    list.innerHTML = payload.length ? payload.map(renderDeckItem).join("") : `<div class="empty-state">还没有卡组。</div>`;
    bindDeckActions(list, payload);
    if (!payload.some((item) => item.id === state.selectedDeckId)) {
        state.selectedDeckId = payload[0]?.id ?? null;
    }
    if (state.selectedDeckId && !payload[0]) {
        state.selectedCardId = null;
    }
    if (!state.selectedDeckId && payload[0]) {
        state.selectedDeckId = payload[0].id;
    }
    if (state.selectedDeckId) {
        await loadCards(state.selectedDeckId);
    } else {
        document.querySelector("#card-list").innerHTML = `<div class="empty-state">先创建一个卡组。</div>`;
        document.querySelector("#question-list").innerHTML = `<div class="empty-state">先选择一个卡片。</div>`;
    }
}

async function loadCards(deckId) {
    const payload = await fetchJson(`/api/web-console/v1/cards?deckId=${encodeURIComponent(deckId)}`);
    if (!payload) return;
    state.cards = payload;
    const list = document.querySelector("#card-list");
    list.classList.remove("empty-state");
    list.innerHTML = payload.length ? payload.map(renderCardItem).join("") : `<div class="empty-state">这个卡组还没有卡片。</div>`;
    bindCardActions(list, payload);
    if (!payload.some((item) => item.id === state.selectedCardId)) {
        state.selectedCardId = payload[0]?.id ?? null;
    }
    if (!state.selectedCardId && payload[0]) {
        state.selectedCardId = payload[0].id;
    }
    if (state.selectedCardId) {
        await loadQuestions(state.selectedCardId);
    } else {
        document.querySelector("#question-list").innerHTML = `<div class="empty-state">这个卡组还没有卡片。</div>`;
    }
}

async function loadQuestions(cardId) {
    const payload = await fetchJson(`/api/web-console/v1/questions?cardId=${encodeURIComponent(cardId)}`);
    if (!payload) return;
    state.questions = payload;
    const list = document.querySelector("#question-list");
    list.classList.remove("empty-state");
    list.innerHTML = payload.length ? payload.map(renderQuestionItem).join("") : `<div class="empty-state">这个卡片还没有问题。</div>`;
    bindQuestionActions(list, payload);
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
        : `<div class="empty-state">暂无统计数据。</div>`;
}

async function loadSettings() {
    const payload = await fetchJson("/api/web-console/v1/settings");
    if (!payload) return;
    const form = document.querySelector("#settings-form");
    form.dailyReminderEnabled.checked = payload.dailyReminderEnabled;
    form.dailyReminderHour.value = payload.dailyReminderHour;
    form.dailyReminderMinute.value = payload.dailyReminderMinute;
    form.themeMode.value = payload.themeMode;
    document.querySelector("#settings-backup-time").textContent = payload.backupLastAt
        ? `最近备份：${new Date(payload.backupLastAt).toLocaleString("zh-CN")}`
        : "最近备份：暂无记录";
}

async function submitDeckForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = {
        id: form.id.value || null,
        name: form.name.value,
        description: form.description.value,
        tags: splitTags(form.tags.value),
        intervalStepCount: Number(form.intervalStepCount.value || 4),
    };
    const response = await postJson("/api/web-console/v1/decks/upsert", payload);
    if (!response) return;
    showMessage(response.message);
    form.hidden = true;
    await loadDecks();
}

async function submitCardForm(event) {
    event.preventDefault();
    if (!state.selectedDeckId) {
        showMessage("请先选择卡组。", true);
        return;
    }
    const form = event.currentTarget;
    const response = await postJson("/api/web-console/v1/cards/upsert", {
        id: form.id.value || null,
        deckId: state.selectedDeckId,
        title: form.title.value,
        description: form.description.value,
    });
    if (!response) return;
    showMessage(response.message);
    form.hidden = true;
    await loadCards(state.selectedDeckId);
}

async function submitQuestionForm(event) {
    event.preventDefault();
    if (!state.selectedCardId) {
        showMessage("请先选择卡片。", true);
        return;
    }
    const form = event.currentTarget;
    const response = await postJson("/api/web-console/v1/questions/upsert", {
        id: form.id.value || null,
        cardId: state.selectedCardId,
        prompt: form.prompt.value,
        answer: form.answer.value,
        tags: splitTags(form.tags.value),
    });
    if (!response) return;
    showMessage(response.message);
    form.hidden = true;
    await loadQuestions(state.selectedCardId);
}

async function submitSearchForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = await postJson("/api/web-console/v1/search", {
        keyword: form.keyword.value,
        tag: form.tag.value || null,
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
        : `<div class="empty-state">没有找到结果。</div>`;
}

async function submitSettingsForm(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const response = await postJson("/api/web-console/v1/settings/update", {
        dailyReminderEnabled: form.dailyReminderEnabled.checked,
        dailyReminderHour: Number(form.dailyReminderHour.value || 20),
        dailyReminderMinute: Number(form.dailyReminderMinute.value || 0),
        themeMode: form.themeMode.value,
    });
    if (!response) return;
    showMessage(response.message);
    await loadSettings();
}

async function exportBackup() {
    const response = await fetch("/api/web-console/v1/backup/export", { credentials: "include" });
    if (!response.ok) {
        showMessage("导出备份失败。", true);
        return;
    }
    const text = await response.text();
    const blob = new Blob([text], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `yike-backup-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
}

async function logout() {
    await fetch("/api/web-console/v1/auth/logout", { method: "POST", credentials: "include" });
    loginView.hidden = false;
    appView.hidden = true;
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
            state.selectedDeckId = deck.id;
            state.selectedCardId = null;
            openDeckForm(deck);
            await loadCards(deck.id);
        });
    });
    container.querySelectorAll("[data-deck-archive]").forEach((button) => {
        button.addEventListener("click", async () => {
            const archived = button.dataset.deckArchive === "true";
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
            state.selectedCardId = card.id;
            openCardForm(card);
            await loadQuestions(card.id);
        });
    });
    container.querySelectorAll("[data-card-archive]").forEach((button) => {
        button.addEventListener("click", async () => {
            const archived = button.dataset.cardArchive === "true";
            const response = await postJson("/api/web-console/v1/cards/archive", { id: button.dataset.cardId, archived });
            if (!response) return;
            showMessage(response.message);
            await loadCards(state.selectedDeckId);
        });
    });
}

function bindQuestionActions(container, questions) {
    container.querySelectorAll("[data-question-id]").forEach((button) => {
        button.addEventListener("click", () => {
            const question = questions.find((item) => item.id === button.dataset.questionId);
            openQuestionForm(question);
        });
    });
    container.querySelectorAll("[data-question-delete]").forEach((button) => {
        button.addEventListener("click", async () => {
            const response = await postJson("/api/web-console/v1/questions/delete", { id: button.dataset.questionDelete });
            if (!response) return;
            showMessage(response.message);
            await loadQuestions(state.selectedCardId);
        });
    });
}

function openDeckForm(deck) {
    const form = document.querySelector("#deck-form");
    form.hidden = false;
    form.id.value = deck?.id ?? "";
    form.name.value = deck?.name ?? "";
    form.description.value = deck?.description ?? "";
    form.tags.value = deck?.tags?.join(", ") ?? "";
    form.intervalStepCount.value = deck?.intervalStepCount ?? 4;
}

function openCardForm(card) {
    const form = document.querySelector("#card-form");
    form.hidden = false;
    form.id.value = card?.id ?? "";
    form.title.value = card?.title ?? "";
    form.description.value = card?.description ?? "";
}

function openQuestionForm(question) {
    const form = document.querySelector("#question-form");
    form.hidden = false;
    form.id.value = question?.id ?? "";
    form.prompt.value = question?.prompt ?? "";
    form.answer.value = question?.answer ?? "";
    form.tags.value = question?.tags?.join(", ") ?? "";
}

async function fetchJson(url) {
    const response = await fetch(url, { credentials: "include" });
    if (!response.ok) {
        if (response.status === 401) {
            await logout();
            return null;
        }
        showMessage("请求失败，请稍后重试。", true);
        return null;
    }
    return response.json();
}

async function postJson(url, payload) {
    const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
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
            <div>${escapeHtml(item.answer)}</div>
            <div class="item-actions">
                <button type="button" data-question-id="${item.id}">编辑</button>
                <button type="button" data-question-delete="${item.id}">删除</button>
            </div>
        </div>
    `;
}

function splitTags(value) {
    return value.split(",").map((item) => item.trim()).filter(Boolean);
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
    }, 2600);
}
