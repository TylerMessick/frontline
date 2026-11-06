package com.messickmedia.frontline;

import com.messickmedia.frontline.block.CommandFlagBlockEntity;
import com.messickmedia.frontline.entity.SoldierEntity;
import com.messickmedia.frontline.gun.GunItem;
import com.messickmedia.frontline.gun.GunLogic;
import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import com.messickmedia.frontline.nation.Nations;
import com.messickmedia.frontline.net.ModNetwork;
import com.messickmedia.frontline.registry.ModRegistries;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side glue: per-tick gun upkeep, nation syncing, founding nations, and the /nation command. */
public final class GameEvents {
    private GameEvents() {}

    private static final Map<UUID, String> LAST_CLAIM = new HashMap<>();
    private static final int[] COLORS = {0x8A1C1C, 0x1C4F8A, 0x2F6B2F, 0x7A5A1C, 0x4F1C7A, 0x1C7A6B, 0x3A3A3A, 0xC08A1C};
    private static final String[] COLOR_NAMES = {"red", "blue", "green", "brown", "purple", "teal", "black", "gold"};

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GunLogic.tick(p);
        if (p.tickCount % 20 == 0) syncNation(p, false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) syncNation(p, true);
    }

    /** No sword swings while holding a rifle: the trigger is the attack. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof GunItem) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity src = event.getSource().getEntity();
        if (src != null && !Config.FRIENDLY_FIRE.get() && event.getEntity() instanceof SoldierEntity && Nations.sameSide(src, event.getEntity())) event.setCanceled(true);
    }

    static void syncNation(ServerPlayer p, boolean force) {
        ServerLevel level = p.serverLevel();
        NationData data = NationData.get(level.getServer());
        Nation mine = data.ofPlayer(p.getUUID());
        Nation here = Nations.claimAt(level, p.blockPosition());
        String claim = here == null ? "" : here.name;
        String last = LAST_CLAIM.put(p.getUUID(), claim);
        boolean hostile = here != null && mine != null && Nations.atWar(level, mine.id, here.id);
        if (force || !claim.equals(last) || p.tickCount % 100 == 0) {
            int soldiers = mine == null ? 0 : Nations.soldierCount(level, mine.id);
            PacketDistributor.sendToPlayer(p, new ModNetwork.NationSync(mine == null ? "" : mine.name, mine == null ? "" : mine.army, mine == null ? 0 : mine.color,
                    soldiers, mine == null ? 0 : mine.bases.size(), claim, here == null ? 0 : here.color, hostile));
        }
    }

    public static void foundNation(ServerPlayer p, ModNetwork.FoundNation msg) {
        ServerLevel level = p.serverLevel();
        NationData data = NationData.get(level.getServer());
        if (data.ofPlayer(p.getUUID()) != null) { p.displayClientMessage(Component.translatable("msg.frontline.already_member"), false); return; }
        String name = msg.name().trim(), army = msg.army().trim();
        if (name.length() < 2 || name.length() > 24) { p.displayClientMessage(Component.translatable("msg.frontline.bad_name").withStyle(ChatFormatting.RED), false); return; }
        if (army.isEmpty()) army = name + " Army";
        if (data.byName(name) != null) { p.displayClientMessage(Component.translatable("msg.frontline.name_taken", name).withStyle(ChatFormatting.RED), false); return; }
        if (!(level.getBlockEntity(msg.pos()) instanceof CommandFlagBlockEntity be) || be.bound() || msg.pos().distSqr(p.blockPosition()) > 64 * 64) {
            p.displayClientMessage(Component.translatable("msg.frontline.no_flag").withStyle(ChatFormatting.RED), false);
            return;
        }
        Nation there = Nations.claimAt(level, msg.pos());
        if (there != null) { p.displayClientMessage(Component.translatable("msg.frontline.claimed_land", there.name).withStyle(ChatFormatting.RED), false); return; }
        Nation n = new Nation(name, army, msg.color() & 0xFFFFFF, p.getUUID(), level.getGameTime());
        n.bases.add(new Nation.Base(level.dimension().location().toString(), msg.pos().immutable()));
        data.add(n);
        be.bind(n);
        level.playSound(null, msg.pos(), SoundEvents.RAID_HORN.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("msg.frontline.founded", p.getDisplayName(),
                Component.literal(name).withColor(n.textColor()), army).withStyle(ChatFormatting.GOLD), false);
        p.displayClientMessage(Component.translatable("msg.frontline.founded_tip", Config.CLAIM_RADIUS.get()), false);
        syncNation(p, true);
    }

    // ---------------------------------------------------------------- /nation

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nation")
                .then(Commands.literal("info").executes(c -> info(c, null))
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> info(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list").executes(GameEvents::list))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(GameEvents::invite)))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.greedyString()).executes(GameEvents::join)))
                .then(Commands.literal("leave").executes(GameEvents::leave))
                .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player()).executes(GameEvents::kick)))
                .then(Commands.literal("rename").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> rename(c, false))))
                .then(Commands.literal("army").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> rename(c, true))))
                .then(Commands.literal("color").then(Commands.argument("color", StringArgumentType.word()).executes(GameEvents::color)))
                .then(Commands.literal("war").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> war(c, true))))
                .then(Commands.literal("peace").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> war(c, false))))
                .then(Commands.literal("recall").executes(GameEvents::recall))
                .then(Commands.literal("disband").executes(GameEvents::disband)));
    }

    private static final Map<UUID, UUID> INVITES = new HashMap<>();

    private static Nation mine(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = NationData.get(c.getSource().getServer()).ofPlayer(p.getUUID());
        if (n == null) c.getSource().sendFailure(Component.translatable("msg.frontline.need_nation"));
        return n;
    }

    private static int info(CommandContext<CommandSourceStack> c, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NationData data = NationData.get(c.getSource().getServer());
        Nation n = name == null ? mine(c) : data.byName(name);
        if (n == null) { if (name != null) c.getSource().sendFailure(Component.translatable("msg.frontline.unknown_nation", name)); return 0; }
        ServerLevel level = c.getSource().getLevel();
        int soldiers = Nations.soldierCount(level, n.id);
        StringBuilder wars = new StringBuilder();
        for (UUID w : n.wars) { Nation o = data.byId(w); if (o != null) { if (wars.length() > 0) wars.append(", "); wars.append(o.name); } }
        c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.flag_info", Component.literal(n.name).withColor(n.textColor()), n.army, soldiers, n.bases.size(), n.members.size(), n.kills), false);
        if (wars.length() > 0) c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.at_war", wars.toString()).withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        NationData data = NationData.get(c.getSource().getServer());
        if (data.all().isEmpty()) { c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.no_nations"), false); return 1; }
        for (Nation n : data.all()) c.getSource().sendSuccess(() -> Component.literal("• ").append(Component.literal(n.name).withColor(n.textColor())).append(" — " + n.army + " (" + n.members.size() + " members, " + n.bases.size() + " bases)"), false);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Nation n = mine(c);
        if (n == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        INVITES.put(target.getUUID(), n.id);
        target.sendSystemMessage(Component.translatable("msg.frontline.invited", c.getSource().getDisplayName(), n.name, n.name).withStyle(ChatFormatting.GOLD));
        c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.invite_sent", target.getDisplayName()), false);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        NationData data = NationData.get(c.getSource().getServer());
        if (data.ofPlayer(p.getUUID()) != null) { c.getSource().sendFailure(Component.translatable("msg.frontline.already_member")); return 0; }
        Nation n = data.byName(StringArgumentType.getString(c, "name"));
        if (n == null) { c.getSource().sendFailure(Component.translatable("msg.frontline.unknown_nation", StringArgumentType.getString(c, "name"))); return 0; }
        if (!n.id.equals(INVITES.get(p.getUUID()))) { c.getSource().sendFailure(Component.translatable("msg.frontline.not_invited", n.name)); return 0; }
        n.members.add(p.getUUID());
        data.setDirty();
        INVITES.remove(p.getUUID());
        c.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("msg.frontline.joined", p.getDisplayName(), Component.literal(n.name).withColor(n.textColor())), false);
        syncNation(p, true);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        if (n.isLeader(p.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.leader_leave")); return 0; }
        n.members.remove(p.getUUID());
        NationData.get(c.getSource().getServer()).setDirty();
        c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.left", n.name), false);
        syncNation(p, true);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        if (!n.isLeader(p.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.leader_only")); return 0; }
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        if (target == p || !n.members.remove(target.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.not_member", target.getDisplayName())); return 0; }
        NationData.get(c.getSource().getServer()).setDirty();
        target.sendSystemMessage(Component.translatable("msg.frontline.kicked", n.name).withStyle(ChatFormatting.RED));
        syncNation(target, true);
        c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.kicked_ok", target.getDisplayName()), false);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> c, boolean army) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        if (!n.isLeader(p.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.leader_only")); return 0; }
        String name = StringArgumentType.getString(c, "name").trim();
        if (name.length() < 2 || name.length() > 24) { c.getSource().sendFailure(Component.translatable("msg.frontline.bad_name")); return 0; }
        NationData data = NationData.get(c.getSource().getServer());
        if (!army && data.byName(name) != null) { c.getSource().sendFailure(Component.translatable("msg.frontline.name_taken", name)); return 0; }
        if (army) n.army = name; else n.name = name;
        data.setDirty();
        refreshSoldiers(c.getSource().getLevel(), n);
        c.getSource().sendSuccess(() -> Component.translatable(army ? "msg.frontline.army_renamed" : "msg.frontline.renamed", name), false);
        syncNation(p, true);
        return 1;
    }

    private static int color(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        if (!n.isLeader(p.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.leader_only")); return 0; }
        String s = StringArgumentType.getString(c, "color").toLowerCase();
        int color = -1;
        for (int i = 0; i < COLOR_NAMES.length; i++) if (COLOR_NAMES[i].equals(s)) color = COLORS[i];
        if (color < 0) {
            try { color = Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF; } catch (NumberFormatException e) { c.getSource().sendFailure(Component.translatable("msg.frontline.bad_color", String.join(", ", COLOR_NAMES))); return 0; }
        }
        n.color = color;
        NationData.get(c.getSource().getServer()).setDirty();
        refreshSoldiers(c.getSource().getLevel(), n);
        c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.recolored").withColor(n.textColor()), false);
        syncNation(p, true);
        return 1;
    }

    private static int war(CommandContext<CommandSourceStack> c, boolean declare) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        if (!n.isLeader(p.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.leader_only")); return 0; }
        NationData data = NationData.get(c.getSource().getServer());
        Nation other = data.byName(StringArgumentType.getString(c, "name"));
        if (other == null || other == n) { c.getSource().sendFailure(Component.translatable("msg.frontline.unknown_nation", StringArgumentType.getString(c, "name"))); return 0; }
        if (declare) n.wars.add(other.id); else { n.wars.remove(other.id); other.wars.remove(n.id); }
        data.setDirty();
        c.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable(declare ? "msg.frontline.war" : "msg.frontline.peace",
                Component.literal(n.name).withColor(n.textColor()), Component.literal(other.name).withColor(other.textColor())).withStyle(declare ? ChatFormatting.DARK_RED : ChatFormatting.GREEN), false);
        return 1;
    }

    /** Every soldier of the nation in this dimension teleports to the caller and follows. */
    private static int recall(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        int count = 0;
        for (Entity e : p.serverLevel().getAllEntities()) {
            if (e instanceof SoldierEntity s && n.id.equals(s.nationId())) {
                s.teleportTo(p.getX() + p.getRandom().nextInt(7) - 3, p.getY(), p.getZ() + p.getRandom().nextInt(7) - 3);
                s.rallyFollow(p);
                count++;
            }
        }
        int k = count;
        c.getSource().sendSuccess(() -> Component.translatable("msg.frontline.recalled", k), false);
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Nation n = mine(c);
        if (n == null) return 0;
        if (!n.isLeader(p.getUUID())) { c.getSource().sendFailure(Component.translatable("msg.frontline.leader_only")); return 0; }
        NationData data = NationData.get(c.getSource().getServer());
        for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) if (e instanceof SoldierEntity s && n.id.equals(s.nationId())) s.dismiss();
        }
        data.remove(n.id);
        c.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("msg.frontline.disbanded", n.name).withStyle(ChatFormatting.GRAY), false);
        for (ServerPlayer sp : c.getSource().getServer().getPlayerList().getPlayers()) syncNation(sp, true);
        return 1;
    }

    private static void refreshSoldiers(ServerLevel level, Nation n) {
        for (Entity e : level.getAllEntities()) if (e instanceof SoldierEntity s && n.id.equals(s.nationId())) s.refreshNationLook(n);
    }
}
