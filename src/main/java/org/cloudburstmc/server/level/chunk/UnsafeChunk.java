package org.cloudburstmc.server.level.chunk;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.cloudburstmc.api.block.BlockState;
import org.cloudburstmc.api.block.BlockStates;
import org.cloudburstmc.api.block.BlockTypes;
import org.cloudburstmc.api.blockentity.BlockEntity;
import org.cloudburstmc.api.entity.Entity;
import org.cloudburstmc.api.level.Level;
import org.cloudburstmc.api.level.chunk.Chunk;
import org.cloudburstmc.api.level.chunk.LockableChunk;
import org.cloudburstmc.api.player.Player;
import org.cloudburstmc.server.blockentity.BaseBlockEntity;
import org.cloudburstmc.server.entity.BaseEntity;
import org.cloudburstmc.server.player.CloudPlayer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static com.google.common.base.Preconditions.checkElementIndex;

public final class UnsafeChunk implements Chunk, Closeable {

    private static final AtomicIntegerFieldUpdater<UnsafeChunk> DIRTY_FIELD = AtomicIntegerFieldUpdater
            .newUpdater(UnsafeChunk.class, "dirty");
    private static final AtomicIntegerFieldUpdater<UnsafeChunk> INITIALIZED_FIELD = AtomicIntegerFieldUpdater
            .newUpdater(UnsafeChunk.class, "initialized");
    private static final AtomicIntegerFieldUpdater<UnsafeChunk> STATE_FIELD = AtomicIntegerFieldUpdater
            .newUpdater(UnsafeChunk.class, "state");
    private static final AtomicIntegerFieldUpdater<UnsafeChunk> CLOSED_FIELD = AtomicIntegerFieldUpdater
            .newUpdater(UnsafeChunk.class, "closed");
    static final AtomicIntegerFieldUpdater<UnsafeChunk> CLEAR_CACHE_FIELD = AtomicIntegerFieldUpdater
            .newUpdater(UnsafeChunk.class, "clearCache");

    private final int x;

    private final int z;

    private final Level level;

    private final CloudChunkSection[] sections;

    private final Set<CloudPlayer> players = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<BaseEntity> entities = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Short2ObjectMap<BaseBlockEntity> tiles = new Short2ObjectOpenHashMap<>();

    private final byte[] biomes;

    private final int[] heightMap;

    private volatile int dirty;

    private volatile int initialized;

    private volatile int state = STATE_NEW;

    private volatile int closed;

    private volatile int clearCache;

    public UnsafeChunk(int x, int z, Level level) {
        this.x = x;
        this.z = z;
        this.level = level;
        this.sections = new CloudChunkSection[CloudChunk.SECTION_COUNT];
        this.biomes = new byte[CloudChunk.ARRAY_SIZE];
        this.heightMap = new int[CloudChunk.ARRAY_SIZE];
    }

    UnsafeChunk(int x, int z, Level level, CloudChunkSection[] sections, byte[] biomes, int[] heightMap) {
        this.x = x;
        this.z = z;
        this.level = level;
        Preconditions.checkNotNull(sections, "sections");
        this.sections = Arrays.copyOf(sections, CloudChunk.SECTION_COUNT);
        Preconditions.checkNotNull(biomes, "biomes");
        this.biomes = Arrays.copyOf(biomes, CloudChunk.ARRAY_SIZE);
        Preconditions.checkNotNull(heightMap, "heightMap");
        this.heightMap = Arrays.copyOf(heightMap, CloudChunk.ARRAY_SIZE);
    }

    static void checkBounds(int x, int y, int z) {
        checkElementIndex(y, 256, "y coordinate");
        checkBounds(x, z);
    }

    static void checkBounds(int x, int z) {
        checkElementIndex(x, 16, "x coordinate");
        checkElementIndex(z, 16, "z coordinate");
    }

    static int get2dIndex(int x, int z) {
        return z << 4 | x;
    }

    public boolean init() {
        return INITIALIZED_FIELD.compareAndSet(this, 0, 1);
    }


    @Nonnull
    @Override
    public CloudChunkSection getOrCreateSection(int y) {
        checkElementIndex(y, sections.length, "section Y");

        CloudChunkSection section = this.sections[y];
        if (section == null) {
            section = new CloudChunkSection();
            this.sections[y] = section;
            this.setDirty();
        }
        return section;
    }

