package com.messickmedia.frontline.block;

import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/** Knows which nation the flag belongs to and mirrors its name and colour to the client for the renderer. */
public class CommandFlagBlockEntity extends BlockEntity {
    @Nullable private UUID nationId;
    private String nationName = "";
    private String armyName = "";
    private int color = 0x8A1C1C;
    private int syncTimer;

    public CommandFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.FLAG_BE.get(), pos, state);
    }

    @Nullable public UUID nationId() { return nationId; }
    public String nationName() { return nationName; }
    public String armyName() { return armyName; }
    public int color() { return color; }
    public boolean bound() { return nationId != null; }

    public void bind(Nation n) {
        this.nationId = n.id;
        this.nationName = n.name;
        this.armyName = n.army;
        this.color = n.color;
        this.setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }

    void serverTick() {
        if (this.nationId == null || !(this.level instanceof ServerLevel sl) || ++this.syncTimer % 60 != 0) return;
        Nation n = NationData.get(sl.getServer()).byId(this.nationId);
        if (n == null) { this.nationId = null; this.nationName = ""; this.armyName = ""; this.setChanged(); sl.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3); return; }
        if (!n.name.equals(this.nationName) || n.color != this.color || !n.army.equals(this.armyName)) bind(n);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (nationId != null) tag.putUUID("Nation", nationId);
        tag.putString("Name", nationName);
        tag.putString("Army", armyName);
        tag.putInt("Color", color);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        nationId = tag.hasUUID("Nation") ? tag.getUUID("Nation") : null;
        nationName = tag.getString("Name");
        armyName = tag.getString("Army");
        color = tag.contains("Color") ? tag.getInt("Color") : 0x8A1C1C;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
