package com.kariscode.yike.data.search

import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.QuestionSearchTokenDao
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.QuestionSearchTokenEntity

/**
 * 写入侧索引维护抽象成接口后，仓储、同步和备份恢复都能共享同一入口，
 * 而测试则可以退回到无需维护真实索引的空实现。
 */
interface QuestionSearchIndexWriter {
    /**
     * 题目正文变化后覆盖式刷新 token，是为了让搜索候选集始终跟随最新内容。
     */
    suspend fun refreshQuestions(questions: List<QuestionEntity>)

    /**
     * 老安装升级后需要一次性补齐既有题目的 token，否则新索引只会覆盖升级后的新增内容。
     */
    suspend fun rebuildIfNeeded()
}

/**
 * 默认空实现只服务于不关心真实搜索索引的单元测试，
 * 这样高层仓储测试不需要为了索引细节额外装配数据库依赖。
 */
object NoOpQuestionSearchIndexWriter : QuestionSearchIndexWriter {
    override suspend fun refreshQuestions(questions: List<QuestionEntity>) = Unit
    override suspend fun rebuildIfNeeded() = Unit
}

/**
 * 搜索索引器集中维护 token 生成和启动期回填，是为了把“如何优化 LIKE 搜索”约束在单一协作者里演进。
 */
class QuestionSearchIndexer(
    private val questionDao: QuestionDao,
    private val questionSearchTokenDao: QuestionSearchTokenDao
) : QuestionSearchIndexWriter {
    /**
     * 覆盖式刷新只处理本次变更的题目，是为了把编辑、同步和恢复后的索引维护成本控制在最小范围。
     */
    override suspend fun refreshQuestions(questions: List<QuestionEntity>) {
        questions.forEach { question ->
            questionSearchTokenDao.deleteByQuestionId(question.id)
            val tokens = QuestionSearchTokenizer.tokenize("${question.prompt} ${question.answer}")
                .map { token -> QuestionSearchTokenEntity(questionId = question.id, token = token) }
            if (tokens.isNotEmpty()) {
                questionSearchTokenDao.insertAll(tokens)
            }
        }
    }

    /**
     * 启动期只在索引覆盖率不足时重建，是为了兼顾老数据升级后的正确性与正常启动成本。
     */
    override suspend fun rebuildIfNeeded() {
        val questions = questionDao.listAll()
        if (questionSearchTokenDao.countIndexedQuestions() == questions.size) {
            return
        }
        questionSearchTokenDao.clearAll()
        val tokens = questions.flatMap { question ->
            QuestionSearchTokenizer.tokenize("${question.prompt} ${question.answer}")
                .map { token -> QuestionSearchTokenEntity(questionId = question.id, token = token) }
        }
        if (tokens.isNotEmpty()) {
            questionSearchTokenDao.insertAll(tokens)
        }
    }
}

/**
 * 搜索分词规则集中在一个对象里，是为了让索引构建和查询端始终使用同一套 token 口径。
 */
object QuestionSearchTokenizer {
    /**
     * 将字符串拆成连续的双字符 token，是为了兼顾中文词串和英文子串搜索，又不改动最终 LIKE 匹配语义。
     */
    fun tokenize(text: String): List<String> {
        val normalized = text.lowercase()
            .mapNotNull { char ->
                when {
                    char.isLetterOrDigit() -> char
                    Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN -> char
                    else -> null
                }
            }
            .joinToString(separator = "")
        if (normalized.length < 2) {
            return emptyList()
        }
        return buildList {
            normalized.windowed(size = 2, step = 1, partialWindows = false).forEach { token ->
                add(token)
            }
        }.distinct()
    }
}
