package com.spectate;

import com.spectate.data.SpectatePointData;
import com.spectate.data.SpectateStateSaver;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpectatePointManager {
    private static SpectatePointManager instance;

    public static class SpectatePoint {
        public final Vec3 position;
        public final float distance;
        public final float heightOffset;
        public final String description;

        public SpectatePoint(Vec3 position, float distance, float heightOffset, String description) {
            this.position = position;
            this.distance = distance;
            this.heightOffset = heightOffset;
            this.description = description;
        }

        public SpectatePoint(Vec3 position, float distance, String description) {
            this(position, distance, 0.0f, description);
        }

        public SpectatePoint(Vec3 position, String description) {
            this(position, 50.0f, 0.0f, description);
        }

        @Override
        public String toString() {
            return String.format("%s [%.1f, %.1f, %.1f] 距离:%.1f 高度:%.1f", 
                description, position.x, position.y, position.z, distance, heightOffset);
        }
    }

    private SpectatePointManager() {
        // 初始化不再需要文件加载
    }

    public static SpectatePointManager getInstance() {
        if (instance == null) {
            instance = new SpectatePointManager();
        }
        return instance;
    }

    public boolean addPoint(String name, Vec3 position, float distance, float heightOffset, String description) {
        if (name == null || name.trim().isEmpty()) {
            System.err.println("坐标点名称不能为空");
            return false;
        }

        if (position == null) {
            System.err.println("坐标位置不能为null");
            return false;
        }

        if (distance <= 0) {
            System.err.println("观察距离必须大于0");
            return false;
        }

        name = name.trim().toLowerCase();
        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        SpectatePointData pointData = new SpectatePointData(position, distance, heightOffset, description);
        
        return stateSaver.addSpectatePoint(name, pointData);
    }

    public boolean addPoint(String name, Vec3 position, String description) {
        return addPoint(name, position, 50.0f, 0.0f, description);
    }

    public boolean addPoint(String name, Vec3 position) {
        return addPoint(name, position, 50.0f, 0.0f, name);
    }

    public boolean removePoint(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        return stateSaver.removeSpectatePoint(name);
    }

    public SpectatePoint getPoint(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        
        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        SpectatePointData pointData = stateSaver.getSpectatePoint(name);
        
        if (pointData == null) {
            return null;
        }
        
        return new SpectatePoint(pointData.position, pointData.distance, pointData.heightOffset, pointData.description);
    }

    public boolean hasPoint(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        return stateSaver.hasSpectatePoint(name);
    }

    public Set<String> getPointNames() {
        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        return stateSaver.getAllSpectatePoints().keySet();
    }

    public Map<String, SpectatePoint> getAllPoints() {
        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        Map<String, SpectatePointData> pointsData = stateSaver.getAllSpectatePoints();
        Map<String, SpectatePoint> points = new HashMap<>();
        
        for (Map.Entry<String, SpectatePointData> entry : pointsData.entrySet()) {
            SpectatePointData data = entry.getValue();
            points.put(entry.getKey(), new SpectatePoint(data.position, data.distance, data.heightOffset, data.description));
        }
        
        return points;
    }

    public int getPointCount() {
        SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();
        return stateSaver.getAllSpectatePoints().size();
    }

    public boolean updatePoint(String name, Vec3 position, float distance, float heightOffset, String description) {
        if (!hasPoint(name)) {
            return false;
        }

        return addPoint(name, position, distance, heightOffset, description);
    }


} 