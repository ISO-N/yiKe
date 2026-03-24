/**
 * 页面模板集中在壳层侧维护，是为了让多页面路由仍复用同一套玻璃壳层而不把业务 DOM 分散到多个 HTML 入口。
 */
export function renderPageTemplate(page) {
    return PAGE_TEMPLATES[page]?.() ?? PAGE_TEMPLATES.study();
}

const PAGE_TEMPLATES = {
    overview: () => `
        <section class="page-section stack">
            <div id="overview-metrics" class="metric-grid"></div>
            <article class="card">
                <div class="card-head">
                    <h3>最近卡组</h3>
                    <span class="muted">和手机首页使用同一口径</span>
                </div>
                <div id="recent-decks" class="list-stack"></div>
            </article>
        </section>
    `,
    study: () => `
        <section class="page-section stack">
            <div id="study-overview-metrics" class="metric-grid"></div>
            <div class="study-grid">
                <article class="card stack">
                    <div class="card-head">
                        <div>
                            <h3>今日复习</h3>
                            <p class="muted">浏览器端沿用正式复习语义，会写入正式复习记录。</p>
                        </div>
                        <button id="start-review-button" class="primary-button" type="button">开始复习</button>
                    </div>
                    <div id="review-workspace-summary" class="study-summary-card">
                        正在读取今日待复习规模…
                    </div>
                </article>

                <article class="card stack">
                    <div class="card-head">
                        <div>
                            <h3>自由练习</h3>
                            <p class="muted">只做题面、答案与切题，不写入 \`ReviewRecord\`，也不改变调度状态。</p>
                        </div>
                        <button id="start-practice-button" class="primary-button" type="button">开始练习</button>
                    </div>
                    <div id="practice-workspace-summary" class="study-summary-card">
                        先选择练习范围，再决定顺序模式。
                    </div>
                    <div class="practice-config-grid">
                        <div class="practice-scope-panel">
                            <div class="item-head">
                                <strong>卡组范围</strong>
                                <span id="practice-deck-count" class="muted">0 已选</span>
                            </div>
                            <div id="practice-deck-options" class="option-list"></div>
                        </div>
                        <div class="practice-scope-panel">
                            <div class="item-head">
                                <strong>卡片范围</strong>
                                <span id="practice-card-count" class="muted">0 已选</span>
                            </div>
                            <div id="practice-card-options" class="option-list empty-state">先选择至少一个卡组。</div>
                        </div>
                        <div class="practice-scope-panel">
                            <div class="item-head">
                                <strong>题目范围</strong>
                                <span id="practice-question-count" class="muted">0 已选</span>
                            </div>
                            <div id="practice-question-options" class="option-list empty-state">先选择至少一个卡片。</div>
                        </div>
                    </div>
                    <div class="practice-footer">
                        <label>顺序模式
                            <select id="practice-order-mode">
                                <option value="sequential">稳定顺序</option>
                                <option value="random">单次随机</option>
                            </select>
                        </label>
                        <p id="practice-selection-summary" class="muted">当前尚未选择练习范围。</p>
                    </div>
                </article>
            </div>

            <article id="study-session-card" class="card stack" hidden>
                <div class="card-head">
                    <div>
                        <h3 id="study-session-title">当前学习会话</h3>
                        <p id="study-session-subtitle" class="muted">刷新后会自动恢复到最近一次有效学习上下文。</p>
                    </div>
                    <button id="end-study-session-button" class="ghost-button" type="button">结束当前会话</button>
                </div>
                <div id="study-session-content" class="study-session-body"></div>
            </article>
        </section>
    `,
    content: () => `
        <section class="page-section stack">
            <div id="content-selection-summary" class="card section-note">
                先从左侧选择卡组，再逐层浏览卡片和问题。
            </div>
            <div class="content-layout">
                <div class="content-grid">
                    <article class="card">
                        <div class="card-head">
                            <h3>卡组</h3>
                            <button id="new-deck-button" class="ghost-button" type="button">新建卡组</button>
                        </div>
                        <div id="deck-list" class="list-stack"></div>
                    </article>

                    <article class="card">
                        <div class="card-head">
                            <h3>卡片</h3>
                            <button id="new-card-button" class="ghost-button" type="button">新建卡片</button>
                        </div>
                        <div id="card-list" class="list-stack empty-state">先选择一个卡组。</div>
                    </article>

                    <article class="card">
                        <div class="card-head">
                            <h3>问题</h3>
                            <button id="new-question-button" class="ghost-button" type="button">新建问题</button>
                        </div>
                        <div id="question-list" class="list-stack empty-state">先选择一个卡片。</div>
                    </article>
                </div>

                <aside class="content-workbench">
                    <article class="card stack">
                        <div class="card-head">
                            <div>
                                <h3>当前工作台</h3>
                                <p class="muted">选中对象后，可在同一上下文中继续编辑、练习或下钻。</p>
                            </div>
                            <span class="muted">Drill-down</span>
                        </div>
                        <div class="content-context-panels">
                            <section id="content-deck-details" class="content-detail-panel">
                                <strong>等待选择卡组</strong>
                                <p class="muted">先在左侧确定一个卡组，再继续整理卡片与问题。</p>
                            </section>
                            <section id="content-card-details" class="content-detail-panel">
                                <strong>等待选择卡片</strong>
                                <p class="muted">卡片详情会跟随当前卡组自动同步。</p>
                            </section>
                            <section id="content-question-details" class="content-detail-panel">
                                <strong>等待选择问题</strong>
                                <p class="muted">选中题目后，这里会保留题面、答案和练习入口。</p>
                            </section>
                        </div>
                    </article>

                    <div class="editor-grid content-editor-stack">
                        <form id="deck-form" class="card stack" hidden>
                            <h3 id="deck-form-title">新建卡组</h3>
                            <input type="hidden" name="id">
                            <label>名称<input name="name" required></label>
                            <label>描述<textarea name="description" rows="3"></textarea></label>
                            <label>标签（用逗号分隔）<input name="tags"></label>
                            <label>间隔步数<input name="intervalStepCount" type="number" min="2" max="12" value="4"></label>
                            <p class="muted">间隔步数建议保持在 2 到 12 之间，便于和手机端复习节奏一致。</p>
                            <div class="form-actions">
                                <button type="submit" class="primary-button">保存卡组</button>
                                <button type="button" class="ghost-button" data-close-form="deck-form">取消</button>
                            </div>
                        </form>

                        <form id="card-form" class="card stack" hidden>
                            <h3 id="card-form-title">新建卡片</h3>
                            <input type="hidden" name="id">
                            <label>标题<input name="title" required></label>
                            <label>描述<textarea name="description" rows="3"></textarea></label>
                            <div class="form-actions">
                                <button type="submit" class="primary-button">保存卡片</button>
                                <button type="button" class="ghost-button" data-close-form="card-form">取消</button>
                            </div>
                        </form>

                        <form id="question-form" class="card stack" hidden>
                            <h3 id="question-form-title">新建问题</h3>
                            <input type="hidden" name="id">
                            <label>题面<textarea name="prompt" rows="3" required></textarea></label>
                            <label>答案<textarea name="answer" rows="5"></textarea></label>
                            <label>标签（用逗号分隔）<input name="tags"></label>
                            <div class="form-actions">
                                <button type="submit" class="primary-button">保存问题</button>
                                <button type="button" class="ghost-button" data-close-form="question-form">取消</button>
                            </div>
                        </form>
                    </div>
                </aside>
            </div>
        </section>
    `,
    search: () => `
        <section class="page-section stack">
            <form id="search-form" class="card search-form">
                <label>关键词<input name="keyword" placeholder="输入题面或答案关键词"></label>
                <label>标签<input name="tag" placeholder="可选"></label>
                <button type="submit" class="primary-button">开始搜索</button>
            </form>
            <div id="search-feedback" class="workspace-feedback is-empty">
                <strong>等待搜索条件</strong>
                <span class="muted">输入关键词或标签后，结果会在同一工作区内展开，并保留继续练习的入口。</span>
            </div>
            <div id="search-results" class="list-stack empty-state">输入关键词后开始搜索。</div>
        </section>
    `,
    analytics: () => `
        <section class="page-section stack">
            <div id="analytics-metrics" class="metric-grid"></div>
            <div id="analytics-feedback" class="workspace-feedback is-empty">
                <strong>等待统计数据</strong>
                <span class="muted">复习记录汇总完成后，这里会统一提示当前统计是否足够支撑分析。</span>
            </div>
            <article class="card">
                <div class="card-head">
                    <h3>卡组统计拆分</h3>
                    <span class="muted">用于定位最近最需要补课的主题</span>
                </div>
                <div id="analytics-breakdowns" class="list-stack"></div>
            </article>
        </section>
    `,
    settings: () => `
        <section class="page-section stack">
            <div id="settings-feedback" class="workspace-feedback is-empty">
                <strong>等待设置载入</strong>
                <span class="muted">提醒和主题配置读取完成后，这里会给出当前状态与保存反馈。</span>
            </div>
            <form id="settings-form" class="card stack">
                <h3>全局设置</h3>
                <label class="toggle-row">
                    <span>每日提醒</span>
                    <input name="dailyReminderEnabled" type="checkbox">
                </label>
                <div class="two-column">
                    <label>提醒小时<input name="dailyReminderHour" type="number" min="0" max="23"></label>
                    <label>提醒分钟<input name="dailyReminderMinute" type="number" min="0" max="59"></label>
                </div>
                <label>显示主题
                    <select name="themeMode">
                        <option value="LIGHT">浅色</option>
                        <option value="DARK">深色</option>
                        <option value="SYSTEM">跟随系统</option>
                    </select>
                </label>
                <button type="submit" class="primary-button">保存设置</button>
                <p id="settings-backup-time" class="muted"></p>
            </form>
        </section>
    `,
    backup: () => `
        <section class="page-section stack">
            <div id="backup-feedback" class="workspace-feedback is-warning">
                <strong>高风险操作提示</strong>
                <span class="muted">导出可直接下载当前备份，恢复会覆盖本地全部数据，执行前请先确认文件来源。</span>
            </div>
            <article class="card stack">
                <h3>备份导出</h3>
                <p class="muted">导出的 JSON 与手机端备份页使用同一份格式，可直接下载到当前设备。</p>
                <button id="export-backup-button" class="primary-button" type="button">导出备份 JSON</button>
            </article>
            <article class="card stack">
                <h3>备份恢复</h3>
                <p class="muted">上传备份 JSON 后会覆盖当前本地全部数据，恢复成功后会自动同步网页与提醒配置。</p>
                <label class="upload-field">
                    <span>选择备份文件</span>
                    <input id="restore-backup-file" type="file" accept="application/json,.json">
                </label>
                <p id="restore-backup-file-meta" class="muted">尚未选择备份文件。</p>
                <label class="toggle-row danger-toggle">
                    <span>我确认恢复会覆盖当前本地全部数据</span>
                    <input id="restore-backup-confirm" type="checkbox">
                </label>
                <div class="form-actions">
                    <button id="restore-backup-button" class="primary-button" type="button" disabled>确认恢复</button>
                    <button id="clear-restore-file-button" class="ghost-button" type="button">清空选择</button>
                </div>
            </article>
        </section>
    `,
};