    @Nullable
    @Override
    public CloudChunkSection getSection(int y) {
        checkElementIndex(y, sections.length, "section Y");
        return this.sections[y];
    }

    @Nonnull
    @Override
    public CloudChunkSection[] getSections() {
        return this.sections;
    }


    @Nonnull
    @Override
    public BlockState getBlock(int x, int y, int z, int layer) {
        checkBounds(x, y, z);
        CloudChunkSection section = this.getSection(y >> 4);
        BlockState blockState;
        if (section == null) {
            blockState = BlockStates.AIR;
        } else {
            blockState = section.getBlock(x, y & 0xf, z, layer);
        }
        return blockState;
    }

    @Nonnull
    @Override
    public BlockState getAndSetBlock(int x, int y, int z, int layer, BlockState blockState) {
        BlockState previousBlockState = this.getBlock(x, y, z, layer);
        this.setBlock(x, y, z, layer, blockState);
        return previousBlockState;
    }

    @Override
    public void setBlock(int x, int y, int z, int layer, BlockState blockState) {
        checkBounds(x, y, z);
        CloudChunkSection section = this.getSection(y >> 4);
        if (section == null) {
            if (blockState.getType() == BlockTypes.AIR) {
                // Setting air in an empty section.
                return;
            }
            section = this.getOrCreateSection(y >> 4);
        }

        section.setBlock(x, y & 0xf, z, layer, blockState);
        this.setDirty();
    }

    @Override
    public int getBiome(int x, int z) {
        checkBounds(x, z);
        return this.biomes[get2dIndex(x, z)] & 0xFF;
    }

    @Override
    public void setBiome(int x, int z, int biome) {
        checkBounds(x, z);
        int index = get2dIndex(x, z);
        int oldBiome = this.biomes[index] & 0xf;
        if (oldBiome != biome) {
            this.biomes[index] = (byte) biome;
            this.setDirty();
        }
    }

    @Override
    public byte getSkyLight(int x, int y, int z) {
        checkBounds(x, y, z);
        CloudChunkSection section = this.getSection(y >> 4);
        return section == null ? 0 : section.getSkyLight(x, y & 0xf, z);
    }

    @Override
    public void setSkyLight(int x, int y, int z, int level) {
        checkBounds(x, y, z);
        this.getOrCreateSection(y >> 4).setSkyLight(x, y & 0xf, z, (byte) level);
        setDirty();
    }

    @Override
    public byte getBlockLight(int x, int y, int z) {
        checkBounds(x, y, z);
        CloudChunkSection section = this.getSection(y >> 4);
        return section == null ? 0 : section.getBlockLight(x, y & 0xf, z);
    }

    @Override
    public void setBlockLight(int x, int y, int z, int level) {
        checkBounds(x, y, z);
        this.getOrCreateSection(y >> 4).setBlockLight(x, y & 0xf, z, (byte) level);
        setDirty();
    }

    @Override
    public int getHighestBlock(int x, int z) {
        checkBounds(x, z);
        for (int sectionY = 15; sectionY >= 0; sectionY--) {
            CloudChunkSection section = this.sections[sectionY];
            if (section != null) {
                for (int y = 15; y >= 0; y--) {
                    if (section.getBlock(x, y, z, 0) != BlockStates.AIR) {
                        return (sectionY << 4) | y;
                    }
                }
            }
        }
        return -1;
    }

    /*@Override
    public int getHeightMap(int x, int z) {
        checkBounds(x, z);
        return this.heightMap[get2dIndex(x, z)] & 0xFF;
    }

    @Override
    public void setHeightMap(int x, int z, int value) {
        checkBounds(x, z);
        this.heightMap[get2dIndex(x, z)] = (byte) value;
        setDirty();
    }*/

    @Override
    public void addEntity(@Nonnull Entity entity) {
        Preconditions.checkNotNull(entity, "entity");
        if (entity instanceof Player) {
            this.players.add((CloudPlayer) entity);
        } else if (this.entities.add((BaseEntity) entity) && this.initialized == 1) {
            this.setDirty();
        }
    }

