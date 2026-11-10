package com.example.tiercity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 전선 연결점(완목, 벽 연결점): 이어진 상대 연결점 좌표를 기억한다. 전선은 이쪽 끝에서만 그린다. */
public class WireBlockEntity extends BlockEntity {
    private long[] targets = new long[0];

    public WireBlockEntity(BlockPos pos, BlockState state) {
        super(TierCityMod.WIRE_BE.get(), pos, state);
    }

    public long[] targets() { return targets; }

    public void setTargets(long[] t) {
        targets = t == null ? new long[0] : t;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLongArray("Targets", targets);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        targets = tag.getLongArray("Targets");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition);
        for (long t : targets) box = box.minmax(new AABB(BlockPos.of(t)));
        return box.inflate(3);
    }
}
