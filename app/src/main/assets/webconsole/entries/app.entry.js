import { initializeAppShell } from "./scripts/shell/app-shell.js";

/**
 * 构建入口仍只暴露壳层初始化，是为了让生成后的 assets 入口继续锁定在稳定的 shell 边界上。
 */
initializeAppShell();
