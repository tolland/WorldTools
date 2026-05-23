package org.waste.of.time.storage

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.path.PathUtil
import net.minecraft.util.ThrowableDeliverer
import org.waste.of.time.WorldTools.LOG
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.storage.RegionFile
import net.minecraft.world.storage.StorageKey
import org.waste.of.time.WorldTools.MCA_EXTENSION
import org.waste.of.time.WorldTools.MOD_NAME
import org.waste.of.time.WorldTools.mc
import net.minecraft.storage.NbtReadView
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Path


open class CustomRegionBasedStorage internal constructor(
    private val directory: Path,
    private val dsync: Boolean
) : AutoCloseable {
    private val cachedRegionFiles: Long2ObjectLinkedOpenHashMap<RegionFile?> = Long2ObjectLinkedOpenHashMap()

    companion object {
        // Seems to only be used for MC's profiler
        // simpler to just use a default key instead of wiring this all in here
        val defaultStorageKey: StorageKey = StorageKey(MOD_NAME, World.OVERWORLD, "chunk")
    }

    @Throws(IOException::class)
    fun getRegionFile(pos: ChunkPos): RegionFile {
        val longPos = ChunkPos.toLong(pos.regionX, pos.regionZ)
        cachedRegionFiles.getAndMoveToFirst(longPos)?.let { return it }

        if (cachedRegionFiles.size >= 256) {
            cachedRegionFiles.removeLast()?.close()
        }

        PathUtil.createDirectories(directory)
        val path = directory.resolve("r." + pos.regionX + "." + pos.regionZ + MCA_EXTENSION)
        val regionFile = RegionFile(defaultStorageKey, path, directory, dsync)
        cachedRegionFiles.putAndMoveToFirst(longPos, regionFile)
        return regionFile
    }

    @Throws(IOException::class)
    fun write(pos: ChunkPos, nbt: NbtCompound?) {
        val regionFile = getRegionFile(pos)
        if (nbt == null) {
            regionFile.delete(pos)
        } else {
            regionFile.getChunkOutputStream(pos).use { dataOutputStream ->
                NbtIo.write(nbt, dataOutputStream as DataOutput)
            }
        }
    }

    private fun getNbtAt(chunkPos: ChunkPos) =
        getRegionFile(chunkPos).getChunkInputStream(chunkPos)?.use { dataInputStream ->
            NbtIo.readCompound(dataInputStream)
        }

    fun getBlockEntities(chunkPos: ChunkPos): List<BlockEntity> {
        val nbt = getNbtAt(chunkPos)
        if (nbt == null) {
            LOG.info("[WT-merge]   getBlockEntities($chunkPos): no saved NBT found (chunk not in region file)")
            return emptyList()
        }

        val rawList = nbt.getList("block_entities").orElse(null)
        if (rawList == null) {
            LOG.info("[WT-merge]   getBlockEntities($chunkPos): NBT found but no 'block_entities' key (keys: ${nbt.keys})")
            return emptyList()
        }

        val compounds = rawList.filterIsInstance<NbtCompound>()
        LOG.info("[WT-merge]   getBlockEntities($chunkPos): found ${compounds.size} raw block entity entries")

        val registryManager = mc.world?.registryManager ?: run {
            LOG.warn("[WT-merge]   getBlockEntities($chunkPos): mc.world is null, cannot deserialize block entities")
            return emptyList()
        }

        return compounds.mapNotNull { compoundTag ->
            val blockPos = BlockPos(compoundTag.getInt("x", 0), compoundTag.getInt("y", 0), compoundTag.getInt("z", 0))
            val id = compoundTag.getString("id", "")
            val blockStateIdentifier = Identifier.of(id)

            runCatching {
                val block = Registries.BLOCK.get(blockStateIdentifier)
                Registries.BLOCK_ENTITY_TYPE
                    .getOptionalValue(blockStateIdentifier)
                    .orElse(null)
                    ?.instantiate(blockPos, block.defaultState)?.apply {
                        read(NbtReadView.create(null, registryManager, compoundTag))
                    }
            }.onFailure { e ->
                LOG.warn("[WT-merge]   getBlockEntities($chunkPos): failed to instantiate '$id' at $blockPos: ${e.message}")
            }.getOrNull()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        val throwableDeliverer = ThrowableDeliverer<IOException>()

        cachedRegionFiles.values.filterNotNull().forEach { regionFile ->
            try {
                regionFile.close()
            } catch (iOException: IOException) {
                throwableDeliverer.add(iOException)
            }
        }

        throwableDeliverer.deliver()
    }
}
