package org.waste.of.time.storage

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.path.PathUtil
import net.minecraft.util.ThrowableDeliverer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.storage.RegionFile
import net.minecraft.world.storage.StorageKey
import org.waste.of.time.WorldTools.mc
import net.minecraft.storage.NbtReadView
import org.apache.logging.log4j.LogManager
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Path


open class CustomRegionBasedStorage internal constructor(
    private val directory: Path,
    private val dsync: Boolean
) : AutoCloseable {
    private val cachedRegionFiles: Long2ObjectLinkedOpenHashMap<RegionFile?> = Long2ObjectLinkedOpenHashMap()

    companion object {
        private const val MCA_EXTENSION = ".mca"
        private const val MOD_NAME = "WorldTools"
        private val LOG = LogManager.getLogger()

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

    fun getBlockEntityTags(chunkPos: ChunkPos): List<NbtCompound> {
        val nbt = getNbtAt(chunkPos)
        if (nbt == null) {
            LOG.info("[WT-merge]   getBlockEntityTags($chunkPos): no saved NBT found (chunk not in region file)")
            return emptyList()
        }

        return getBlockEntityTags(nbt).also { compounds ->
            LOG.info("[WT-merge]   getBlockEntityTags($chunkPos): found ${compounds.size} raw block entity entries")
        }
    }

    internal fun getBlockEntityTags(nbt: NbtCompound): List<NbtCompound> {
        val rawList = nbt.get("block_entities") as? NbtList
        if (rawList == null) {
            LOG.info("[WT-merge]   getBlockEntityTags: NBT found but no 'block_entities' key or wrong type (keys: ${nbt.keys})")
            return emptyList()
        }

        return rawList.streamCompounds().toList()
    }

    fun getBlockEntities(chunkPos: ChunkPos): List<BlockEntity> {
        val compounds = getBlockEntityTags(chunkPos)

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
