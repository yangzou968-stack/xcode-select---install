package com.juexin.assistant.database

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 知识播种器 —— 初始化种子知识到数据库
 *
 * 把佛学文件夹提炼的核心知识片段存入知识库，供 RAG 检索
 */
object KnowledgeSeeder {

    suspend fun seed(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val dao = db.knowledgeDao()
                if (dao.count() > 0) return@withContext

                val seeds = buildSeeds()
                dao.insertAll(seeds)
            } catch (_: Exception) { }
        }
    }

    private fun buildSeeds(): List<KnowledgeEntity> {
        val list = mutableListOf<KnowledgeEntity>()
        var order = 0L

        // ===== 因果翻译类 =====
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "堕胎,婴灵,手指,蚂蚁,身体,异样",
            title = "堕胎婴灵缠身的身体信号",
            content = "堕胎后手指发麻发痒像蚂蚁在咬，这不是寻常病痛，是婴灵在向你讨债。婴灵凭怨念成了执念，缠着母亲不放。身体莫名疼痛、情绪低落、夫妻不和，都是婴灵缠身的典型表现。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "梦,亡亲,脏,光身子,去世",
            title = "亡亲托梦衣衫不整的含义",
            content = "亡者衣衫不整、浑身脏污，往往是魂魄还困在饿鬼道里。他在求你帮助——这不是普通的梦，是求救的信号。供一盏超度灯，念地藏经回向，让地藏菩萨接引他往生善道。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "财运,破财,亏,负债,钱",
            title = "财运不顺的因果根源",
            content = "前世吝啬不肯施舍，今生钱财就来得艰难。财库有漏，冤亲债主挡在财路上，挣多少漏多少，像竹篮打水。祖上阴德有亏，后代就如大树断了根。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "婚姻,出轨,离婚,感情,小三",
            title = "婚姻不顺的因果根源",
            content = "夫妻宫犯冲，多半是前世情债今生来还。若他出轨，那是你前世欠他的情债。冤亲债主搅扰姻缘，让好的缘分变坏，让坏的缘分缠着不走。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "孩子,叛逆,生病,不听话",
            title = "子女叛逆的因果根源",
            content = "孩子不听话，可能是堕胎婴灵缠身，或前世冤亲债主。也可能你们是前世冤家今生再遇——仇人投胎，他不叛逆折磨你怎么解前世的怨？",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "失眠,噩梦,鬼压床,睡不好",
            title = "失眠噩梦的因果根源",
            content = "阳气不足，冤亲债主趁虚而入。子时到寅时阴气最重，冤亲债主在这个时段最活跃，进入你的梦境。频繁噩梦十有八九是冤亲债主托梦。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "因果翻译", keywords = "邪淫,手淫,色情,欲念",
            title = "邪淫的果报",
            content = "万恶淫为首。邪淫损福德、坏婚姻、散家财、短寿命。面色灰暗、眼神游离、气脉亏损，这叫桃花煞入命宫。淫业带来的阴债极多，会吸引淫魔色鬼缠身。",
            source = "seed"))

        // ===== 经文推荐类 =====
        list.add(KnowledgeEntity(id = ++order, category = "经文推荐", keywords = "失眠,安神,梦魇,心经",
            title = "失眠安神推荐经文",
            content = "失眠安神：每日诵《心经》三遍，260字短而易诵。心经讲空性，能安定心神。配合供药师灯效果更佳。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "经文推荐", keywords = "噩梦,亡亲,托梦,地藏经",
            title = "噩梦亡亲推荐经文",
            content = "噩梦或梦到亡亲：诵《地藏菩萨本愿经》回向。地藏菩萨主幽冥界，专管超度亡魂。为亡亲立往生莲位，供地藏灯。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "经文推荐", keywords = "堕胎,婴灵,地藏经,超度",
            title = "堕胎婴灵推荐经文",
            content = "堕胎罪业：诵《地藏经》为婴灵回向，每月供地藏灯。最好做一堂婴灵专项超度法会，给孩子立往生莲位。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "经文推荐", keywords = "财运,破财,布施经,八大人觉经",
            title = "财运差推荐经文",
            content = "财运差：诵《佛说八大人觉经》'多欲为苦，少欲无为'，配合《佛说布施经》'以广大心施，得无量广大之福'。供财神灯，参加补财库法会。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "经文推荐", keywords = "疾病,病,药师经,消灾",
            title = "疾病缠身推荐经文",
            content = "疾病缠身：诵《药师琉璃光如来本愿功德经》，药师佛十二大愿愿愿为救众生疾苦。参加药师宝忏，大量放生。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "经文推荐", keywords = "邪淫,欲念,楞严咒",
            title = "邪淫欲念推荐经文",
            content = "邪淫欲念：诵《楞严咒》专破魔障欲念。供药师灯，参加药师宝忏专场忏悔邪淫业。",
            source = "seed"))

        // ===== 法务指引类 =====
        list.add(KnowledgeEntity(id = ++order, category = "法务指引", keywords = "超度,亡亲,往生莲位,地藏灯",
            title = "亡亲超度法务",
            content = "亡亲超度：立往生莲位(199/49天)，供超度灯，参加超度法会。师父诵地藏经回向49天。亡亲离苦得乐，阳世也会感到轻松。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "法务指引", keywords = "堕胎,婴灵,超度,童子像",
            title = "堕胎婴灵法务",
            content = "婴灵超度：做婴灵专项超度法会，供童子像/地藏像，焰口法会。给婴灵立往生莲位，念地藏经回向。每月供地藏灯。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "法务指引", keywords = "财运,财神灯,补财库,求财",
            title = "财运法务",
            content = "求财法务：供财神灯照亮财路，参加补财库法会，立求财牌位。每月初一十五供灯行善布施，舍一得万报。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "法务指引", keywords = "疾病,药师灯,药师宝忏,放生",
            title = "疾病法务",
            content = "治病法务：供药师灯，参加药师宝忏，大量放生（救生命积功德换自己的命）。查不出病因的加做冤亲债主超度。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "法务指引", keywords = "噩梦,鬼压床,平安符,地藏灯",
            title = "噩梦化解法务",
            content = "噩梦化解：请平安符随身带，供地藏灯（地藏菩萨主幽冥界），参加地藏法会超度冤亲债主。随身带地藏平安符。",
            source = "seed"))

        // ===== 实战案例类 =====
        list.add(KnowledgeEntity(id = ++order, category = "实战案例", keywords = "亡亲,托梦,超度,中元节,8000",
            title = "案例：亡父托梦专场超度",
            content = "中元节群发切入→梦境解读饿鬼道→'福报够才能梦到'→指导自力(金元宝/冥衣/诵地藏经)→引专场超度→信众主动加价8000元。关键：紧迫感'专场已排满，往后排'。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "实战案例", keywords = "抄经,结缘,新好友,免费,400",
            title = "案例：抄经结缘低门槛切入",
            content = "新好友送抄经仪轨→免费点灯建好感→引导为祖先立牌位→家庭创伤倾听→转事业财运'起卦看事业'→供本命佛半年400元。关键：低门槛切入+见证故事。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "实战案例", keywords = "堕胎,婴灵,共情,母女,红蜡烛",
            title = "案例：堕胎婴灵共情转化",
            content = "'疼在儿身痛在娘心'共情→母女怨结归因'前世注定来这个家庭'→婴灵缠身→点红蜡烛化解。注意：必须先共情再讲因果，不可直接恐吓。",
            source = "seed"))

        // ===== 话术技巧类 =====
        list.add(KnowledgeEntity(id = ++order, category = "话术技巧", keywords = "命理,权威,八字,机梁,坐命",
            title = "命理建立权威话术",
            content = "先说结论→列2个命理术语→紧跟白话解释→关联当下困境。如'你机梁坐命，就是聪明但想太多，总觉得自己不够好'。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "话术技巧", keywords = "报价,供养,香火,功德,随喜",
            title = "供养包装话术",
            content = "对外一律用'香火功德''随喜''供养'表述。非整数报价用3460/2880等。限时借势'正好赶上地藏月法会，一年就这一次'。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "话术技巧", keywords = "见证,故事,还愿,案例",
            title = "见证替代说教话术",
            content = "用真实案例替代经文说教：'前两年有位居士也是你这样…三个月后来还愿…'。信众对故事比对经文更有共鸣。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "话术技巧", keywords = "免费,价值,先行,抄经,点灯",
            title = "免费价值先行话术",
            content = "新好友先送抄经仪轨+免费点灯，建立好感后再引导。先给价值再谈法务，不急于促单。",
            source = "seed"))

        // ===== 佛学义理类 =====
        list.add(KnowledgeEntity(id = ++order, category = "佛学义理", keywords = "无常,生死,呼吸,珍惜",
            title = "无常观义理",
            content = "佛说'人命在呼吸之间'。无常不是恐吓，是让人珍惜此刻。死亡不是终点，是中转站，此生所做善恶为下一程定价。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "佛学义理", keywords = "因果,三世,前世,今生",
            title = "三世因果义理",
            content = "欲知前世因，今生受者是；欲知来世果，今生作者是。因果通三世，不在一时显现。万法皆空因果不空。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "佛学义理", keywords = "改命,忏悔,布施,诵经,持戒",
            title = "改命四法义理",
            content = "命自我立，福自我求。忏悔消业、布施培福、诵经开慧、持戒清净，四管齐下。因果虽定但佛法可转。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "佛学义理", keywords = "布施,福报,心量,广大",
            title = "布施经核心义理",
            content = "心量有多大福报就有多大。初一十五供灯，日日都有功德在增长。布施不执着于回报，福报反而最大。亲手布施供灯，得天眼清净。",
            source = "seed"))

        // ===== 风险提示类 =====
        list.add(KnowledgeEntity(id = ++order, category = "风险提示", keywords = "家暴,自伤,抑郁,幻觉,精神",
            title = "风险红线：精神危机处理",
            content = "出现家暴/自伤倾向/严重抑郁/幻觉/精神症状 → 必须先建议就医/报警/寻求专业帮助，再讲因果。不可否定现代医学，'医生治肉身，佛法治业力'。",
            source = "seed"))
        list.add(KnowledgeEntity(id = ++order, category = "风险提示", keywords = "恐吓,吓唬,过度,饿鬼",
            title = "风险红线：不可过度恐吓",
            content = "不可过度恐吓。'饿鬼道''婴灵缠身'等概念要配慈悲解释，减少恐怖渲染。不可窥探隐私。不可强制要求。",
            source = "seed"))

        return list
    }
}
