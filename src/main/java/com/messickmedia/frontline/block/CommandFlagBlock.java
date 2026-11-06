package com.messickmedia.frontline.block;

import com.messickmedia.frontline.Config;
import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import com.messickmedia.frontline.nation.Nations;
import com.messickmedia.frontline.net.ModNetwork;
import com.messickmedia.frontline.registry.ModRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * The Command Flag. Placing your first one founds a nation (a naming screen opens); every later flag is another base
 * that claims land around it. Soldiers on patrol guard the nearest flag.
 */
public class CommandFlagBlock extends BaseEntityBlock {
    public static final MapCodec<CommandFlagBlock> CODEC = simpleCodec(CommandFlagBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public CommandFlagBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CommandFlagBlockEntity(pos, state); }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistries.FLAG_BE.get(), (l, p, s, be) -> be.serverTick());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel sl) || !(placer instanceof ServerPlayer player)) return;
        NationData data = NationData.get(sl.getServer());
        Nation mine = data.ofPlayer(player.getUUID());
        Nation there = Nations.claimAt(sl, pos);
        if (there != null && (mine == null || !there.id.equals(mine.id))) {
            sl.destroyBlock(pos, false);
            if (!player.getAbilities().instabuild) player.getInventory().placeItemBackInInventory(new ItemStack(this));
            player.displayClientMessage(Component.translatable("msg.frontline.claimed_land", there.name).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof CommandFlagBlockEntity be)) return;
        if (mine == null) {
            // first flag: ask the player to name the nation; the flag is bound when the answer arrives
            PacketDistributor.sendToPlayer(player, new ModNetwork.OpenNationScreen(pos));
        } else {
            mine.bases.add(new Nation.Base(sl.dimension().location().toString(), pos.immutable()));
            data.setDirty();
            be.bind(mine);
            sl.playSound(null, pos, SoundEvents.RAID_HORN.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
            player.displayClientMessage(Component.translatable("msg.frontline.outpost", mine.name, Config.CLAIM_RADIUS.get()), false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof CommandFlagBlockEntity be && be.nationId() != null) {
            NationData data = NationData.get(sl.getServer());
            Nation n = data.byId(be.nationId());
            if (n != null) {
                n.bases.removeIf(b -> b.pos().equals(pos));
                data.setDirty();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl) || !(level.getBlockEntity(pos) instanceof CommandFlagBlockEntity be)) return InteractionResult.PASS;
        NationData data = NationData.get(sl.getServer());
        Nation n = data.byId(be.nationId());
        if (n == null) {
            Nation mine = data.ofPlayer(player.getUUID());
            if (mine == null && player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new ModNetwork.OpenNationScreen(pos));
            else if (mine != null) { mine.bases.add(new Nation.Base(sl.dimension().location().toString(), pos.immutable())); data.setDirty(); be.bind(mine); }
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && n.isLeader(player.getUUID()) && player instanceof ServerPlayer sp) {
            com.messickmedia.frontline.GameEvents.openPainter(sp, n);
            return InteractionResult.CONSUME;
        }
        int soldiers = Nations.soldierCount(sl, n.id);
        player.displayClientMessage(Component.translatable("msg.frontline.flag_info", Component.literal(n.name).withColor(n.textColor()), n.army, soldiers, n.bases.size(), n.members.size(), n.kills), false);
        return InteractionResult.CONSUME;
    }
}
