package org.waste.of.time.storage

import net.minecraft.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.util.math.ChunkPos
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path

class CustomRegionBasedStorageTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraftRegistries() {
            SharedConstants.createGameVersion()
            Bootstrap.initialize()
        }
    }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads minecraft 1_21_11 top-level block entity list from region storage`() {
        val chunkPos = ChunkPos(3, -1)
        val chunkNbt = chunkNbt(chunkPos) {
            add(chestTag(52, -60, -2))
            add(lecternTag(57, 64, -2))
        }

        CustomRegionBasedStorage(tempDir, dsync = false).use { storage ->
            storage.write(chunkPos, chunkNbt)

            val blockEntities = storage.getBlockEntityTags(chunkPos)

            assertEquals(2, blockEntities.size)
            assertEquals("minecraft:chest", blockEntities[0].getString("id", ""))
            assertEquals(52, blockEntities[0].getInt("x", 0))
            assertEquals(-60, blockEntities[0].getInt("y", 0))
            assertEquals(-2, blockEntities[0].getInt("z", 0))
            assertEquals("minecraft:lectern", blockEntities[1].getString("id", ""))
        }
    }

    @Test
    fun `preserves component based item stacks in container block entity tags`() {
        val tags = CustomRegionBasedStorage(tempDir, dsync = false).getBlockEntityTags(
            chunkNbt(ChunkPos(0, 0)) {
                add(chestTag(1, 64, 2))
            }
        )

        val chest = tags.single()
        val items = chest.getList("Items").orElseThrow().streamCompounds().toList()
        val firstStack = items.single()
        val components = firstStack.getCompound("components").orElseThrow()

        assertEquals("minecraft:diamond", firstStack.getString("id", ""))
        assertEquals(3, firstStack.getInt("count", 0))
        assertEquals(0, firstStack.getByte("Slot", (-1).toByte()))
        assertTrue("components" in firstStack.keys)
        assertEquals("{\"text\":\"Downloaded\"}", components.getString("minecraft:custom_name", ""))
    }

    @Test
    fun `preserves loot table only containers for later merge handling`() {
        val tags = CustomRegionBasedStorage(tempDir, dsync = false).getBlockEntityTags(
            chunkNbt(ChunkPos(-8, -10)) {
                add(lootTableChestTag(-128, -33, -145))
            }
        )

        val chest = tags.single()

        assertEquals("minecraft:chest", chest.getString("id", ""))
        assertEquals("minecraft:chests/simple_dungeon", chest.getString("LootTable", ""))
        assertEquals(-2649340854660047352L, chest.getLong("LootTableSeed", 0L))
        assertTrue("Items" !in chest.keys)
    }

    @Test
    fun `returns an empty list when a chunk has no block entity list`() {
        val tags = CustomRegionBasedStorage(tempDir, dsync = false).getBlockEntityTags(
            NbtCompound().apply {
                putInt("DataVersion", 4440)
                putInt("xPos", 0)
                putInt("zPos", 0)
            }
        )

        assertEquals(0, tags.size)
    }

    private fun chunkNbt(chunkPos: ChunkPos, blockEntities: NbtList.() -> Unit): NbtCompound =
        NbtCompound().apply {
            putInt("DataVersion", 4440)
            putInt("xPos", chunkPos.x)
            putInt("zPos", chunkPos.z)
            put("block_entities", NbtList().apply(blockEntities))
        }

    private fun chestTag(x: Int, y: Int, z: Int): NbtCompound =
        blockEntityTag("minecraft:chest", x, y, z).apply {
            put("Items", NbtList().apply {
                add(NbtCompound().apply {
                    putByte("Slot", 0)
                    putString("id", "minecraft:diamond")
                    putInt("count", 3)
                    put("components", NbtCompound().apply {
                        putString("minecraft:custom_name", "{\"text\":\"Downloaded\"}")
                    })
                })
            })
        }

    private fun lecternTag(x: Int, y: Int, z: Int): NbtCompound =
        blockEntityTag("minecraft:lectern", x, y, z)

    private fun lootTableChestTag(x: Int, y: Int, z: Int): NbtCompound =
        blockEntityTag("minecraft:chest", x, y, z).apply {
            putString("LootTable", "minecraft:chests/simple_dungeon")
            putLong("LootTableSeed", -2649340854660047352L)
        }

    private fun blockEntityTag(id: String, x: Int, y: Int, z: Int): NbtCompound =
        NbtCompound().apply {
            put("components", NbtCompound())
            putBoolean("keepPacked", false)
            putInt("x", x)
            putInt("y", y)
            putInt("z", z)
            putString("id", id)
        }
}
