## Hare Netty

Web, Kotlin, Netty

### Simple Code

Main Entry:
```kotlin
@KeepMe
fun main(args: Array<String>) {

    // init database pool
    HarePool.push(PostgresConnectionBuilder(user = DB.user, password = DB.pwd, dbname = DB.name, host = DB.host))

    val app = HttpApp("/", name = "MyApp", work = findAppWorkDirectory(args, "work")).apply {
        // init data model, ORM
        migrate(UserAccount::class, AdminAccount::class)
        migrate(Messages::class, Relations::class, Topics::class)
        migrate(Upload::class)
        // timer task
        every(10.timeMinutes) {
            Topics.cleanExpired()
        }
        router {
            // simple router
            action("sayHello") {
                it.sendText("Hello")
            }
            // router from group
            group(PubPage::class, ImagePage::class)
            group(AccountPage::class, RelationPage::class, ChatPage::class, TopicPage::class)
            // interceptor
            before(TokenCheck::checkSessionAction)
            before(::checkAccountState)
            // static file, images, html...
            webroot("static", dirWeb)
        }
    }

    val server = HttpNettyServer(app, 8080)
    server.start()
    logi("Http server listen on ${server.port}")
    logi("Word directory: ${app.work.canonicalPath}")
    server.waitClose()
}
```

Topic model:
```kotlin
/// Topic
class Topics : TableModel() {
    @Label("User ID")
    @ModelField(primaryKey = true)
    var userId: Long by model

    // [0, user.bottleCount)
    @Label("Position")
    @ModelField(primaryKey = true)
    var position: Int by model

    @ModelField
    var nickname: String by model

    @ModelField
    var portrait: String? by model

    @ModelField(index = true, defaultValue = "0")
    var sex: Int by model

    @Hidden
    @ModelField(index = true, defaultValue = "0")
    var ageStage: Int by model

    @Hidden
    @ModelField(index = true, defaultValue = "0")
    @Label("City")
    var city: Int by model

    @ModelField
    @Length(max = 1024)
    var message: String by model

    @ModelField
    var image: String? by model

    @ModelField
    var voice: String? by model

    @ModelField(index = true, defaultValue = "0")
    @OptionList("0:Normal", "1:With Image", "2:Hight")
    @Hidden
    var type: Int by model

    @ModelField(index = true, defaultValue = "0")
    @Label("State")
    @OptionList("0:Waiting", "1:Finish", "2:Expired")
    var state: Int by model

    @ModelField(index = true)
    var createTime: Long by model

    @ModelField(index = true)
    @Hidden
    var deadTime: Long by model

    @Hidden
    @ModelField
    var maxReply: Int by model

    // json array
    @ModelField
    @Length(max = 4096)
    var replys: String? by model

    companion object : TableModelClass<Topics>() {
        const val ST_WAITING = 0
        const val ST_REPLIED = 1
        const val ST_END = 2

        fun cleanExpired() {
            val now: Long = System.currentTimeMillis()
            Topics.filter(Topics::deadTime.BETWEEN(1, now), Topics::state EQ ST_WAITING).update(Topics::state to ST_END)
        }

    }
}
```
Page Group:
```kotlin
// need login, token
@LoginNeed
class TopicPage(override val context: HttpContext) : OnHttpContext {
    val accId: Long = context.accountID!!

    @Action
    fun finish(position: Int): JsonResult {
        val b = Topics.oneByKeys(accId, position) ?: return JsonFailed()
        b.update {
            it.state = Topics.ST_END
        }
        return b.jsonResult()
    }

    @Action
    fun one(position: Int): JsonResult {
        val b = Topics.oneByKeys(accId, position) ?: return JsonFailed()
        return b.jsonResult()
    }

    @Action
    fun list(): JsonResult {
        val ls = Topics.list(Topics::userId EQ accId)
        return ls.jsonResult()
    }

    @Action
    fun reply(targetId: Long, position: Int, message: String): JsonResult {
        val b = Topics.oneByKeys(targetId, position) ?: return JsonFailed()
        if (b.state != Topics.ST_WAITING) return JsonFailed()
        val acc = UserAccount.oneByKey(accId) ?: return JsonFailed()
        val yo: KsonArray = if (b.replys == null) {
            KsonArray()
        } else {
            KsonArray(b.replys!!)
        }

        yo.add(ksonObject("userId" to accId, "message" to message, "nickname" to acc.nickname, "portrait" to acc.portrait))
        if (yo.size >= b.maxReply) {
            b.updateByKey(Topics::state to Topics.ST_REPLIED, Topics::replys to yo.toString())
        } else {
            b.updateByKey(Topics::replys to yo.toString())
        }

        targetClient(targetId, "action" to "bottle", "position" to position)
        return JsonSuccess()
    }

    @Action
    fun pick(sameCity: Boolean = false): JsonResult {
        val user: UserAccount = UserAccount.oneByKey(accId) ?: return JsonFailed("Invalid user")
        val now = context.timeMill
        var w: Where = AND_ALL(Topics::deadTime EQ 0 OR (Topics::deadTime GE now), Topics::state EQ Topics.ST_WAITING)
        if (user.city != 0 && sameCity) {
            w = w AND (Topics::city EQ user.city)
        }
        if (user.targetSex != 0) {
            w = w AND (Topics::sex EQ user.targetSex)
        }
        if (user.targetAgeStage != 0) {
            w = w AND Topics::ageStage.HAS_ANY_BIT(user.targetAgeStage)
        }
        val count = Topics.countAll(w)
        var offset: Int? = if (count > 1) {
            Rand.nextInt(0, count)
        } else {
            null
        }
        val b = Topics.one(w, orderBy = listOf(Topics::createTime.DESC), offset = offset)
        if (b == null) {
            return JsonFailed("NO avaliable topic", code = 999)
        }
        return b.jsonResult()
    }

    @Action
    fun publish(position: Int, message: String, image: HttpFile?, voice: HttpFile?): JsonResult {
        val user: UserAccount = UserAccount.oneByKey(accId) ?: return JsonFailed("Invalid user")
        if (position >= user.bottleCount) return JsonFailed("exced max count")

        val topic: Topics = Topics()
        topic.userId = accId
        topic.position = position
        topic.nickname = user.nickname
        topic.portrait = user.portrait
        topic.sex = user.sex
        topic.ageStage = user.ageStage
        topic.city = user.city
        topic.type = 0
        topic.state = 0
        topic.createTime = context.timeMill
        topic.deadTime = context.timeMill + 1.HOR_MILLS
        topic.message = message
        topic.image = null
        topic.voice = null
        topic.maxReply = user.bottleReplyLimit
        topic.replys = null
        topic.upsert()

        return topic.jsonResult()
    }
}

```
