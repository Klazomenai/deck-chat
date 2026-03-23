package dev.klazomenai.deckchat

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineListener
import java.io.File

/**
 * Matrix client implementation backed by matrix-rust-sdk (UniFFI Kotlin bindings).
 *
 * Uses Simplified Sliding Sync (MSC4186) exclusively — no /sync v2 fallback.
 * E2EE is automatic with SQLite store persistence (SDK manages encryption internally).
 * Sends messages via [Room.sendRaw] for standard m.room.message events.
 * Receives messages via [TimelineListener] and parses body-prefix convention.
 *
 * Session tokens are persisted via [SecureStorage].
 */
class RustMatrixClient(
    private val context: Context,
    private val storage: SecureStorage,
) : MatrixClient {

    private var client: Client? = null
    private var syncService: SyncService? = null
    private var timelineHandle: TaskHandle? = null
    private var timeline: Timeline? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun login(homeserverUrl: String, username: String, password: String) {
        val client = buildClient(homeserverUrl)
        client.login(username, password, "DeckChat Android", null)

        val session = client.session()
        persistSession(session)
        this.client = client
    }

    override suspend fun restoreSession() {
        val homeserverUrl = storage.homeserverUrl
            ?: throw IllegalStateException("No homeserver URL stored")

        val client = buildClient(homeserverUrl)
        val session = loadSession()
            ?: throw IllegalStateException("No session stored")

        client.restoreSession(session)
        this.client = client
    }

    override suspend fun sendMessage(roomId: String, text: String) {
        val room = requireClient().getRoom(roomId)
            ?: throw IllegalArgumentException("Room not found: $roomId")

        val content = JSONObject().apply {
            put("msgtype", "m.text")
            put("body", text)
        }
        room.sendRaw("m.room.message", content.toString())
    }

    override fun startSync(onMessage: (CrewMessage) -> Unit) {
        if (syncService != null) return // already syncing

        val client = requireClient()
        this.onMessageCallback = onMessage
        scope.launch {
            val service = client.syncService().finish()
            synchronized(this@RustMatrixClient) {
                if (syncService != null) {
                    // stop() was called between launch and here — don't start
                    return@launch
                }
                syncService = service
            }
            service.start()
        }
    }

    override suspend fun listenToRoom(roomId: String) {
        timelineHandle?.close()
        timelineHandle = null

        val room = requireClient().getRoom(roomId)
            ?: throw IllegalArgumentException("Room not found: $roomId")
        val tl = room.timeline()
        timeline = tl

        timelineHandle = tl.addListener(object : TimelineListener {
            override fun onUpdate(diff: List<TimelineDiff>) {
                for (d in diff) {
                    extractNewItems(d).forEach { item ->
                        processTimelineItem(item)
                    }
                }
            }
        })
    }

    /**
     * Stops syncing. Does NOT clear the session — call [SecureStorage.clearSession]
     * to log out fully. After stop(), [isLoggedIn] remains true if the session is
     * still stored; call [startSync] to resume.
     */
    override suspend fun stop() {
        timelineHandle?.close()
        timelineHandle = null
        timeline = null
        syncService?.stop()
        syncService = null
    }

    override fun isLoggedIn(): Boolean = client != null && storage.hasSession()

    // --- Internal ---

    private var onMessageCallback: ((CrewMessage) -> Unit)? = null

    private suspend fun buildClient(homeserverUrl: String): Client {
        val dataDir = File(context.filesDir, "matrix-data").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "matrix-cache").apply { mkdirs() }

        // SDK manages SQLite store encryption internally via sessionPaths.
        // No explicit passphrase method exists on ClientBuilder.
        return ClientBuilder()
            .homeserverUrl(homeserverUrl)
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
            .sessionPaths(dataDir.absolutePath, cacheDir.absolutePath)
            .build()
    }

    private fun persistSession(session: Session) {
        storage.accessToken = session.accessToken
        storage.refreshToken = session.refreshToken
        storage.userId = session.userId
        storage.deviceId = session.deviceId
        storage.homeserverUrl = session.homeserverUrl
        storage.slidingSyncVersion = session.slidingSyncVersion.toString()
    }

    private fun loadSession(): Session? {
        val accessToken = storage.accessToken ?: return null
        val userId = storage.userId ?: return null
        val deviceId = storage.deviceId ?: return null
        val homeserverUrl = storage.homeserverUrl ?: return null

        return Session(
            accessToken = accessToken,
            refreshToken = storage.refreshToken,
            userId = userId,
            deviceId = deviceId,
            homeserverUrl = homeserverUrl,
            oidcData = null,
            slidingSyncVersion = org.matrix.rustcomponents.sdk.SlidingSyncVersion.NATIVE,
        )
    }

    private fun requireClient(): Client {
        return client ?: throw IllegalStateException("Not logged in — call login() or restoreSession() first")
    }

    private fun extractNewItems(diff: TimelineDiff): List<TimelineItem> {
        return when (diff) {
            is TimelineDiff.Append -> diff.values
            is TimelineDiff.PushBack -> listOf(diff.value)
            is TimelineDiff.PushFront -> listOf(diff.value)
            is TimelineDiff.Insert -> listOf(diff.value)
            else -> emptyList()
        }
    }

    private fun processTimelineItem(item: TimelineItem) {
        val event = item.asEvent() ?: return
        if (event.isOwn) return
        val content = event.content
        if (content !is TimelineItemContent.MsgLike) return
        val kind = content.content.kind
        if (kind !is MsgLikeKind.Message) return
        val msgType = kind.content.msgType
        if (msgType !is MessageType.Text) return

        val body = msgType.content.body
        val crewMessage = parseCrewMessage(body, event.sender) ?: return
        onMessageCallback?.invoke(crewMessage)
    }
}
