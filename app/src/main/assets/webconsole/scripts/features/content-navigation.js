import { replaceCurrentQuery } from "../shared/navigation.js";
import { state } from "../shared/core.js";

/**
 * 内容页 URL 与下钻上下文集中同步，是为了让刷新和回退后仍能回到同一层级。
 */
export function syncContentQuery() {
    replaceCurrentQuery({
        deckId: state.selectedDeckId,
        cardId: state.selectedCardId,
        questionId: state.selectedQuestionId,
    });
}

/**
 * 练习入口文案集中在辅助模块，是为了让工作台主文件继续把体量留给 drill-down 行为本身。
 */
export function resolvePracticeLaunchLabel(action, deck, card, question) {
    if (action === "practice-question" && question) {
        return `内容工作台 / ${question.prompt}`;
    }
    if (action === "practice-card" && card) {
        return `内容工作台 / ${card.title}`;
    }
    return `内容工作台 / ${deck.name}`;
}
