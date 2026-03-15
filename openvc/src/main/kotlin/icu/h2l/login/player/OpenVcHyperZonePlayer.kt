package icu.h2l.login.player

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.util.GameProfile
import icu.h2l.api.db.Profile
import icu.h2l.api.player.HyperZonePlayer
import icu.h2l.login.HyperZoneLoginMain
import icu.h2l.api.util.RemapUtils
import net.elytrium.limboapi.api.player.LimboPlayer
import net.kyori.adventure.text.Component
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class OpenVcHyperZonePlayer(
//    最开始客户端传入的，不可信
    override var userName: String,
    override var uuid: UUID,
) : HyperZonePlayer {

    private var proxyPlayer: Player? = null

    @Volatile
    var profileId: UUID? = null

    private val isVerifiedState = AtomicBoolean(false)
    private val hasSpawned = AtomicBoolean(false)
    private val messageQueue = ConcurrentLinkedQueue<Component>()

    @Volatile
    private var limboPlayer: LimboPlayer? = null

    /**
     * 存储 Mojang 验证返回的 GameProfile properties（皮肤等）
     */
    @Volatile
    var onlineProfileProperties: List<GameProfile.Property> = emptyList()

    private val databaseHelper = HyperZoneLoginMain.getInstance().databaseHelper

    @Volatile
    private var profileProperties: List<GameProfile.Property> = emptyList()

    init {
        profileId = databaseHelper
            .getProfileByNameOrUuid(userName, uuid)
            ?.id
        profileId?.let { _ ->
            val profile = getProfile()
            userName = profile!!.name
            uuid = profile.uuid
        }
    }

    fun update(player: Player) {
        proxyPlayer = player
    }

    fun onSpawn(player: LimboPlayer) {
        limboPlayer = player
        update(player.proxyPlayer)
        hasSpawned.set(true)

        while (messageQueue.isNotEmpty()) {
            val message = messageQueue.poll() ?: continue
            proxyPlayer?.sendMessage(message)
        }
    }

    override fun canRegister(): Boolean {
        return profileId == null
    }

    override fun register(userName: String?, uuid: UUID?): Profile {
        val resolvedName = userName ?: this.userName
        val remapPrefix = HyperZoneLoginMain.getRemapConfig().prefix
        val resolvedUuid = uuid ?: RemapUtils.genUUID(resolvedName, remapPrefix)

        val existing = databaseHelper.getProfileByNameOrUuid(resolvedName, resolvedUuid)
        if (existing != null) {
            throw IllegalStateException("玩家 $resolvedName 已存在 Profile，无法重复注册")
        }

        val profile = Profile(
            id = RemapUtils.genProfileUUID(resolvedName),
            name = resolvedName,
            uuid = resolvedUuid
        )

        val created = databaseHelper.createProfile(profile.id, profile.name, profile.uuid)
        if (!created) {
            throw IllegalStateException("玩家 ${userName} 注册失败，数据库写入失败")
        }

        profileId = profile.id

        return profile
    }

    override fun getProfile(): Profile? {
        val currentProfileId = profileId ?: return null
        return databaseHelper.getProfile(currentProfileId)
    }


    override fun updateUuid(uuid: UUID): Boolean {
        val currentProfileId = profileId ?: return false
        if (uuid == this.uuid) {
            return true
        }
        val updated = databaseHelper.updateProfileUuid(currentProfileId, uuid)
        if (updated) {
            this.uuid = uuid
        }
        return updated
    }


    override fun updateGameProfileProperties(properties: List<GameProfile.Property>): Boolean {
        profileProperties = properties
        return true
    }

    override fun isVerified(): Boolean {
        return isVerifiedState.get()
    }

    override fun canBind(): Boolean {
        return isVerified()
    }

    override fun overVerify() {
        if (isVerifiedState.compareAndSet(false, true)) {
            limboPlayer?.disconnect()
        }
    }

    fun exitLimbo() {
        limboPlayer?.disconnect()
    }

    override fun sendMessage(message: Component) {
        if (hasSpawned.get()) {
            proxyPlayer?.sendMessage(message)
            return
        }

        messageQueue.offer(message)
    }

    override fun setOnlineProperties(properties: List<GameProfile.Property>) {
        onlineProfileProperties = properties
    }

    override fun getGameProfile(): GameProfile {
        val resolvedProfile = getProfile()
        return GameProfile(
            resolvedProfile!!.uuid,
            resolvedProfile.name,
            onlineProfileProperties
        )
    }
}
