package org.waste.of.time.storage.serializable

import net.minecraft.world.chunk.WorldChunk
import net.minecraft.world.level.storage.LevelStorage
import org.waste.of.time.WorldTools.config
import org.waste.of.time.manager.MessageManager.translateHighlight
import org.waste.of.time.storage.CustomRegionBasedStorage

class BlockEntityLoadable(
    chunk: WorldChunk
) : RegionBasedChunk(chunk) {
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
        // Merge is handled in RegionBasedChunk.writeToStorage() via mergeFromSavedData()
        return true
    }
}
