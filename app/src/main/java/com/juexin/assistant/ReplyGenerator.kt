package com.juexin.assistant

import android.content.Context
import com.juexin.assistant.model.LlmReplies
import com.juexin.assistant.model.ScriptLibrary
import com.juexin.assistant.model.ScriptTemplate
import com.juexin.assistant.network.LlmClient
import com.juexin.assistant.network.ScriptRepository
import kotlinx.coroutines.*

/**
 * 智能回复生成器 —— 三层回退架构
 *
 * 优先级: 远程话术库 > LLM大模型 > 本地硬编码兜底
 */
object ReplyGenerator {

    private var library: ScriptLibrary? = null
    private var isInitialized = false

    /**
     * 初始化：加载配置 + 同步远程话术库
     */
    suspend fun init(context: Context) {
        if (isInitialized) return
        try {
            com.juexin.assistant.network.AppConfig.load(context)
            // 后台同步话术库
            try {
                library = ScriptRepository.syncFromRemote(context)
            } catch (_: Exception) { }
            isInitialized = true
        } catch (_: Exception) {
            isInitialized = true
        }
    }

    /**
     * 生成回复（核心方法）
     */
    suspend fun generateReply(context: Context, userMessage: String): ReplyResult {
        // 确保已初始化
        if (!isInitialized) {
            try { init(context) } catch (_: Exception) { }
        }

        // 第1层：远程话术库匹配
        library?.let { lib ->
            val matched = ScriptRepository.matchTemplate(lib, userMessage)
            if (matched != null) {
                return ReplyResult(
                    compassion = matched.compassion,
                    karma = matched.karma,
                    action = matched.action,
                    source = ReplySource.REMOTE_SCRIPT
                )
            }
        }

        // 第2层：LLM 大模型生成
        if (LlmClient.isAvailable()) {
            try {
                val llmReply = withTimeout(15000L) {
                    LlmClient.generateReply(userMessage)
                }
                if (llmReply != null) {
                    return ReplyResult(
                        compassion = llmReply.compassion,
                        karma = llmReply.karma,
                        action = llmReply.action,
                        source = ReplySource.LLM
                    )
                }
            } catch (_: Exception) { }
        }

        // 第3层：本地硬编码兜底
        val local = matchLocal(userMessage)
        return ReplyResult(
            compassion = local.compassion,
            karma = local.karma,
            action = local.action,
            source = ReplySource.LOCAL_FALLBACK
        )
    }

