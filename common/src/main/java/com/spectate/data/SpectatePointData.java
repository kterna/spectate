package com.spectate.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public class SpectatePointData {
    public Vec3 position;
    public float distance;
    public float heightOffset;
    public String description;

    public SpectatePointData() {
        this.position = Vec3.ZERO;
        this.distance = 50.0f;
        this.heightOffset = 0.0f;
        this.description = "";
    }

    public SpectatePointData(Vec3 position, float distance, float heightOffset, String description) {
        this.position = position;
        this.distance = distance;
        this.heightOffset = heightOffset;
        this.description = description;
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("posX", position.x);
        nbt.putDouble("posY", position.y);
        nbt.putDouble("posZ", position.z);
        nbt.putFloat("distance", distance);
        nbt.putFloat("heightOffset", heightOffset);
        nbt.putString("description", description);
        return nbt;
    }

    public void readFromNbt(CompoundTag nbt) {
        // 使用1.21.5版本的Optional API并提供默认值
        double x = nbt.getDouble("posX").orElse(0.0);
        double y = nbt.getDouble("posY").orElse(0.0);
        double z = nbt.getDouble("posZ").orElse(0.0);
        this.position = new Vec3(x, y, z);
        
        this.distance = nbt.getFloat("distance").orElse(50.0f);
        this.heightOffset = nbt.getFloat("heightOffset").orElse(0.0f);
        this.description = nbt.getString("description").orElse("");
    }

    @Override
    public String toString() {
        return String.format("%s [%.1f, %.1f, %.1f] 距离:%.1f 高度:%.1f", 
            description, position.x, position.y, position.z, distance, heightOffset);
    }
} 