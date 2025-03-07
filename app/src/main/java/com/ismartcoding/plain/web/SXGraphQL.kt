package com.ismartcoding.plain.web

import android.os.Environment
import com.apurebase.kgraphql.GraphQLError
import com.apurebase.kgraphql.GraphqlRequest
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.helpers.getFields
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.apurebase.kgraphql.schema.dsl.SchemaConfigurationDSL
import com.apurebase.kgraphql.schema.execution.Execution
import com.apurebase.kgraphql.schema.execution.Executor
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.extensions.allowSensitivePermissions
import com.ismartcoding.lib.extensions.getFinalPath
import com.ismartcoding.lib.extensions.isAudioFast
import com.ismartcoding.lib.extensions.isImageFast
import com.ismartcoding.lib.extensions.isVideoFast
import com.ismartcoding.lib.extensions.newPath
import com.ismartcoding.lib.extensions.scanFileByConnection
import com.ismartcoding.lib.extensions.toAppUrl
import com.ismartcoding.lib.helpers.CoroutinesHelper.coIO
import com.ismartcoding.lib.helpers.CoroutinesHelper.withIO
import com.ismartcoding.lib.helpers.CryptoHelper
import com.ismartcoding.lib.helpers.PhoneHelper
import com.ismartcoding.lib.isQPlus
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.BuildConfig
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.TempData
import com.ismartcoding.plain.api.BoxProxyApi
import com.ismartcoding.plain.api.HttpApiTimeout
import com.ismartcoding.plain.data.UIDataCache
import com.ismartcoding.plain.data.enums.ActionSourceType
import com.ismartcoding.plain.data.enums.ActionType
import com.ismartcoding.plain.data.enums.DataType
import com.ismartcoding.plain.data.preference.ApiPermissionsPreference
import com.ismartcoding.plain.data.preference.AudioPlayModePreference
import com.ismartcoding.plain.data.preference.AudioPlayingPreference
import com.ismartcoding.plain.data.preference.AudioPlaylistPreference
import com.ismartcoding.plain.data.preference.AudioSortByPreference
import com.ismartcoding.plain.data.preference.AuthDevTokenPreference
import com.ismartcoding.plain.data.preference.ChatGPTApiKeyPreference
import com.ismartcoding.plain.data.preference.ImageSortByPreference
import com.ismartcoding.plain.data.preference.VideoPlaylistPreference
import com.ismartcoding.plain.data.preference.VideoSortByPreference
import com.ismartcoding.plain.db.AppDatabase
import com.ismartcoding.plain.db.DChat
import com.ismartcoding.plain.db.DMessageFile
import com.ismartcoding.plain.db.DMessageFiles
import com.ismartcoding.plain.db.DMessageImages
import com.ismartcoding.plain.db.DMessageType
import com.ismartcoding.plain.features.AIChatCreatedEvent
import com.ismartcoding.plain.features.ActionEvent
import com.ismartcoding.plain.features.CancelNotificationsEvent
import com.ismartcoding.plain.features.ClearAudioPlaylistEvent
import com.ismartcoding.plain.features.Permission
import com.ismartcoding.plain.features.QueryHelper
import com.ismartcoding.plain.features.StartScreenMirrorEvent
import com.ismartcoding.plain.features.aichat.AIChatHelper
import com.ismartcoding.plain.features.audio.AudioHelper
import com.ismartcoding.plain.features.audio.AudioPlayer
import com.ismartcoding.plain.features.audio.DPlaylistAudio
import com.ismartcoding.plain.features.audio.MediaPlayMode
import com.ismartcoding.plain.features.call.CallHelper
import com.ismartcoding.plain.features.call.SimHelper
import com.ismartcoding.plain.features.chat.ChatHelper
import com.ismartcoding.plain.features.contact.ContactHelper
import com.ismartcoding.plain.features.contact.GroupHelper
import com.ismartcoding.plain.features.contact.SourceHelper
import com.ismartcoding.plain.features.feed.FeedEntryHelper
import com.ismartcoding.plain.features.feed.FeedHelper
import com.ismartcoding.plain.features.feed.fetchContentAsync
import com.ismartcoding.plain.features.file.FileSortBy
import com.ismartcoding.plain.features.file.FileSystemHelper
import com.ismartcoding.plain.features.image.ImageHelper
import com.ismartcoding.plain.features.note.NoteHelper
import com.ismartcoding.plain.features.pkg.PackageHelper
import com.ismartcoding.plain.features.sms.SmsHelper
import com.ismartcoding.plain.features.tag.TagHelper
import com.ismartcoding.plain.features.tag.TagRelationStub
import com.ismartcoding.plain.features.video.VideoHelper
import com.ismartcoding.plain.helpers.AppHelper
import com.ismartcoding.plain.helpers.DeviceInfoHelper
import com.ismartcoding.plain.helpers.ExchangeHelper
import com.ismartcoding.plain.helpers.FileHelper
import com.ismartcoding.plain.helpers.TempHelper
import com.ismartcoding.plain.receivers.BatteryReceiver
import com.ismartcoding.plain.receivers.PlugInControlReceiver
import com.ismartcoding.plain.services.ScreenMirrorService
import com.ismartcoding.plain.ui.MainActivity
import com.ismartcoding.plain.web.loaders.FileInfoLoader
import com.ismartcoding.plain.web.loaders.TagsLoader
import com.ismartcoding.plain.web.models.AIChat
import com.ismartcoding.plain.web.models.AIChatConfig
import com.ismartcoding.plain.web.models.App
import com.ismartcoding.plain.web.models.Audio
import com.ismartcoding.plain.web.models.Call
import com.ismartcoding.plain.web.models.ChatItem
import com.ismartcoding.plain.web.models.Contact
import com.ismartcoding.plain.web.models.ContactGroup
import com.ismartcoding.plain.web.models.ContactInput
import com.ismartcoding.plain.web.models.FeedEntry
import com.ismartcoding.plain.web.models.FileInfo
import com.ismartcoding.plain.web.models.Files
import com.ismartcoding.plain.web.models.ID
import com.ismartcoding.plain.web.models.Image
import com.ismartcoding.plain.web.models.Message
import com.ismartcoding.plain.web.models.Note
import com.ismartcoding.plain.web.models.NoteInput
import com.ismartcoding.plain.web.models.PackageStatus
import com.ismartcoding.plain.web.models.StorageStats
import com.ismartcoding.plain.web.models.TempValue
import com.ismartcoding.plain.web.models.Video
import com.ismartcoding.plain.web.models.toModel
import com.ismartcoding.plain.workers.FeedFetchWorker
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.util.AttributeKey
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import kotlin.collections.set
import kotlin.io.path.Path
import kotlin.io.path.moveTo

