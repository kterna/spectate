package com.spectate.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class PlayerData {
    public GameType gameMode;
    public Vec3 position;
    public float yaw;
    public float pitch;

    public PlayerData() {
        this.gameMode = GameType.SURVIVAL;
        this.position = Vec3.ZERO;
        this.yaw = 0.0f;
        this.pitch = 0.0f;
    }

    public PlayerData(GameType gameMode, Vec3 position, float yaw, float pitch) {
        this.gameMode = gameMode;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("gameMode", gameMode.getName());
        nbt.putDouble("posX", position.x);
        nbt.putDouble("posY", position.y);
        nbt.putDouble("posZ", position.z);
        nbt.putFloat("yaw", yaw);
        nbt.putFloat("pitch", pitch);
        return nbt;
    }

    public void readFromNbt(CompoundTag nbt) {
        String gameModeName = nbt.contains("gameMode") ? nbt.getString("gameMode") : "survival";
        this.gameMode = GameType.byName(gameModeName, GameType.SURVIVAL);
        
        double x = nbt.contains("posX") ? nbt.getDouble("posX") : 0.0;
        double y = nbt.contains("posY") ? nbt.getDouble("posY") : 0.0;
        double z = nbt.contains("posZ") ? nbt.getDouble("posZ") : 0.0;
        this.position = new Vec3(x, y, z);
        
        this.yaw = nbt.contains("yaw") ? nbt.getFloat("yaw") : 0.0f;
        this.pitch = nbt.contains("pitch") ? nbt.getFloat("pitch") : 0.0f;
    }
} 