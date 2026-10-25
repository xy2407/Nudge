package com.xy2407.collision.block;

import java.lang.reflect.Method;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import com.xy2407.collision.NudgeConfig;

/**
 * Per-BlockState memoization of the NeoForge ladder hook, custom blocks always fall back
 */
public final class LadderCache {

    private static final byte ABSENT = 2;

    private static final ClassValue<Boolean> CUSTOM_LADDER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method method = type.getMethod("isLadder", BlockState.class, LevelReader.class,
                        BlockPos.class, LivingEntity.class);
                return method.getDeclaringClass() != IBlockExtension.class;
            } catch (NoSuchMethodException missing) {
                return true;
            }
        }
    };

    private static final Reference2ByteOpenHashMap<BlockState> RESULTS = new Reference2ByteOpenHashMap<>();
    private static volatile int version;

    static {
        RESULTS.defaultReturnValue(ABSENT);
    }

    private LadderCache() {
    }

    public static boolean applies(BlockState state, Level level) {
        if (!NudgeConfig.ladderCacheActive() || level.isClientSide()) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        if (NeoForgeConfig.SERVER.fullBoundingBoxLadders.get()) {
            return false;
        }
        return !CUSTOM_LADDER.get(state.getBlock().getClass());
    }

    public static Boolean cached(BlockState state) {
        byte result = RESULTS.getByte(state);
        return result == ABSENT ? null : result == 1;
    }

    public static void store(BlockState state, boolean value) {
        RESULTS.put(state, (byte) (value ? 1 : 0));
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        RESULTS.clear();
        version++;
    }
}
