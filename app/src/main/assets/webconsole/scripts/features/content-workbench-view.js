import { escapeHtml, formatDateTime } from "../shared/core.js";

/**
 * 工作台详情渲染拆到独立模块，是为了让内容工作区主文件继续聚焦数据流和动作绑定，而不是同时承载大段 HTML 拼装。
 */
export function renderDeckDetails(deck) {
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
 * 卡片详情单独拼装，是为了让卡片上下文语义与工作台主模块的动作绑定解耦。
 */
export function renderCardDetails(card) {
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
 * 问题详情提炼到独立文件，是为了让题面、答案和题级动作的模板不会继续膨胀主工作区脚本。
 */
export function renderQuestionDetails(question) {
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
