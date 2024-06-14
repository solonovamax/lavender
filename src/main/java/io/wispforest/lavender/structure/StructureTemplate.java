package io.wispforest.lavender.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.StreamSupport;

public class StructureTemplate {

    private static final char AIR_BLOCKSTATE_KEY = '_';

    private static final char NULL_BLOCKSTATE_KEY = ' ';

    private static final char ANCHOR_BLOCKSTATE_KEY = '#';

    public final int xSize, ySize, zSize;

    public final Vec3i anchor;

    public final Identifier id;

    private final BlockStatePredicate[][][] predicates;

    private final EnumMap<BlockStatePredicate.MatchCategory, MutableInt> predicateCountByType;

    public StructureTemplate(Identifier id, BlockStatePredicate[][][] predicates, int xSize, int ySize, int zSize, @Nullable Vec3i anchor) {
        this.id = id;
        this.predicates = predicates;
        this.xSize = xSize;
        this.ySize = ySize;
        this.zSize = zSize;

        this.anchor = anchor != null
                ? anchor
                : new Vec3i(this.xSize / 2, 0, this.ySize / 2);

        this.predicateCountByType = new EnumMap<>(BlockStatePredicate.MatchCategory.class);
        for (var type : BlockStatePredicate.MatchCategory.values()) {
            this.forEachPredicate((blockPos, predicate) -> {
                if (!predicate.isOf(type)) return;
                this.predicateCountByType.computeIfAbsent(type, $ -> new MutableInt()).increment();
            });
        }
    }

    /**
     * @return How many predicates of this structure template fall
     * into the given match category
     */
    public int predicatesOfType(BlockStatePredicate.MatchCategory type) {
        return this.predicateCountByType.get(type).intValue();
    }

    /**
     * @return The anchor position of this template,
     * to be used when placing in the world
     */
    public Vec3i anchor() {
        return this.anchor;
    }

    // --- iteration ---

    public void forEachPredicate(BiConsumer<BlockPos, BlockStatePredicate> action) {
        this.forEachPredicate(action, BlockRotation.NONE);
    }

    /**
     * Execute {@code action} for every predicate in this structure template,
     * rotated on the y-axis by {@code rotation}
     */
    public void forEachPredicate(BiConsumer<BlockPos, BlockStatePredicate> action, BlockRotation rotation) {
        var mutable = new BlockPos.Mutable();

        for (int x = 0; x < this.predicates.length; x++) {
            for (int y = 0; y < this.predicates[x].length; y++) {
                for (int z = 0; z < this.predicates[x][y].length; z++) {

                    switch (rotation) {
                        case CLOCKWISE_90 -> mutable.set(this.zSize - z - 1, y, x);
                        case COUNTERCLOCKWISE_90 -> mutable.set(z, y, this.xSize - x - 1);
                        case CLOCKWISE_180 -> mutable.set(this.xSize - x - 1, y, this.zSize - z - 1);
                        default -> mutable.set(x, y, z);
                    }

                    action.accept(mutable, this.predicates[x][y][z]);
                }
            }
        }
    }

    // --- validation ---

    /**
     * Shorthand of {@link #validate(World, BlockPos, BlockRotation)} which uses
     * {@link BlockRotation#NONE}
     */
    public boolean validate(World world, BlockPos anchor) {
        return this.validate(world, anchor, BlockRotation.NONE);
    }

    /**
     * @return {@code true} if this template matches the block states present
     * in the given world at the given position
     */
    public boolean validate(World world, BlockPos anchor, BlockRotation rotation) {
        return this.countValidStates(world, anchor, rotation) == this.predicatesOfType(BlockStatePredicate.MatchCategory.NON_NULL);
    }

    /**
     * Shorthand of {@link #countValidStates(World, BlockPos, BlockRotation)} which uses
     * {@link BlockRotation#NONE}
     */
    public int countValidStates(World world, BlockPos anchor) {
        return countValidStates(world, anchor, BlockRotation.NONE, BlockStatePredicate.MatchCategory.NON_NULL);
    }