    /**
     * 强制同步话术库
     */
    suspend fun forceSync(context: Context): Boolean {
        return try {
            library = ScriptRepository.syncFromRemote(context)
            library != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取当前话术库版本
     */
    fun getLibraryVersion(): Int = library?.version ?: 0

    /**
     * 同步方法：仅使用本地话术库匹配（不依赖context，不调网络/LLM）
     * 供悬浮球快速降级使用
     */
    fun generate(userMessage: String): ReplyResult? {
        val local = matchLocal(userMessage)
        return ReplyResult(
            compassion = local.compassion,
            karma = local.karma,
            action = local.action,
            source = ReplySource.LOCAL_FALLBACK
        )
    }

    // ==================== 本地硬编码话术库（兜底） ====================

    private fun matchLocal(message: String): ScriptTemplate {
        val m = message.lowercase()

        // 财运类
        if (m.contains("钱") || m.contains("财") || m.contains("穷") || m.contains("亏") ||
            m.contains("负债") || m.contains("债") || m.contains("赌")
        ) {
            return ScriptTemplate(
                keywords = listOf("钱", "财", "穷", "亏", "负债"),
                compassion = "阿弥陀佛。师父在寺里这些年，见过太多为钱所困的弟子了——有的深夜睡不着给我发消息，有的跪在佛前哭。钱财虽是身外物，可这世间柴米油盐哪样离得开它？弟子心里的苦，师父懂。",
                karma = "从命里看，你这几年恐怕正走破财运，流年冲了财帛宫。往深了说，财运不顺背后是因果——前世吝啬不肯施舍，今生钱财就来得艰难；祖上若阴德有亏，后代就如大树断了根。再加上冤亲债主讨债挡路，钱来了也留不住。",
                action = "师父跟你说句实在话：命运虽定，但可以改。先为你在佛前供一盏财神灯，把财路照亮；再参加一场药师宝忏，把业障清净了。若你心诚，每月初一十五来寺里行善布施，这叫'舍一得万报'，因果不虚啊。"
            )
        }

        // 堕胎类
        if (m.contains("堕胎") || m.contains("流产") || m.contains("打胎") ||
            m.contains("婴灵") || m.contains("孩子没")
        ) {
            return ScriptTemplate(
                keywords = listOf("堕胎", "流产", "婴灵"),
                compassion = "阿弥陀佛。弟子，莫要把自己压垮了。师父跟你说句掏心窝子的话——寺里每个月都有好几位女施主为这事来，有的人十年过去了还放不下。堕胎这个事，缘分不到，强求不来，你不要全怪自己。",
                karma = "但是因果上，我们必须正视。婴灵未能投胎，孤苦飘零，它怨、它冷、它无处可去啊。这在命理上叫'子息宫有煞'，不仅伤你自己的身子，还会拖累婚姻、影响运势，甚至克到在世的子女。地藏经上说得很清楚——杀业之中，堕胎为最重。",
                action = "师父劝你：尽快为这个孩子做一堂超度法事，给它立往生莲位，让地藏菩萨接引它往生净土。你再亲自诵地藏经回向，每月为它供灯。做了这些，你的心才能放下，孩子也才能真正离苦得乐。"
            )
        }

        // 婚姻类
        if (m.contains("婚姻") || m.contains("离婚") || m.contains("出轨") ||
            m.contains("老公") || m.contains("老婆") || m.contains("夫妻") ||
            m.contains("感情") || m.contains("分手")
        ) {
            return ScriptTemplate(
                keywords = listOf("婚姻", "离婚", "出轨", "感情"),
                compassion = "阿弥陀佛。弟子啊，师父活了这些年，见过最伤人的不是病不是穷，就是这情字当头。你现在的痛，旁人不懂，师父懂。一个人扛着这些，苦了你了。",
                karma = "婚姻不顺，从命理上看，多半是夫妻宫犯冲、八字不合。但往深了说，这是前世的情债今生来还——或是你曾辜负过别人，或是这一世有冤亲债主搅扰姻缘。有些缘分本就是业力牵引来的，强求不得，但也并非全无转机。",
                action = "师父给你指条路：先来寺里诚心忏悔前世情债，再参加药师佛圣诞法会，求药师佛加持婚姻和顺。若是缘分实在难续，也莫怕——该了的业了了，下一段路才能走好。"
            )
        }

        // 子女类
        if (m.contains("孩子") || m.contains("儿子") || m.contains("女儿") ||
            m.contains("不听话") || m.contains("叛逆") || m.contains("学习")
        ) {
            return ScriptTemplate(
                keywords = listOf("孩子", "不听话", "叛逆"),
                compassion = "阿弥陀佛。弟子你的心情师父太理解了——自己身上掉下来的肉，不听话的时候，心里比刀割还难受。师父见过太多这样的父母，白天强撑着，晚上偷偷抹泪。你先缓一缓，咱们一起看看这是怎么回事。",
                karma = "孩子不听话、叛逆厌学，从命理上看，这是流年冲了子女宫，孩子自己也很苦，他心里有话说不出来。从佛法因果上讲，这有两种可能：一是前世你欠这孩子的恩债，今生它来讨；二是孩子身边跟着冤亲债主，遮了智慧、迷了心窍。",
                action = "师父给你开个方子：为孩子供一盏文昌灯，求文殊菩萨开启智慧；再参加一场冤亲债主超度，把缠着孩子的不干净东西请走。记住——孩子不是你的仇人，是来度你的菩萨。"
            )
        }

        // 健康类
        if (m.contains("病") || m.contains("疼") || m.contains("痛") || m.contains("癌") ||
            m.contains("医院") || m.contains("失眠") || m.contains("抑郁")
        ) {
            return ScriptTemplate(
                keywords = listOf("病", "疼", "失眠", "抑郁"),
                compassion = "阿弥陀佛。病来如山倒，个中滋味，师父怎么会不知道呢？这几年寺里供药师佛的弟子越来越多——都是被病苦折磨的人啊。你身心俱疲的样子，师父虽未见你面，但能感受到。",
                karma = "病痛分三种：一是四大不调该看医生；二是业障病，前世杀业、口业今生报在身体上；三是冤亲债主缠身，让你查不出病因就是不舒服。从命理上看，这是疾厄宫有凶星照临，加上自身福报亏空，病就趁虚而入。",
                action = "师父说句实在话：药要吃、医生要看，这是一层。另一层，你得在佛前供药师灯，参加药师宝忏。药师佛是东方琉璃世界教主，专治众生疾苦。再随师父放一次生——救命积德，这功德回向到身体上，比什么补药都灵。"
            )
        }

        // 噩梦类
        if (m.contains("梦") || m.contains("噩梦") || m.contains("鬼") || m.contains("怕"))
        {
            return ScriptTemplate(
                keywords = listOf("梦", "噩梦", "鬼"),
                compassion = "阿弥陀佛！弟子莫怕，师父在呢。噩梦缠身这个事，不是小事——寺里有些老居士，几十年了一提起噩梦还心有余悸。师父告诉你，这不是你想多了，是你身边确有不干净的东西。",
                karma = "从命理上看，你眼下走的可能是阴气重的运，阳气不足，阴性众生就容易找上门。从佛法上讲，频繁噩梦，十有八九是冤亲债主托梦。它们在地狱或饿鬼道受苦，只能通过梦境告诉你它们的存在。也可能是过世亲人在下面过得不好，托梦来求救。",
                action = "师父给你指条明路：第一，赶紧来寺里请一道平安符随身带着；第二，在寺里供一盏地藏灯——地藏菩萨主幽冥界，专管这些事；第三，参加一场地藏法会，把冤亲债主超度了。做了这些，你就能睡个安稳觉了。"
            )
        }

        // 亡亲托梦类
        if (m.contains("去世") || m.contains("过世") || m.contains("托梦") ||
            m.contains("亡") || m.contains("死") || m.contains("走")
        ) {
            return ScriptTemplate(
                keywords = listOf("去世", "过世", "托梦"),
                compassion = "阿弥陀佛。亲人走了，这个痛，时间再久也不会完全消失。师父活了这把年纪，送走过太多人——有些家属十年后来寺里做法事，说起故去的亲人还是一边说一边掉泪。他能托梦给你，说明他牵挂着你，也说明他在那边需要你的帮助。",
                karma = "《地藏经》讲得很清楚：亡人七七四十九天之内，最需要阳上亲人做功德回向。若是亡人堕在饿鬼道、地狱道，就会托梦给亲人——那不是普通的梦，是求救的信号。再说得直白些，祖上不安，后代也难安宁，这在风水上叫'祖荫不足'。",
                action = "师父建议你三件事：立刻为亡亲立往生莲位，请师父为他诵经回向；在地藏法会期间，你亲自来诵一部地藏经；再随缘放生、供灯，功德全部回向给亡亲。你做了这些，他在那边离苦得乐了，你在阳世也会感到莫名的轻松。"
            )
        }

        // 压力/焦虑类
        if (m.contains("压力") || m.contains("焦虑") || m.contains("烦") ||
            m.contains("累") || m.contains("迷茫")
        ) {
            return ScriptTemplate(
                keywords = listOf("压力", "焦虑", "烦", "迷茫"),
                compassion = "阿弥陀佛。弟子，你辛苦了。师父见过太多人扛着压力硬撑——白天在人前笑，晚上一个人闷着。这世道确实不容易，一个人扛着一个家，肩上越来越重。你先坐下来，喘口气，听听师父跟你说。",
                karma = "从命理上讲，你现在恐怕正逢'运气低迷'的阶段——做什么都不顺，想什么都烦。往根子上挖，心不安神不宁，多半是前世业障现前，冤亲债主在你心神最脆弱的时候来干扰。你越累、越烦，它们越缠你，形成恶性循环。",
                action = "师父给你个实在建议：先来寺里静心坐一炷香的时间——就静静地坐在佛前，什么都不想，让佛光照照你。再在佛前供一盏药师灯安神定志。等你心里平静了，很多事自然就有答案了。记住——你不是在孤军奋战，佛菩萨一直在看着你。"
            )
        }

        // 通用兜底
        return ScriptTemplate(
            keywords = listOf("*"),
            compassion = "阿弥陀佛。弟子所遇之事，师父已明了。人生在世，谁能无苦？能在茫茫苦海中遇见佛法，本身就是莫大的福报。师父在寺里这些年，什么样的苦没见过——但有一条是千真万确的：没有过不去的坎，只有想不通的心。",
            karma = "世间一切不顺，根源皆在因果。前世所造之业，今生必受其报——这不是佛在惩罚谁，是你自己种下的种子，时候到了自然结果。但弟子不要绝望，因为因果是可以转的，业障是可以消的。不然我们修行、供佛、做法事又是为了什么呢？",
            action = "师父给你指一条路：先来寺里一趟，师父当面为你看看，该供灯供灯、该做法事做法事。你也可以随缘参加寺院的法会，先在佛前点一盏灯——这一盏灯点下去，就是从黑暗中迈出的第一步。阿弥陀佛。"
        )
    }
}

/**
 * 回复结果
 */
data class ReplyResult(
    val compassion: String,
    val karma: String,
    val action: String,
    val source: ReplySource
)

/**
 * 回复来源
 */
enum class ReplySource {
    REMOTE_SCRIPT,  // 远程同步的话术库
    LLM,            // 大模型生成
    LOCAL_FALLBACK  // 本地硬编码兜底
}