    @Override
    public void removeEntity(Entity entity) {
        Preconditions.checkNotNull(entity, "entity");
        if (entity instanceof Player) {
            this.players.remove(entity);
        } else if (this.entities.remove(entity) && this.initialized == 1) {
            this.setDirty();
        }
    }

    @Override
    public void addBlockEntity(BlockEntity blockEntity) {
        Preconditions.checkNotNull(blockEntity, "blockEntity");
        short hash = CloudChunk.blockKey(blockEntity.getPosition());
        if (this.tiles.put(hash, (BaseBlockEntity) blockEntity) != blockEntity && this.initialized == 1) {
            this.setDirty();
        }
    }

    @Override
    public void removeBlockEntity(BlockEntity blockEntity) {
        Preconditions.checkNotNull(blockEntity, "blockEntity");
        short hash = CloudChunk.blockKey(blockEntity.getPosition());
        if (this.tiles.remove(hash) == blockEntity && this.initialized == 1) {
            this.setDirty();
        }
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(int x, int y, int z) {
        checkBounds(x, y, z);
        return this.tiles.get(CloudChunk.blockKey(x, y, z));
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Nonnull
    @Override
    public Level getLevel() {
        return level;
    }

    @Nonnull
    @Override
    public byte[] getBiomeArray() {
        return biomes;
    }

    @Nonnull
    @Override
    public int[] getHeightMapArray() {
        return heightMap;
    }


    /**
     * Gets an immutable copy of players currently in this chunk
     *
     * @return player set
     */
    @Nonnull
    @Override
    public Set<CloudPlayer> getPlayers() {
        return players;
    }

    /**
     * Gets an immutable copy of entities currently in this chunk
     *
     * @return entity set
     */
    @Nonnull
    @Override
    public Set<BaseEntity> getEntities() {
        return this.entities;
    }

    /**
     * Gets an immutable copy of all block entities within the current chunk.
     *
     * @return block entity collection
     */
    @Nonnull
    @Override
    public Set<BaseBlockEntity> getBlockEntities() {
        return new HashSet<>(this.tiles.values());
    }

    @Override
    public int getState() {
        return this.state;
    }

    @Override
    public int setState(int nextIn) {
        return STATE_FIELD.getAndAccumulate(this, nextIn, (curr, next) -> {
            Preconditions.checkArgument(next >= 0 && next <= STATE_FINISHED, "invalid state: %s", next);
            Preconditions.checkState(curr < next, "invalid state transition: %s => %s", curr, next);
            return next;
        });
    }

    /**
     * Whether the chunk has changed since it was last loaded or saved.
     *
     * @return dirty
     */
    @Override
    public boolean isDirty() {
        return this.state >= STATE_GENERATED && this.dirty == 1;
    }

    /**
     * Sets the chunk's dirty status.
     */
    @Override
    public void setDirty(boolean dirty) {
        if (dirty) {
            CLEAR_CACHE_FIELD.set(this, 1);
        }
        DIRTY_FIELD.set(this, dirty ? 1 : 0);
    }

    @Override
    public boolean clearDirty() {
        return this.state >= STATE_GENERATED && DIRTY_FIELD.compareAndSet(this, 1, 0);
    }

    /**
     * Clear chunk to a state as if it was not generated.
     */
    @Override
    public void clear() {
        Arrays.fill(this.sections, null);
        Arrays.fill(this.biomes, (byte) 0);
        Arrays.fill(this.heightMap, (byte) 0);
        this.tiles.clear();
        this.entities.clear();
        this.state = STATE_NEW;
        this.dirty = 1;
    }

    @Override
    public void close() {
        if (CLOSED_FIELD.compareAndSet(this, 0, 1)) {
            for (Entity entity : ImmutableList.copyOf(this.entities)) {
                if (entity instanceof Player) {
                    continue;
                }
                entity.close();
            }

            ImmutableList.copyOf(this.tiles.values()).forEach(BlockEntity::close);
            clear();
        }
    }

    @Override
    public Set<CloudPlayer> getLoaders() {
        return players;
    }

    @Override
    public Set<CloudPlayer> getPlayerLoaders() {
        return players;
    }

    @Override
    public long key() {
        return CloudChunk.key(getX(), getZ());
    }

    @Override
    public LockableChunk readLockable() {
        return null;
    }

    @Override
    public LockableChunk writeLockable() {
        return null;
    }
}