class SXGraphQL(val schema: Schema) {
    class Configuration : SchemaConfigurationDSL() {
        fun init() {
            schemaBlock = {
                query("aiChats") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        val items = AIChatHelper.search(QueryHelper.prepareQuery(query), limit, offset)
                        items.map { it.toModel() }
                    }
                    type<AIChat> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.AI_CHAT)
                            }
                        }
                    }
                }
                query("aiChatCount") {
                    resolver { query: String ->
                        AIChatHelper.count(QueryHelper.prepareQuery(query))
                    }
                }
                query("aiChatConfig") {
                    resolver { ->
                        AIChatConfig(ChatGPTApiKeyPreference.getAsync(MainApp.instance))
                    }
                }
                query("aiChat") {
                    resolver { id: ID ->
                        AIChatHelper.getAsync(id.value)?.toModel()
                    }
                }
                query("chatItems") {
                    resolver { ->
                        val dao = AppDatabase.instance.chatDao()
                        var items = dao.getAll()
                        if (!TempData.chatItemsMigrated) {
                            val context = MainApp.instance
                            TempData.chatItemsMigrated = true
                            val types = setOf("app", "storage", "work", "social", "exchange")
                            val ids = items.filter { types.contains(it.content.type) }.map { it.id }
                            if (ids.isNotEmpty()) {
                                dao.deleteByIds(ids)
                                items = items.filter { !types.contains(it.content.type) }
                            }
                            items.filter { setOf(DMessageType.IMAGES.value, DMessageType.FILES.value).contains(it.content.type) }.forEach {
                                if (it.content.value is DMessageImages) {
                                    val c = it.content.value as DMessageImages
                                    if (c.items.any { i -> !i.uri.startsWith("app://") }) {
                                        it.content.value =
                                            DMessageImages(
                                                c.items.map { i ->
                                                    DMessageFile(i.uri.toAppUrl(context), i.size, i.duration)
                                                },
                                            )
                                        dao.update(it)
                                    }
                                } else if (it.content.value is DMessageFiles) {
                                    val c = it.content.value as DMessageFiles
                                    if (c.items.any { i -> !i.uri.startsWith("app://") }) {
                                        it.content.value =
                                            DMessageFiles(
                                                c.items.map { i ->
                                                    DMessageFile(i.uri.toAppUrl(context), i.size, i.duration)
                                                },
                                            )
                                        dao.update(it)
                                    }
                                }
                            }
                        }
                        items.map { it.toModel() }
                    }
                }
                type<ChatItem> {
//                    property(ChatItem::_content) {
//                        ignore = true
//                    }
                    property("data") {
                        resolver { c: ChatItem ->
                            c.getContentData()
                        }
                    }
                }
                query("messages") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        Permission.READ_SMS.checkAsync(MainApp.instance)
                        SmsHelper.search(MainApp.instance, QueryHelper.prepareQuery(query), limit, offset).map { it.toModel() }
                    }
                    type<Message> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.SMS)
                            }
                        }
                    }
                }
                query("messageCount") {
                    resolver { query: String ->
                        if (Permission.READ_SMS.can(MainApp.instance)) {
                            SmsHelper.count(MainApp.instance, QueryHelper.prepareQuery(query))
                        } else {
                            -1
                        }
                    }
                }
                query("images") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        ImageHelper.search(context, QueryHelper.prepareQuery(query), limit, offset, ImageSortByPreference.getValueAsync(context)).map {
                            it.toModel()
                        }
                    }
                    type<Image> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.IMAGE)
                            }
                        }
                    }
                }
                query("imageCount") {
                    resolver { query: String ->
                        if (Permission.WRITE_EXTERNAL_STORAGE.can(MainApp.instance)) {
                            ImageHelper.count(MainApp.instance, QueryHelper.prepareQuery(query))
                        } else {
                            -1
                        }
                    }
                }
                query("mediaBuckets") {
                    resolver { type: DataType ->
                        val context = MainApp.instance
                        if (Permission.WRITE_EXTERNAL_STORAGE.can(context)) {
                            if (type == DataType.IMAGE) {
                                ImageHelper.getBuckets(context).map { it.toModel() }
                            } else if (type == DataType.AUDIO) {
                                if (isQPlus()) {
                                    AudioHelper.getBuckets(context).map { it.toModel() }
                                } else {
                                    emptyList()
                                }
                            } else if (type == DataType.VIDEO) {
                                VideoHelper.getBuckets(context).map { it.toModel() }
                            } else {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    }
                }
                query("videos") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        VideoHelper.search(context, QueryHelper.prepareQuery(query), limit, offset, VideoSortByPreference.getValueAsync(context)).map {
                            it.toModel()
                        }
                    }
                    type<Video> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.VIDEO)
                            }
                        }
                    }
                }
                query("videoCount") {
                    resolver { query: String ->
                        if (Permission.WRITE_EXTERNAL_STORAGE.can(MainApp.instance)) {
                            VideoHelper.count(MainApp.instance, QueryHelper.prepareQuery(query))
                        } else {
                            -1
                        }
                    }
                }
                query("audios") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        AudioHelper.search(context, QueryHelper.prepareQuery(query), limit, offset, AudioSortByPreference.getValueAsync(context)).map {
                            it.toModel()
                        }
                    }
                    type<Audio> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.AUDIO)
                            }
                        }
                    }
                }
                query("audioCount") {
                    resolver { query: String ->
                        if (Permission.WRITE_EXTERNAL_STORAGE.can(MainApp.instance)) {
                            AudioHelper.count(MainApp.instance, QueryHelper.prepareQuery(query))
                        } else {
                            -1
                        }
                    }
                }
                query("contacts") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        Permission.READ_CONTACTS.checkAsync(MainApp.instance)
                        try {
                            ContactHelper.search(MainApp.instance, QueryHelper.prepareQuery(query), limit, offset).map { it.toModel() }
                        } catch (ex: Exception) {
                            LogCat.e(ex)
                            emptyList()
                        }
                    }
                    type<Contact> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.CONTACT)
                            }
                        }
                    }
                }
                query("contactCount") {
                    resolver { query: String ->
                        if (Permission.READ_CONTACTS.can(MainApp.instance)) {
                            ContactHelper.count(MainApp.instance, QueryHelper.prepareQuery(query))
                        } else {
                            -1
                        }
                    }
                }
                query("contactSources") {
                    resolver { ->
                        Permission.READ_CONTACTS.checkAsync(MainApp.instance)
                        SourceHelper.getAll().map { it.toModel() }
                    }
                }
                query("contactGroups") {
                    resolver { node: Execution.Node ->
                        Permission.READ_CONTACTS.checkAsync(MainApp.instance)
                        val groups = GroupHelper.getAll().map { it.toModel() }
                        val fields = node.getFields()
                        if (fields.contains(ContactGroup::contactCount.name)) {
                            // TODO support contactsCount
                        }
                        groups
                    }
                }
                query("calls") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        Permission.READ_CALL_LOG.checkAsync(MainApp.instance)
                        CallHelper.search(MainApp.instance, QueryHelper.prepareQuery(query), limit, offset).map { it.toModel() }
                    }
                    type<Call> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.CALL)
                            }
                        }
                    }
                }
                query("callCount") {
                    resolver { query: String ->
                        if (Permission.READ_CALL_LOG.can(MainApp.instance)) {
                            CallHelper.count(MainApp.instance, QueryHelper.prepareQuery(query))
                        } else {
                            -1
                        }
                    }
                }
                query("sims") {
                    resolver { ->
                        SimHelper.getAll().map { it.toModel() }
                    }
                }
                query("packages") {
                    resolver { offset: Int, limit: Int, query: String ->
                        PackageHelper.search(query, limit, offset).map { it.toModel() }
                    }
                }
                query("packageStatuses") {
                    resolver { ids: List<ID> ->
                        PackageHelper.getPackageStatuses(ids.map { it.value }).map { PackageStatus(ID(it.key), it.value) }
                    }
                }
                query("packageCount") {
                    resolver { query: String ->
                        PackageHelper.count(query)
                    }
                }
                query("storageStats") {
                    resolver { ->
                        val context = MainApp.instance
                        StorageStats(
                            FileSystemHelper.getInternalStorageStats().toModel(),
                            FileSystemHelper.getSDCardStorageStats(context).toModel(),
                            FileSystemHelper.getUSBStorageStats().map { it.toModel() },
                        )
                    }
                }
                query("screenMirrorImage") {
                    resolver { ->
                        ScreenMirrorService.instance?.getLatestImageBase64() ?: ""
                    }
                }
                query("recentFiles") {
                    resolver { ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        FileSystemHelper.getRecents(context).map { it.toModel() }
                    }
                }
                query("files") {
                    resolver { dir: String, showHidden: Boolean, sortBy: FileSortBy ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        val files = FileSystemHelper.getFilesList(dir, showHidden, sortBy).map { it.toModel() }
                        Files(dir, files)
                    }
                }
                query("fileInfo") {
                    resolver { id: ID, path: String ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        val finalPath = path.getFinalPath(context)
                        val file = File(finalPath)
                        val updatedAt = Instant.fromEpochMilliseconds(file.lastModified())
                        val size = file.length()
                        val fileInfo = FileInfo(updatedAt, size)
                        if (finalPath.isImageFast()) {
                            fileInfo.data = FileInfoLoader.loadImage(id.value, finalPath)
                        } else if (finalPath.isVideoFast()) {
                            fileInfo.data = FileInfoLoader.loadVideo(context, id.value, finalPath)
                        } else if (finalPath.isAudioFast()) {
                            fileInfo.data = FileInfoLoader.loadAudio(context, id.value, finalPath)
                        }
                        fileInfo
                    }
                }
                query("boxes") {
                    resolver { ->
                        val items = AppDatabase.instance.boxDao().getAll()
                        items.map { it.toModel() }
                    }
                }
                query("tags") {
                    resolver { type: DataType ->
                        val tagCountMap = TagHelper.count(type).associate { it.id to it.count }
                        TagHelper.getAll(type).map {
                            it.count = tagCountMap[it.id] ?: 0
                            it.toModel()
                        }
                    }
                }
                query("notifications") {
                    resolver { ->
                        val context = MainApp.instance
                        Permission.NOTIFICATION_LISTENER.checkAsync(context)
                        TempData.notifications.sortedByDescending { it.time }.map { it.toModel() }
                    }
                }
                query("feeds") {
                    resolver { ->
                        val items = FeedHelper.getAll()
                        items.map { it.toModel() }
                    }
                }
                query("feedEntries") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        val items = FeedEntryHelper.search(QueryHelper.prepareQuery(query), limit, offset)
                        items.map { it.toModel() }
                    }
                    type<FeedEntry> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.FEED_ENTRY)
                            }
                        }
                    }
                }
                query("feedEntryCount") {
                    resolver { query: String ->
                        FeedEntryHelper.count(QueryHelper.prepareQuery(query))
                    }
                }
                query("feedEntry") {
                    resolver { id: ID ->
                        val data = FeedEntryHelper.feedEntryDao.getById(id.value)
                        data?.toModel()
                    }
                }
                query("notes") {
                    configure {
                        executor = Executor.DataLoaderPrepared
                    }
                    resolver { offset: Int, limit: Int, query: String ->
                        val items = NoteHelper.search(QueryHelper.prepareQuery(query), limit, offset)
                        items.map { it.toModel() }
                    }
                    type<Note> {
                        dataProperty("tags") {
                            prepare { item -> item.id.value }
                            loader { ids ->
                                TagsLoader.load(ids, DataType.NOTE)
                            }
                        }
                    }
                }
                query("noteCount") {
                    resolver { query: String ->
                        NoteHelper.count(QueryHelper.prepareQuery(query))
                    }
                }
                query("note") {
                    resolver { id: ID ->
                        val data = NoteHelper.getById(id.value)
                        data?.toModel()
                    }
                }
                query("latestExchangeRates") {
                    resolver { live: Boolean ->
                        if (live || UIDataCache.current().latestExchangeRates == null) {
                            ExchangeHelper.getRates()
                        }
                        UIDataCache.current().latestExchangeRates?.toModel()
                    }
                }
                query("deviceInfo") {
                    resolver { ->
                        val apiPermissions = ApiPermissionsPreference.getAsync(MainApp.instance)
                        val readPhoneNumber = apiPermissions.contains(Permission.READ_PHONE_STATE.toString()) && apiPermissions.contains(Permission.READ_PHONE_NUMBERS.toString())
                        DeviceInfoHelper.getDeviceInfo(MainApp.instance, readPhoneNumber).toModel()
                    }
                }
                query("battery") {
                    resolver { ->
                        BatteryReceiver.get(MainApp.instance).toModel()
                    }
                }
                query("app") {
                    resolver { ->
                        val context = MainApp.instance
                        val apiPermissions = ApiPermissionsPreference.getAsync(context)
                        App(
                            usbConnected = PlugInControlReceiver.isUSBConnected(context),
                            urlToken = TempData.urlToken,
                            externalFilesDir = context.getExternalFilesDir(null)?.path ?: "",
                            if (TempData.demoMode) "Demo phone" else PhoneHelper.getDeviceName(context),
                            PhoneHelper.getBatteryPercentage(context),
                            BuildConfig.VERSION_CODE,
                            android.os.Build.VERSION.SDK_INT,
                            BuildConfig.isPro,
                            Permission.values().filter { apiPermissions.contains(it.name) && it.can(MainApp.instance) },
                            AudioPlaylistPreference.getValueAsync(context).map { it.toModel() },
                            AudioPlayModePreference.getValueAsync(context),
                            AudioPlayingPreference.getValueAsync(context)?.path ?: "",
                            context.allowSensitivePermissions(),
                            sdcardPath = FileSystemHelper.getSDCardPath(context),
                            usbDiskPaths = FileSystemHelper.getUsbDiskPaths(),
                            internalStoragePath = FileSystemHelper.getInternalStoragePath(),
                            downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
                        )
                    }
                }
                query("fileIds") {
                    resolver { paths: List<String> ->
                        paths.map { FileHelper.getFileId(it) }
                    }
                }
                mutation("setTempValue") {
                    resolver { key: String, value: String ->
                        TempHelper.setValue(key, value)
                        TempValue(key, value)
                    }
                }
                mutation("uninstallPackages") {
                    resolver { ids: List<ID> ->
                        ids.forEach {
                            PackageHelper.uninstall(MainActivity.instance.get()!!, it.value)
                        }
                        true
                    }
                }
                mutation("cancelNotifications") {
                    resolver { ids: List<ID> ->
                        sendEvent(CancelNotificationsEvent(ids.map { it.value }.toSet()))
                        true
                    }
                }
                mutation("updateAIChatConfig") {
                    resolver { chatGPTApiKey: String ->
                        val context = MainApp.instance
                        ChatGPTApiKeyPreference.putAsync(context, chatGPTApiKey)
                        AIChatConfig(chatGPTApiKey)
                    }
                }
                mutation("createChatItem") {
                    resolver { content: String ->
                        val item =
                            ChatHelper.sendAsync(
                                DChat.parseContent(content),
                            )
                        sendEvent(HttpServerEvents.MessageCreatedEvent(arrayListOf(item)))
                        arrayListOf(item).map { it.toModel() }
                    }
                }
                mutation("deleteChatItem") {
                    resolver { id: ID ->
                        val item = ChatHelper.getAsync(id.value)
                        if (item != null) {
                            ChatHelper.deleteAsync(MainApp.instance, item.id, item.content.value)
                        }
                        true
                    }
                }
                mutation("createAIChat") {
                    resolver { id: ID, message: String, isMe: Boolean ->
                        if (ChatGPTApiKeyPreference.getAsync(MainApp.instance).isEmpty()) {
                            throw Exception("no_api_key")
                        }
                        val items = AIChatHelper.createChatItemsAsync(id.value, isMe, message)
                        if (isMe) {
                            sendEvent(AIChatCreatedEvent(items[0]))
                        }
                        items.map { it.toModel() }
                    }
                }
                mutation("relaunchApp") {
                    resolver { ->
                        coIO {
                            AppHelper.relaunch(MainApp.instance)
                        }
                        true
                    }
                }
                mutation("deleteAIChats") {
                    resolver { query: String ->
                        AIChatHelper.deleteAsync(query)
                        true
                    }
                }
                mutation("deleteContacts") {
                    resolver { query: String ->
                        val context = MainApp.instance
                        Permission.WRITE_CONTACTS.checkAsync(context)
                        val newIds = ContactHelper.getIds(context, query)
                        TagHelper.deleteTagRelationByKeys(newIds, DataType.CONTACT)
                        ContactHelper.deleteByIds(context, newIds)
                        true
                    }
                }
                mutation("fetchFeedContent") {
                    resolver { id: ID ->
                        val feed = FeedEntryHelper.feedEntryDao.getById(id.value)
                        feed?.fetchContentAsync()
                        feed?.toModel()
                    }
                }
                mutation("updateContact") {
                    resolver { id: ID, input: ContactInput ->
                        Permission.WRITE_CONTACTS.checkAsync(MainApp.instance)
                        ContactHelper.update(id.value, input)
                        ContactHelper.get(MainApp.instance, id.value)?.toModel()
                    }
                }
                mutation("createContact") {
                    resolver { input: ContactInput ->
                        Permission.WRITE_CONTACTS.checkAsync(MainApp.instance)
                        val id = ContactHelper.create(input)
                        if (id.isEmpty()) null else ContactHelper.get(MainApp.instance, id)?.toModel()
                    }
                }
                mutation("createTag") {
                    resolver { type: DataType, name: String ->
                        val id =
                            TagHelper.addOrUpdate("") {
                                this.name = name
                                this.type = type.value
                            }
                        TagHelper.get(id)?.toModel()
                    }
                }
                mutation("updateTag") {
                    resolver { id: ID, name: String ->
                        TagHelper.addOrUpdate(id.value) {
                            this.name = name
                        }
                        TagHelper.get(id.value)?.toModel()
                    }
                }
                mutation("deleteTag") {
                    resolver { id: ID ->
                        TagHelper.deleteTagRelationsByTagId(id.value)
                        TagHelper.delete(id.value)
                        true
                    }
                }
                mutation("syncFeeds") {
                    resolver { id: ID? ->
                        FeedFetchWorker.oneTimeRequest(id?.value ?: "")
                        true
                    }
                }
                mutation("updateFeed") {
                    resolver { id: ID, name: String, fetchContent: Boolean ->
                        FeedHelper.updateAsync(id.value) {
                            this.name = name
                            this.fetchContent = fetchContent
                        }
                        FeedHelper.getById(id.value)?.toModel()
                    }
                }
                mutation("startScreenMirror") {
                    resolver { ->
                        sendEvent(StartScreenMirrorEvent())
                        true
                    }
                }
                mutation("stopScreenMirror") {
                    resolver { ->
                        ScreenMirrorService.instance?.stop()
                        ScreenMirrorService.instance = null
                        true
                    }
                }
                mutation("createContactGroup") {
                    resolver { name: String, accountName: String, accountType: String ->
                        Permission.WRITE_CONTACTS.checkAsync(MainApp.instance)
                        GroupHelper.create(name, accountName, accountType).toModel()
                    }
                }

                mutation("call") {
                    resolver { number: String ->
                        Permission.CALL_PHONE.checkAsync(MainApp.instance)
                        CallHelper.call(MainActivity.instance.get()!!, number)
                        true
                    }
                }
                mutation("updateContactGroup") {
                    resolver { id: ID, name: String ->
                        Permission.WRITE_CONTACTS.checkAsync(MainApp.instance)
                        GroupHelper.update(id.value, name)
                        ContactGroup(id, name)
                    }
                }
                mutation("deleteContactGroup") {
                    resolver { id: ID ->
                        Permission.WRITE_CONTACTS.checkAsync(MainApp.instance)
                        GroupHelper.delete(id.value)
                        true
                    }
                }

                mutation("deleteCalls") {
                    resolver { query: String ->
                        val context = MainApp.instance
                        Permission.WRITE_CALL_LOG.checkAsync(context)
                        val newIds = CallHelper.getIds(context, query)
                        TagHelper.deleteTagRelationByKeys(newIds, DataType.CALL)
                        CallHelper.deleteByIds(context, newIds)
                        true
                    }
                }
                mutation("deleteFiles") {
                    resolver { paths: List<String> ->
                        val context = MainApp.instance
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(context)
                        paths.forEach {
                            java.io.File(it).deleteRecursively()
                        }
                        context.scanFileByConnection(paths.toTypedArray())
                        true
                    }
                }
                mutation("createDir") {
                    resolver { path: String ->
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(MainApp.instance)
                        FileSystemHelper.createDirectory(path).toModel()
                    }
                }
                mutation("renameFile") {
                    resolver { path: String, name: String ->
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(MainApp.instance)
                        val dst = FileHelper.rename(path, name)
                        if (dst != null) {
                            MainApp.instance.scanFileByConnection(path)
                            MainApp.instance.scanFileByConnection(dst)
                        }
                        dst != null
                    }
                }
                mutation("copyFile") {
                    resolver { src: String, dst: String, overwrite: Boolean ->
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(MainApp.instance)
                        val dstFile = java.io.File(dst)
                        if (overwrite || !dstFile.exists()) {
                            java.io.File(src).copyRecursively(dstFile, overwrite)
                        } else {
                            java.io.File(src)
                                .copyRecursively(java.io.File(dstFile.newPath()), false)
                        }
                        MainApp.instance.scanFileByConnection(dstFile)
                        true
                    }
                }
                mutation("playAudio") {
                    resolver { path: String ->
                        val context = MainApp.instance
                        val audio = DPlaylistAudio.fromPath(context, path)
                        AudioPlayingPreference.putAsync(context, audio)
                        if (!AudioPlaylistPreference.getValueAsync(context).any { it.path == audio.path }) {
                            AudioPlaylistPreference.addAsync(context, listOf(audio))
                        }
                        audio.toModel()
                    }
                }
                mutation("updateAudioPlayMode") {
                    resolver { mode: MediaPlayMode ->
                        AudioPlayModePreference.putAsync(MainApp.instance, mode)
                        true
                    }
                }
                mutation("clearAudioPlaylist") {
                    resolver { ->
                        val context = MainApp.instance
                        AudioPlayer.instance.pause()
                        AudioPlayingPreference.putAsync(context, null)
                        AudioPlaylistPreference.putAsync(context, arrayListOf())
                        sendEvent(ClearAudioPlaylistEvent())
                        true
                    }
                }
                mutation("deletePlaylistAudio") {
                    resolver { path: String ->
                        AudioPlaylistPreference.deleteAsync(MainApp.instance, setOf(path))
                        true
                    }
                }
                mutation("saveNote") {
                    resolver { id: ID, input: NoteInput ->
                        val newId =
                            NoteHelper.addOrUpdateAsync(id.value) {
                                title = input.title
                                content = input.content
                            }
                        NoteHelper.getById(newId)?.toModel()
                    }
                }
                mutation("trashNotes") {
                    resolver { query: String ->
                        val ids = NoteHelper.getIdsAsync(query)
                        TagHelper.deleteTagRelationByKeys(ids, DataType.NOTE)
                        NoteHelper.trashAsync(ids)
                        true
                    }
                }
                mutation("untrashNotes") {
                    resolver { query: String ->
                        val ids = NoteHelper.getIdsAsync(query)
                        NoteHelper.untrashAsync(ids)
                        true
                    }
                }
                mutation("deleteNotes") {
                    resolver { query: String ->
                        val ids = NoteHelper.getIdsAsync(query)
                        TagHelper.deleteTagRelationByKeys(ids, DataType.NOTE)
                        NoteHelper.deleteAsync(ids)
                        true
                    }
                }
                mutation("deleteFeedEntries") {
                    resolver { query: String ->
                        val ids = FeedEntryHelper.getIdsAsync(query)
                        TagHelper.deleteTagRelationByKeys(ids, DataType.FEED_ENTRY)
                        FeedEntryHelper.deleteAsync(ids)
                        true
                    }
                }
                mutation("addPlaylistAudios") {
                    resolver { query: String ->
                        val context = MainApp.instance
                        // 1000 items at most
                        val items = AudioHelper.search(context, query, 1000, 0, AudioSortByPreference.getValueAsync(context))
                        AudioPlaylistPreference.addAsync(context, items.map { DPlaylistAudio(it.title, it.path, it.artist, it.duration) })
                        true
                    }
                }
                mutation("createFeed") {
                    resolver { url: String, fetchContent: Boolean ->
                        val syndFeed = withIO { FeedHelper.fetchAsync(url) }
                        val id =
                            FeedHelper.addAsync {
                                this.url = url
                                this.name = syndFeed.title ?: ""
                                this.fetchContent = fetchContent
                            }
                        FeedFetchWorker.oneTimeRequest(id)
                        sendEvent(ActionEvent(ActionSourceType.FEED, ActionType.CREATED, setOf(id)))
                        FeedHelper.getById(id)
                    }
                }
                mutation("importFeeds") {
                    resolver { content: String ->
                        FeedHelper.import(StringReader(content))
                        true
                    }
                }
                mutation("exportFeeds") {
                    resolver { ->
                        val writer = StringWriter()
                        FeedHelper.export(writer)
                        writer.toString()
                    }
                }
                mutation("addToTags") {
                    resolver { type: DataType, tagIds: List<ID>, query: String ->
                        var items = listOf<TagRelationStub>()
                        val context = MainApp.instance
                        when (type) {
                            DataType.AUDIO -> {
                                items = AudioHelper.getTagRelationStubs(context, query)
                            }

                            DataType.VIDEO -> {
                                items = VideoHelper.getTagRelationStubs(context, query)
                            }

                            DataType.IMAGE -> {
                                items = ImageHelper.getTagRelationStubs(context, query)
                            }

                            DataType.SMS -> {
                                items = SmsHelper.getIds(context, query).map { TagRelationStub(it) }
                            }

                            DataType.CONTACT -> {
                                items = ContactHelper.getIds(context, query).map { TagRelationStub(it) }
                            }

                            DataType.NOTE -> {
                                items = NoteHelper.getIdsAsync(query).map { TagRelationStub(it) }
                            }

                            DataType.FEED_ENTRY -> {
                                items = FeedEntryHelper.getIdsAsync(query).map { TagRelationStub(it) }
                            }

                            DataType.CALL -> {
                                items = CallHelper.getIds(context, query).map { TagRelationStub(it) }
                            }

                            DataType.AI_CHAT -> {
                                items = AIChatHelper.getIdsAsync(query).map { TagRelationStub(it) }
                            }

                            else -> {}
                        }

                        tagIds.forEach { tagId ->
                            val existingKeys = withIO { TagHelper.getKeysByTagId(tagId.value) }
                            val newItems = items.filter { !existingKeys.contains(it.key) }
                            if (newItems.isNotEmpty()) {
                                TagHelper.addTagRelations(
                                    newItems.map {
                                        it.toTagRelation(tagId.value, type)
                                    },
                                )
                            }
                        }
                        true
                    }
                }
                mutation("updateTagRelations") {
                    resolver { type: DataType, item: TagRelationStub, addTagIds: List<ID>, removeTagIds: List<ID> ->
                        addTagIds.forEach { tagId ->
                            TagHelper.addTagRelations(
                                arrayOf(item).map {
                                    it.toTagRelation(tagId.value, type)
                                },
                            )
                        }
                        if (removeTagIds.isNotEmpty()) {
                            TagHelper.deleteTagRelationByKeysTagIds(setOf(item.key), removeTagIds.map { it.value }.toSet())
                        }
                        true
                    }
                }
                mutation("removeFromTags") {
                    resolver { type: DataType, tagIds: List<ID>, query: String ->
                        val context = MainApp.instance
                        var ids = setOf<String>()
                        when (type) {
                            DataType.AUDIO -> {
                                ids = AudioHelper.getIds(context, query)
                            }

                            DataType.VIDEO -> {
                                ids = VideoHelper.getIds(context, query)
                            }

                            DataType.IMAGE -> {
                                ids = ImageHelper.getIds(context, query)
                            }

                            DataType.SMS -> {
                                ids = SmsHelper.getIds(context, query)
                            }

                            DataType.CONTACT -> {
                                ids = ContactHelper.getIds(context, query)
                            }

                            DataType.NOTE -> {
                                ids = NoteHelper.getIdsAsync(query)
                            }

                            DataType.FEED_ENTRY -> {
                                ids = FeedEntryHelper.getIdsAsync(query)
                            }

                            DataType.CALL -> {
                                ids = CallHelper.getIds(context, query)
                            }

                            DataType.AI_CHAT -> {
                                ids = AIChatHelper.getIdsAsync(query)
                            }

                            else -> {}
                        }

                        TagHelper.deleteTagRelationByKeysTagIds(ids, tagIds.map { it.value }.toSet())
                        true
                    }
                }
                mutation("deleteMediaItems") {
                    resolver { type: DataType, query: String ->
                        var ids = setOf<String>()
                        val context = MainApp.instance
                        when (type) {
                            DataType.AUDIO -> {
                                ids = AudioHelper.getIds(context, query)
                                val paths = AudioHelper.deleteRecordsAndFilesByIds(context, ids)
                                AudioPlaylistPreference.deleteAsync(context, paths)
                            }

                            DataType.VIDEO -> {
                                ids = VideoHelper.getIds(context, query)
                                val paths = VideoHelper.deleteRecordsAndFilesByIds(context, ids)
                                VideoPlaylistPreference.deleteAsync(context, paths)
                            }

                            DataType.IMAGE -> {
                                ids = ImageHelper.getIds(context, query)
                                ImageHelper.deleteRecordsAndFilesByIds(context, ids)
                            }

                            else -> {
                            }
                        }
                        TagHelper.deleteTagRelationByKeys(ids, type)
                        true
                    }
                }
                mutation("moveFile") {
                    resolver { src: String, dst: String, overwrite: Boolean ->
                        Permission.WRITE_EXTERNAL_STORAGE.checkAsync(MainApp.instance)
                        val dstFile = java.io.File(dst)
                        if (overwrite || !dstFile.exists()) {
                            Path(src).moveTo(Path(dst), overwrite)
                        } else {
                            Path(src).moveTo(Path(dstFile.newPath()), false)
                        }
                        MainApp.instance.scanFileByConnection(src)
                        MainApp.instance.scanFileByConnection(dstFile)
                        true
                    }
                }
                mutation("deleteFeed") {
                    resolver { id: ID ->
                        val newIds = setOf(id.value)
                        val entryIds = FeedEntryHelper.feedEntryDao.getIds(newIds)
                        if (entryIds.isNotEmpty()) {
                            TagHelper.deleteTagRelationByKeys(entryIds.toSet(), DataType.FEED_ENTRY)
                            FeedEntryHelper.feedEntryDao.deleteByFeedIds(newIds)
                        }
                        FeedHelper.deleteAsync(newIds)
                        true
                    }
                }
                mutation("syncFeedContent") {
                    resolver { id: ID ->
                        val feedEntry = FeedEntryHelper.feedEntryDao.getById(id.value)
                        feedEntry?.fetchContentAsync()
                        feedEntry?.toModel()
                    }
                }
                enum<MediaPlayMode>()
                enum<DataType>()
                enum<Permission>()
                enum<FileSortBy>()
                stringScalar<Instant> {
                    deserialize = { value: String -> value.toInstant() }
                    serialize = Instant::toString
                }

                stringScalar<ID> {
                    deserialize = { it: String -> ID(it) }
                    serialize = { it: ID -> it.toString() }
                }
            }
        }

        internal var schemaBlock: (SchemaBuilder.() -> Unit)? = null
    }

    companion object Feature : BaseApplicationPlugin<Application, Configuration, SXGraphQL> {
        override val key = AttributeKey<SXGraphQL>("KGraphQL")

        private suspend fun executeGraphqlQL(
            schema: Schema,
            query: String,
            useBoxApi: Boolean,
        ): String {
            if (useBoxApi) {
                return BoxProxyApi.executeAsync(query, HttpApiTimeout.MEDIUM_SECONDS)
            }
            val request = Json.decodeFromString(GraphqlRequest.serializer(), query)
            return schema.execute(request.query, request.variables.toString(), context {})
        }

        override fun install(
            pipeline: Application,
            configure: Configuration.() -> Unit,
        ): SXGraphQL {
            val config = Configuration().apply(configure)
            val schema =
                KGraphQL.schema {
                    configuration = config
                    config.schemaBlock?.invoke(this)
                }

            val routing: Routing.() -> Unit = {
                route("/graphql") {
                    post {
                        if (!TempData.webEnabled) {
                            call.response.status(HttpStatusCode.Forbidden)
                            return@post
                        }
                        val clientId = call.request.header("c-id") ?: ""
                        val useBoxApi = call.request.header("x-box-api") == "true"
                        if (clientId.isNotEmpty()) {
                            val token = HttpServerManager.tokenCache[clientId]
                            if (token == null) {
                                call.response.status(HttpStatusCode.Unauthorized)
                                return@post
                            }

                            var requestStr = ""
                            val decryptedBytes = CryptoHelper.aesDecrypt(token, call.receive())
                            if (decryptedBytes != null) {
                                requestStr = decryptedBytes.decodeToString()
                            }
                            if (requestStr.isEmpty()) {
                                call.response.status(HttpStatusCode.Unauthorized)
                                return@post
                            }

                            LogCat.d("[Request] $requestStr")
                            HttpServerManager.clientRequestTs[clientId] = System.currentTimeMillis() // record the api request time
                            val r = executeGraphqlQL(schema, requestStr, useBoxApi)
                            call.respondBytes(CryptoHelper.aesEncrypt(token, r))
                        } else {
                            val authStr = call.request.header("authorization")?.split(" ")
                            val token = AuthDevTokenPreference.getAsync(MainApp.instance)
                            if (token.isEmpty() || authStr?.get(1) != token) {
                                call.respondText(
                                    """{"errors":[{"message":"Unauthorized"}]}""",
                                    contentType = ContentType.Application.Json,
                                )
                                return@post
                            }

                            val requestStr = call.receiveText()
                            LogCat.d("[Request] $requestStr")
                            HttpServerManager.clientRequestTs[clientId] = System.currentTimeMillis() // record the api request time
                            val r = executeGraphqlQL(schema, requestStr, useBoxApi)
                            call.respondText(r, contentType = ContentType.Application.Json)
                        }
                    }
                }
            }

            pipeline.pluginOrNull(Routing)?.apply(routing)

            pipeline.intercept(ApplicationCallPipeline.Monitoring) {
                try {
                    coroutineScope {
                        proceed()
                    }
                } catch (e: Throwable) {
                    if (e is GraphQLError) {
                        val clientId = call.request.header("c-id") ?: ""
                        if (clientId.isNotEmpty()) {
                            val token = HttpServerManager.tokenCache[clientId]
                            if (token != null) {
                                call.respondBytes(CryptoHelper.aesEncrypt(token, e.serialize()))
                            } else {
                                call.response.status(HttpStatusCode.Unauthorized)
                            }
                        } else {
                            context.respond(HttpStatusCode.OK, e.serialize())
                        }
                    } else {
                        throw e
                    }
                }
            }
            return SXGraphQL(schema)
        }

        private fun GraphQLError.serialize(): String =
            buildJsonObject {
                put(
                    "errors",
                    buildJsonArray {
                        addJsonObject {
                            put("message", message)
                            put(
                                "locations",
                                buildJsonArray {
                                    locations?.forEach {
                                        addJsonObject {
                                            put("line", it.line)
                                            put("column", it.column)
                                        }
                                    }
                                },
                            )
                            put(
                                "path",
                                buildJsonArray {
                                    // TODO: Build this path. https://spec.graphql.org/June2018/#example-90475
                                },
                            )
                        }
                    },
                )
            }.toString()
    }
}