    /**
     * Shorthand of {@link #countValidStates(World, BlockPos, BlockRotation, BlockStatePredicate.MatchCategory)}
     * which uses {@link BlockStatePredicate.MatchCategory#NON_NULL}
     */
    public int countValidStates(World world, BlockPos anchor, BlockRotation rotation) {
        return countValidStates(world, anchor, rotation, BlockStatePredicate.MatchCategory.NON_NULL);
    }

    /**
     * @return The amount of predicates in this template which match the block
     * states present in the given world at the given position
     */
    public int countValidStates(World world, BlockPos anchor, BlockRotation rotation, BlockStatePredicate.MatchCategory predicateFilter) {
        var validStates = new MutableInt();
        var mutable = new BlockPos.Mutable();

        this.forEachPredicate((pos, predicate) -> {
            if (!predicate.isOf(predicateFilter)) return;

            if (predicate.matches(world.getBlockState(mutable.set(pos).move(anchor)).rotate(inverse(rotation)))) {
                validStates.increment();
            }
        }, rotation);

        return validStates.intValue();
    }

    // --- utility ---

    public BlockRenderView asBlockRenderView() {
        return new StructureTemplateRenderView(Objects.requireNonNull(MinecraftClient.getInstance().world), this);
    }

    public static BlockRotation inverse(BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> BlockRotation.NONE;
            case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> BlockRotation.CLOCKWISE_180;
        };
    }

    // --- parsing ---

    @NotNull
    public static StructureTemplate parse(Identifier resourceId, JsonObject json) {
        Vec3i anchor = null;

        var keyObject = JsonHelper.getObject(json, "keys");
        var keys = StructureTemplate.buildStructureKeysMap(keyObject);

        var layersArray = JsonHelper.getArray(json, "layers");
        int xSize = 0, ySize = layersArray.size(), zSize = 0;

        for (var element : layersArray) {
            if (!(element instanceof JsonArray layer)) {
                throw new JsonParseException("Every element in the 'layers' array must itself be an array");
            }

            if (zSize == 0) {
                zSize = layer.size();
            } else if (zSize != layer.size()) {
                throw new JsonParseException("Every layer must have the same amount of rows");
            }

            for (var rowElement : layer) {
                if (!rowElement.isJsonPrimitive()) {
                    throw new JsonParseException("Every element in a row must be a primitive");
                }
                if (xSize == 0) {
                    xSize = rowElement.getAsString().length();
                } else if (xSize != rowElement.getAsString().length()) {
                    throw new JsonParseException("Every row must have the same length");
                }
            }
        }

        var result = new BlockStatePredicate[xSize][][];
        for (int x = 0; x < xSize; x++) {
            result[x] = new BlockStatePredicate[ySize][];
            for (int y = 0; y < ySize; y++) {
                result[x][y] = new BlockStatePredicate[zSize];
            }
        }

        for (int y = 0; y < layersArray.size(); y++) {
            var layer = (JsonArray) layersArray.get(y);
            for (int z = 0; z < layer.size(); z++) {
                var row = layer.get(z).getAsString();
                for (int x = 0; x < row.length(); x++) {
                    char key = row.charAt(x);

                    BlockStatePredicate predicate;
                    if (keys.containsKey(key)) {
                        predicate = keys.get(key);

                        if (key == ANCHOR_BLOCKSTATE_KEY) {
                            if (anchor != null) {
                                throw new JsonParseException("Anchor key '#' cannot be used twice within the same structure");
                            } else {
                                anchor = new Vec3i(x, y, z);
                            }
                        }
                    } else if (key == NULL_BLOCKSTATE_KEY) {
                        predicate = BlockStatePredicate.NULL_PREDICATE;
                    } else if (key == AIR_BLOCKSTATE_KEY) {
                        predicate = BlockStatePredicate.AIR_PREDICATE;
                    } else {
                        throw new JsonParseException("Unknown key '" + key + "'");
                    }

                    result[x][y][z] = predicate;
                }
            }
        }

        return new StructureTemplate(resourceId, result, xSize, ySize, zSize, anchor);
    }

    @NotNull
    private static Char2ObjectOpenHashMap<BlockStatePredicate> buildStructureKeysMap(@NotNull JsonObject keyObject) {
        var keys = new Char2ObjectOpenHashMap<BlockStatePredicate>();
        for (var entry : keyObject.entrySet()) {
            char key;
            if (entry.getKey().length() == 1) {
                key = entry.getKey().charAt(0);
                if (key == ANCHOR_BLOCKSTATE_KEY) {
                    throw new JsonParseException("Key '#' is reserved for 'anchor' declarations. Rename the key to 'anchor' and use '#' in the structure definition.");
                } else if (key == AIR_BLOCKSTATE_KEY) {
                    throw new JsonParseException("Key '_' is a reserved key for marking a block that must be AIR.");
                } else if (key == NULL_BLOCKSTATE_KEY) {
                    throw new JsonParseException("Key ' ' is a reserved key for marking a block that can be anything.");
                }
            } else if ("anchor".equals(entry.getKey())) {
                key = ANCHOR_BLOCKSTATE_KEY;
            } else {
                throw new JsonParseException("Keys should only be a single character or should be 'anchor'.");
            }

            if (keys.containsKey(key)) {
                throw new JsonParseException("Keys can only appear once. Key '" + key + "' appears twice.");
            }


            if (entry.getValue().isJsonArray()) {
                JsonArray blockStringsArray = entry.getValue().getAsJsonArray();
                var blockStatePredicates = StreamSupport.stream(blockStringsArray.spliterator(), false)
                        .map(blockString -> StructureTemplate.parseStringToBlockStatePredicate(blockString.getAsString()))
                        .toArray(BlockStatePredicate[]::new);
                keys.put(key, new NestedBlockStatePredicate(blockStatePredicates));
            } else if (entry.getValue().isJsonPrimitive()) {
                keys.put(key, StructureTemplate.parseStringToBlockStatePredicate(entry.getValue().getAsString()));
            } else {
                throw new JsonParseException("The values for the map of key-to-blocks must either be a string or an array of strings.");
            }
        }
        return keys;
    }

    @NotNull
    private static BlockStatePredicate parseStringToBlockStatePredicate(@NotNull String blockOrTag) {
        try {
            var result = BlockArgumentParser.blockOrTag(Registries.BLOCK.getReadOnlyWrapper(), blockOrTag, false);
            return result.map(
                    blockResult -> new SingleBlockStatePredicate(blockResult.blockState(), blockResult.properties()),
                    tagResult -> new TagBlockStatePredicate((RegistryEntryList.Named<Block>) tagResult.tag(), tagResult.vagueProperties())
            );
        } catch (CommandSyntaxException e) {
            throw new JsonParseException("Failed to parse block state predicate", e);
        }
    }

    public static class NestedBlockStatePredicate implements BlockStatePredicate {
        @NotNull
        private final BlockStatePredicate[] predicates;

        @NotNull
        private final BlockState[] previewStates;

        public NestedBlockStatePredicate(@NotNull BlockStatePredicate[] predicates) {
            this.predicates = predicates;
            this.previewStates = Arrays.stream(predicates)
                    .flatMap((predicate) -> Arrays.stream(predicate.previewBlockstates()))
                    .toArray(BlockState[]::new);
        }

        @Override
        public BlockState[] previewBlockstates() {
            return this.previewStates;
        }

        @NotNull
        @Override
        public Result test(@NotNull BlockState state) {
            boolean hasBlockMatch = false;
            for (var predicate : this.predicates) {
                var result = predicate.test(state);
                if (result == Result.STATE_MATCH)
                    return Result.STATE_MATCH;
                else if (result == Result.BLOCK_MATCH)
                    hasBlockMatch = true;
            }

            return hasBlockMatch ? Result.BLOCK_MATCH : Result.NO_MATCH;
        }
    }

    public static class SingleBlockStatePredicate implements BlockStatePredicate {
        @NotNull
        private final BlockState state;

        @NotNull
        private final BlockState[] states;

        @NotNull
        private final Map<Property<?>, Comparable<?>> properties;

        public SingleBlockStatePredicate(@NotNull BlockState state, @NotNull Map<Property<?>, Comparable<?>> properties) {
            this.state = state;
            this.states = new BlockState[]{state};
            this.properties = properties;
        }

        @Override
        public BlockState[] previewBlockstates() {
            return this.states;
        }

        @NotNull
        @Override
        public Result test(@NotNull BlockState state) {
            if (state.getBlock() != this.state.getBlock()) return Result.NO_MATCH;

            for (var propAndValue : this.properties.entrySet()) {
                if (!state.get(propAndValue.getKey()).equals(propAndValue.getValue())) {
                    return Result.BLOCK_MATCH;
                }
            }

            return Result.STATE_MATCH;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class TagBlockStatePredicate implements BlockStatePredicate {
        @NotNull
        private final TagKey<Block> tag;

        @NotNull
        private final Map<String, String> vagueProperties;

        @NotNull
        private final BlockState[] previewStates;

        public TagBlockStatePredicate(@NotNull RegistryEntryList.Named<Block> tagEntries, @NotNull Map<String, String> properties) {
            this.vagueProperties = properties;
            this.tag = tagEntries.getTag();
            this.previewStates = tagEntries.stream().map(entry -> {
                var block = entry.value();
                var state = block.getDefaultState();

                for (var propAndValue : this.vagueProperties.entrySet()) {
                    Property prop = block.getStateManager().getProperty(propAndValue.getKey());
                    if (prop == null) continue;

                    Optional<Comparable> value = prop.parse(propAndValue.getValue());
                    if (value.isEmpty()) continue;

                    state = state.with(prop, value.get());
                }

                return state;
            }).toArray(BlockState[]::new);
        }

        @Override
        public BlockState[] previewBlockstates() {
            return this.previewStates;
        }

        @NotNull
        @Override
        public Result test(@NotNull BlockState state) {
            if (!state.isIn(this.tag))
                return Result.NO_MATCH;

            for (var propAndValue : this.vagueProperties.entrySet()) {
                var prop = state.getBlock().getStateManager().getProperty(propAndValue.getKey());
                if (prop == null)
                    return Result.BLOCK_MATCH;

                var expected = prop.parse(propAndValue.getValue());
                if (expected.isEmpty())
                    return Result.BLOCK_MATCH;

                if (!state.get(prop).equals(expected.get()))
                    return Result.BLOCK_MATCH;
            }

            return Result.STATE_MATCH;
        }
    }

    private record StructureTemplateRenderView(@NotNull World world, @NotNull StructureTemplate template) implements BlockRenderView {
        @Override
        public float getBrightness(Direction direction, boolean shaded) {
            return 1.0f;
        }

        @Override
        public LightingProvider getLightingProvider() {
            return this.world.getLightingProvider();
        }

        @Override
        public int getColor(BlockPos pos, ColorResolver colorResolver) {
            return colorResolver.getColor(this.world.getBiome(pos).value(), pos.getX(), pos.getZ());
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() < 0 || pos.getX() >= this.template.xSize ||
                pos.getY() < 0 || pos.getY() >= this.template.ySize ||
                pos.getZ() < 0 || pos.getZ() >= this.template.zSize)
                return Blocks.AIR.getDefaultState();
            return this.template.predicates[pos.getX()][pos.getY()][pos.getZ()].preview();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.getDefaultState();
        }

        @Override
        public int getHeight() {
            return this.world.getHeight();
        }

        @Override
        public int getBottomY() {
            return this.world.getBottomY();
        }
    }
}
