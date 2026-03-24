import { initializeAppShell } from "./scripts/shell/app-shell.js";

/**
 * 应用入口只保留壳层启动，是为了让网页后台的结构边界回到“shell + features + shared”的稳定分层。
 */
initializeAppShell();
