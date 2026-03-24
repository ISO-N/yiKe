import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const webConsoleRoot = path.join(repoRoot, "app", "src", "main", "assets", "webconsole");
const scanRoots = [
    path.join(webConsoleRoot, "entries"),
    path.join(webConsoleRoot, "scripts"),
];

/**
 * 主流程统一检查网页后台相对导入，是为了让“导入不存在的导出”这类浏览器级故障在构建前就暴露。
 */
async function main() {
    const files = await collectJavaScriptFiles(scanRoots);
    const exportMap = new Map();
    for (const filePath of files) {
        exportMap.set(filePath, await collectExports(filePath));
    }
    const problems = [];
    for (const filePath of files) {
        const source = await readFile(filePath, "utf8");
        for (const statement of matchImportStatements(source)) {
            const resolvedPath = resolveImportPath(filePath, statement.specifier);
            if (!resolvedPath || !exportMap.has(resolvedPath)) {
                continue;
            }
            const availableExports = exportMap.get(resolvedPath);
            for (const importedName of statement.namedImports) {
                if (!availableExports.has(importedName)) {
                    problems.push(
                        `${path.relative(repoRoot, filePath)} 导入 ${importedName}，但 ${path.relative(repoRoot, resolvedPath)} 没有导出它。`
                    );
                }
            }
        }
    }
    if (problems.length) {
        throw new Error(problems.join("\n"));
    }
    console.log("WebConsole relative imports are valid.");
}

/**
 * 递归收集 JS 文件，是为了让入口模板和脚本层都走同一套导入契约检查。
 */
async function collectJavaScriptFiles(roots) {
    const files = [];
    for (const root of roots) {
        const entries = await readdir(root, { withFileTypes: true });
        for (const entry of entries) {
            const absolutePath = path.join(root, entry.name);
            if (entry.isDirectory()) {
                files.push(...await collectJavaScriptFiles([absolutePath]));
                continue;
            }
            if (entry.isFile() && absolutePath.endsWith(".js")) {
                files.push(absolutePath);
            }
        }
    }
    return files;
}

/**
 * 导出名集中解析，是为了让校验脚本只关注浏览器模块图需要的命名导出契约。
 */
async function collectExports(filePath) {
    const source = await readFile(filePath, "utf8");
    const exports = new Set();
    for (const match of source.matchAll(/export\s+(?:async\s+)?(?:function|const|let|class)\s+([A-Za-z0-9_$]+)/g)) {
        exports.add(match[1]);
    }
    for (const match of source.matchAll(/export\s*\{([^}]+)\}(?:\s*from\s*["'][^"']+["'])?/g)) {
        const names = match[1]
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean)
            .map((item) => item.split(/\s+as\s+/i).pop()?.trim())
            .filter(Boolean);
        names.forEach((name) => exports.add(name));
    }
    return exports;
}

/**
 * import 语句集中抽取，是为了让多行命名导入和简单默认导入都能被同一正则遍历到。
 */
function matchImportStatements(source) {
    const statements = [];
    for (const match of source.matchAll(/import\s*\{([^}]+)\}\s*from\s*["']([^"']+)["']/gms)) {
        statements.push({
            namedImports: match[1]
                .split(",")
                .map((item) => item.trim())
                .filter(Boolean)
                .map((item) => item.split(/\s+as\s+/i)[0].trim()),
            specifier: match[2],
        });
    }
    return statements;
}

/**
 * 相对导入路径解析在单点完成，是为了让 Windows 路径和浏览器模块路径仍按同一文件系统语义比对。
 */
function resolveImportPath(importerPath, specifier) {
    if (!specifier.startsWith(".")) {
        return null;
    }
    return fileURLToPath(new URL(specifier, pathToFileURL(importerPath)));
}

main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
});
