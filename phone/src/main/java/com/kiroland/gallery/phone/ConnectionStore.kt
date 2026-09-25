package com.kiroland.gallery.phone

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import com.kiroland.gallery.shared.Protocol

@Serializable
data class TvConnection(
    /** NSD service name; used to find the TV again when its IP changes. */
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)

data class SyncStatus(
    val running: Boolean = false,
    val lastSuccessAt: Long = 0,
    val message: String? = null,
    val needsRepair: Boolean = false,
)

class ConnectionStore(context: Context) {
    private val sp = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    private val _tv = MutableStateFlow(
        sp.getString("tv", null)?.let { runCatching { Protocol.json.decodeFromString<TvConnection>(it) }.getOrNull() }
    )
    val tv: StateFlow<TvConnection?> = _tv

    private val _status = MutableStateFlow(SyncStatus(lastSuccessAt = sp.getLong("lastSuccess", 0)))
    val status: StateFlow<SyncStatus> = _status

    fun save(tv: TvConnection?) {
        sp.edit().apply {
            if (tv == null) remove("tv") else putString("tv", Protocol.json.encodeToString(TvConnection.serializer(), tv))
        }.apply()
        _tv.value = tv
    }

    fun updateStatus(transform: (SyncStatus) -> SyncStatus) {
        val new = transform(_status.value)
        if (new.lastSuccessAt != _status.value.lastSuccessAt) sp.edit().putLong("lastSuccess", new.lastSuccessAt).apply()
        _status.value = new
    }

    /** Size photos are prepared for; 4K until we know the paired TV. */
    val targetSize: Pair<Int, Int>
        get() = _tv.value?.takeIf { it.screenWidth > 0 }?.let { it.screenWidth to it.screenHeight } ?: (3840 to 2160)
}
