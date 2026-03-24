const APP_PAGE_PATHS = {
    overview: "/overview",
    study: "/study",
    content: "/content",
    search: "/search",
    analytics: "/analytics",
    settings: "/settings",
    backup: "/backup",
};

const FRIENDLY_ROUTE_PREFIX = "friendly:";
const RETURN_CONTEXT_KEY = "yike:web:return-context";

/**
 * 路由表集中维护，是为了让多页面壳层、登录跳转和来源恢复围绕同一份页面约定演进。
 */
export const appPagePaths = APP_PAGE_PATHS;

/**
 * 当前路径解析成页面 key 集中处理，是为了让壳层不必在多处分散维护路径分支。
 */
export function resolveCurrentAppPage() {
    const pathName = window.location.pathname.replace(/\/+$/, "") || "/";
    return Object.entries(APP_PAGE_PATHS).find(([, path]) => path === pathName)?.[0] ?? null;
}

/**
 * 页面跳转统一经由这里，是为了让多页面导航、登录后回跳和来源恢复共用同一条 URL 规则。
 */
export function navigateToAppPage(page, { replace = false, query = null } = {}) {
    const path = APP_PAGE_PATHS[page] ?? APP_PAGE_PATHS.study;
    const targetUrl = new URL(path, window.location.origin);
    if (query) {
        applyQueryParams(targetUrl.searchParams, query);
    }
    const target = `${targetUrl.pathname}${targetUrl.search}`;
    if (replace) {
        window.location.replace(target);
        return;
    }
    window.location.assign(target);
}

/**
 * 登录页跳转统一附带 next，是为了让会话失效后还能稳定回到原始目标页面。
 */
export function redirectToLogin({ expired = false } = {}) {
    const loginUrl = new URL("/login", window.location.origin);
    loginUrl.searchParams.set("next", `${window.location.pathname}${window.location.search}`);
    if (expired) {
        loginUrl.searchParams.set("expired", "1");
    }
    window.location.replace(`${loginUrl.pathname}${loginUrl.search}`);
}

/**
 * 访问码登录后的目标页集中解析，是为了让登录页只需要处理稳定的安全回跳规则。
 */
export function resolveNextPagePath() {
    const nextPath = new URLSearchParams(window.location.search).get("next");
    return isSafeInternalPath(nextPath) ? nextPath : APP_PAGE_PATHS.study;
}

/**
 * 当前页面查询参数更新统一封装，是为了让内容上下文与搜索条件能在刷新后稳定恢复。
 */
export function replaceCurrentQuery(query) {
    const targetUrl = new URL(window.location.href);
    targetUrl.search = "";
    applyQueryParams(targetUrl.searchParams, query);
    window.history.replaceState({}, "", `${targetUrl.pathname}${targetUrl.search}`);
}

/**
 * 当前查询参数读取单独暴露，是为了让各工作区按需恢复自己的 URL 上下文。
 */
export function getQueryParam(name) {
    const value = new URLSearchParams(window.location.search).get(name);
    return normalizeNullableQueryValue(value);
}

/**
 * 来源页面上下文写入 sessionStorage，是为了让跨页发起练习后仍能返回原始工作区。
 */
export function saveReturnContext(context) {
    if (!context?.path || !isSafeInternalPath(context.path)) {
        return;
    }
    window.sessionStorage.setItem(
        RETURN_CONTEXT_KEY,
        JSON.stringify({
            path: context.path,
            label: context.label || "原始工作区",
        })
    );
}

/**
 * 来源页面上下文读取与清理分开暴露，是为了让学习页既能展示返回入口，也能在结束时消费它。
 */
export function readReturnContext() {
    const raw = window.sessionStorage.getItem(RETURN_CONTEXT_KEY);
    if (!raw) {
        return null;
    }
    try {
        const parsed = JSON.parse(raw);
        return isSafeInternalPath(parsed?.path) ? parsed : null;
    } catch {
        return null;
    }
}

/**
 * 来源页面上下文消费后立即清空，是为了避免旧练习来源污染下一次学习流程。
 */
export function consumeReturnContext() {
    const context = readReturnContext();
    window.sessionStorage.removeItem(RETURN_CONTEXT_KEY);
    return context;
}

/**
 * 对内部友好路径做标准化，是为了让内容页和搜索页保存来源时不必自己拼接绝对地址。
 */
export function buildInternalPath(page, query = null) {
    const basePath = APP_PAGE_PATHS[page] ?? APP_PAGE_PATHS.study;
    const targetUrl = new URL(basePath, window.location.origin);
    applyQueryParams(targetUrl.searchParams, query);
    return `${FRIENDLY_ROUTE_PREFIX}${targetUrl.pathname}${targetUrl.search}`;
}

/**
 * 友好路径写入和读取统一剥离前缀，是为了避免外部输入借助 sessionStorage 混入不安全地址。
 */
export function normalizeInternalPath(value) {
    const rawValue = String(value ?? "");
    const candidate = rawValue.startsWith(FRIENDLY_ROUTE_PREFIX)
        ? rawValue.slice(FRIENDLY_ROUTE_PREFIX.length)
        : rawValue;
    return isSafeInternalPath(candidate) ? candidate : APP_PAGE_PATHS.study;
}

function applyQueryParams(searchParams, query) {
    Object.entries(query).forEach(([key, value]) => {
        if (value === null || value === undefined || value === "") {
            return;
        }
        searchParams.set(key, value);
    });
}

/**
 * 查询参数中的空值字符串统一回收成 null，是为了避免 `deckId=null` 这类无效状态破坏上下文恢复。
 */
function normalizeNullableQueryValue(value) {
    if (value === null) {
        return null;
    }
    const trimmedValue = String(value).trim();
    if (!trimmedValue || trimmedValue === "null" || trimmedValue === "undefined") {
        return null;
    }
    return trimmedValue;
}

function isSafeInternalPath(value) {
    return typeof value === "string"
        && value.startsWith("/")
        && !value.startsWith("//")
        && !value.startsWith("/api/");
}
