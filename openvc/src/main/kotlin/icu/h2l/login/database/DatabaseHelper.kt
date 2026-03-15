package icu.h2l.login.database

import icu.h2l.api.db.Profile
import icu.h2l.api.log.warn
import icu.h2l.login.manager.DatabaseManager
import icu.h2l.api.util.RemapUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 数据库操作示例和帮助类
 */
class DatabaseHelper(
    private val manager: DatabaseManager
) {

    private val profileTable = manager.getProfileTable()

    private val profileCacheById = ConcurrentHashMap<UUID, Profile>()
    private val profileCacheByName = ConcurrentHashMap<String, Profile>()
    private val profileCacheByUuid = ConcurrentHashMap<UUID, Profile>()

    private fun cacheProfile(profile: Profile) {
        profileCacheById[profile.id] = profile
        profileCacheByName[profile.name.lowercase()] = profile
        profileCacheByUuid[profile.uuid] = profile
    }

    private fun removeProfileCache(profile: Profile) {
        profileCacheById.remove(profile.id)
        profileCacheByName.remove(profile.name.lowercase())
        profileCacheByUuid.remove(profile.uuid)
    }

    private fun loadProfileById(profileId: UUID): Profile? {
        return manager.executeTransaction {
            profileTable.selectAll().where { profileTable.id eq profileId }
                .limit(1)
                .map {
                    Profile(
                        id = it[profileTable.id],
                        name = it[profileTable.name],
                        uuid = it[profileTable.uuid]
                    )
                }
                .firstOrNull()
        }
    }

    private fun loadProfileByNameOrUuid(name: String, uuid: UUID): Profile? {
        return manager.executeTransaction {
            profileTable.selectAll().where {
                (profileTable.name eq name) or (profileTable.uuid eq uuid)
            }.limit(1)
                .map {
                    Profile(
                        id = it[profileTable.id],
                        name = it[profileTable.name],
                        uuid = it[profileTable.uuid]
                    )
                }
                .firstOrNull()
        }
    }
    
    /**
     * 根据 name 或 uuid 查找档案（OR 查询）
     * 
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return 档案ID，如果不存在返回 null
     */
    fun findProfileByNameOrUuid(name: String, uuid: UUID): UUID? {
        return getProfileByNameOrUuid(name, uuid)?.id
    }

    /**
     * 根据 name 或 uuid 获取档案（带缓存）
     *
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return 档案信息，如果不存在返回 null
     */
    fun getProfileByNameOrUuid(name: String, uuid: UUID): Profile? {
        val profileId = RemapUtils.genProfileUUID(name)
        profileCacheById[profileId]?.let { return it }

        val loadedById = loadProfileById(profileId)
        if (loadedById != null) {
            cacheProfile(loadedById)
            return loadedById
        }

        profileCacheByName[name.lowercase()]?.let {
            warn { "Profile 命中 name/uuid 回退逻辑: name=$name uuid=$uuid" }
            return it
        }
        profileCacheByUuid[uuid]?.let {
            warn { "Profile 命中 name/uuid 回退逻辑: name=$name uuid=$uuid" }
            return it
        }

        val loaded = loadProfileByNameOrUuid(name, uuid) ?: return null
        warn { "Profile 命中 name/uuid 回退逻辑: name=$name uuid=$uuid" }
        cacheProfile(loaded)
        return loaded
    }
    
    /**
     * 创建新的游戏档案
     * 
     * @param id 档案ID
     * @param name 玩家名
     * @param uuid 玩家UUID
     * @return 是否创建成功
     */
    fun createProfile(id: UUID, name: String, uuid: UUID): Boolean {
        return try {
            manager.executeTransaction {
                profileTable.insert {
                    it[profileTable.id] = id
                    it[profileTable.name] = name
                    it[profileTable.uuid] = uuid
                }
            }
            cacheProfile(Profile(id = id, name = name, uuid = uuid))
            true
        } catch (e: Exception) {
            warn { "创建档案失败: ${e.message}" }
            false
        }
    }
    
    /**
     * 更新档案名称
     * 
     * @param profileId 档案ID
     * @param newName 新名称
     * @return 是否更新成功
     */
    fun updateProfileName(profileId: UUID, newName: String): Boolean {
        return try {
            val updated = manager.executeTransaction {
                profileTable.update({ profileTable.id eq profileId }) {
                    it[name] = newName
                }
            } > 0

            if (!updated) {
                return false
            }

            val oldCached = profileCacheById[profileId]
            if (oldCached != null) {
                removeProfileCache(oldCached)
            }

            loadProfileById(profileId)?.let { cacheProfile(it) }

            true
        } catch (e: Exception) {
            warn { "更新档案名称失败: ${e.message}" }
            false
        }
    }

    /**
     * 获取档案信息
     * 
     * @param profileId 档案ID
     * @return 档案信息，如果不存在返回 null
     */
    fun updateProfileUuid(profileId: UUID, newUuid: UUID): Boolean {
        return try {
            val updated = manager.executeTransaction {
                profileTable.update({ profileTable.id eq profileId }) {
                    it[uuid] = newUuid
                }
            } > 0

            if (!updated) {
                return false
            }

            val oldCached = profileCacheById[profileId]
            if (oldCached != null) {
                removeProfileCache(oldCached)
            }

            loadProfileById(profileId)?.let { cacheProfile(it) }

            true
        } catch (e: Exception) {
            warn { "????UUID??: ${e.message}" }
            false
        }
    }

    fun getProfile(profileId: UUID): Profile? {
        profileCacheById[profileId]?.let { return it }

        val loaded = loadProfileById(profileId) ?: return null
        cacheProfile(loaded)
        return loaded
    }
}
