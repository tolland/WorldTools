package org.waste.of.time.storage.serializable

import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.LecternBlockEntity
import net.minecraft.block.entity.LockableContainerBlockEntity
import net.minecraft.world.chunk.WorldChunk
import net.minecraft.world.level.storage.LevelStorage
import org.waste.of.time.WorldTools.LOG
import org.waste.of.time.WorldTools.config
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.storage.CustomRegionBasedStorage
import org.waste.of.time.storage.cache.HotCache.isSupported
import org.waste.of.time.storage.cache.HotCache.markScanned

class BlockEntityLoadable(
    chunk: WorldChunk
) : RegionBasedChunk(chunk) {
    private var migrated = false
    override fun shouldStore() =
        config.general.reloadBlockEntities && chunk.blockEntities.isNotEmpty()

    override val verboseInfo = translateHighlight(
        "worldtools.capture.loaded.block_entities",
        chunk.pos,
        chunk.world.registryKey.value.path
    )

    override val anonymizedInfo = translateHighlight(
        "worldtools.capture.loaded.block_entities.anonymized",
        chunk.world.registryKey.value.path
    )

    fun load(
        session: LevelStorage.Session,
        cachedStorages: MutableMap<String, CustomRegionBasedStorage>
    ): Boolean {
        val storage = generateStorage(session, cachedStorages)
        val savedEntities = storage.getBlockEntities(chunkPos).filter { it.isSupported }

        LOG.info("[WT-merge] chunk $chunkPos: ${savedEntities.size} supported saved block entities, ${cachedBlockEntities.size} in memory")

        savedEntities.forEach { existing ->
            val live = cachedBlockEntities[existing.pos]
            if (live == null) {
                LOG.info("[WT-merge]   ${existing.pos}: no live block entity at this position (skipped)")
                return@forEach
            }
            when (live) {
                is LockableContainerBlockEntity -> live.migrateData(existing)
                is LecternBlockEntity -> live.migrateData(existing)
            }
        }
        return migrated
    }

    private fun LockableContainerBlockEntity.migrateData(existing: BlockEntity) {
        if (existing !is LockableContainerBlockEntity) {
            LOG.info("[WT-merge]   $pos: saved entity is not a container (${existing::class.simpleName}), skipped")
            return
        }
        if (!isEmpty) {
            LOG.info("[WT-merge]   $pos: live container already has items, skipped")
            return
        }
        if (existing.isEmpty) {
            LOG.info("[WT-merge]   $pos: saved container is empty, nothing to restore")
            return
        }
        heldStacks = existing.heldStacks
        markScanned(true)
        migrated = true
        LOG.info("[WT-merge]   $pos: restored ${heldStacks.count { !it.isEmpty }} item stacks from saved data")
    }

    private fun LecternBlockEntity.migrateData(existing: BlockEntity) {
        if (existing !is LecternBlockEntity) return
        if (!book.isEmpty) return
        book = existing.book
        markScanned(true)
        migrated = true
        LOG.info("[WT-merge]   $pos: restored lectern book from saved data")
    }
}
