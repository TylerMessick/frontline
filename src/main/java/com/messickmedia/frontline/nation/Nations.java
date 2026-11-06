package com.messickmedia.frontline.nation;

import com.messickmedia.frontline.Config;
import com.messickmedia.frontline.entity.SoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/** Who belongs to whom. All of this runs on the server. */
public final class Nations {
    private Nations() {}

    @Nullable
    public static NationData data(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? null : NationData.get(server);
    }

    /** The nation an entity fights for: a member player, one of its soldiers, or the owner of a tamed pet / projectile. */
    @Nullable
    public static UUID sideOf(@Nullable Entity e) {
        if (e == null) return null;
        if (e instanceof SoldierEntity s) return s.nationId();
        if (e instanceof Player p) { NationData d = data(p.level()); Nation n = d == null ? null : d.ofPlayer(p.getUUID()); return n == null ? null : n.id; }
        if (e instanceof Projectile pr) return sideOf(pr.getOwner());
        if (e instanceof OwnableEntity o && o.getOwner() != null) return sideOf(o.getOwner());
        return null;
    }

    public static boolean sameSide(Entity a, Entity b) {
        if (a == b) return true;
        UUID x = sideOf(a), y = sideOf(b);
        return x != null && x.equals(y);
    }

    public static boolean atWar(Level level, @Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        NationData d = data(level);
        if (d == null) return false;
        Nation na = d.byId(a), nb = d.byId(b);
        return (na != null && na.wars.contains(b)) || (nb != null && nb.wars.contains(a));
    }

    /** The nation whose Command Flag claim covers this position, if any. */
    @Nullable
    public static Nation claimAt(ServerLevel level, BlockPos pos) {
        NationData d = data(level);
        if (d == null) return null;
        String dim = level.dimension().location().toString();
        int r = Config.CLAIM_RADIUS.get();
        for (Nation n : d.all()) {
            for (Nation.Base b : n.bases) {
                if (!b.dimension().equals(dim)) continue;
                double dx = b.pos().getX() - pos.getX(), dz = b.pos().getZ() - pos.getZ();
                if (dx * dx + dz * dz <= (double) r * r) return n;
            }
        }
        return null;
    }

    @Nullable
    public static Nation.Base nearestBase(ServerLevel level, Nation n, BlockPos pos) {
        String dim = level.dimension().location().toString();
        Nation.Base best = null;
        double bd = Double.MAX_VALUE;
        for (Nation.Base b : n.bases) {
            if (!b.dimension().equals(dim)) continue;
            double d = b.pos().distSqr(pos);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    public static int soldierCount(ServerLevel level, UUID nation) {
        int n = 0;
        for (Entity e : level.getAllEntities()) if (e instanceof SoldierEntity s && nation.equals(s.nationId())) n++;
        return n;
    }
}
