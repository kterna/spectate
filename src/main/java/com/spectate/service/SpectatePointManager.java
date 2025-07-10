package com.spectate.service;

import com.spectate.data.SpectatePointData;
import com.spectate.data.SpectateStateSaver;

import java.util.Collection;

/**
 * SpectatePointManager 负责管理已定义的观察点（SpectatePointData）。
 * 它处理点的增、删、查、改和持久化。
 */
public class SpectatePointManager {

    private static final SpectatePointManager INSTANCE = new SpectatePointManager();
    public static SpectatePointManager getInstance() { return INSTANCE; }

    private final SpectateStateSaver stateSaver = SpectateStateSaver.getInstance();

    private SpectatePointManager() {
        // 在未来的重构中，可以从这里触发加载
    }

    public void addPoint(String name, SpectatePointData data) {
        stateSaver.addSpectatePoint(name, data);
    }

    public SpectatePointData removePoint(String name) {
        return stateSaver.removeSpectatePoint(name);
    }

    public SpectatePointData getPoint(String name) {
        return stateSaver.getSpectatePoint(name);
    }

    public Collection<String> listPointNames() {
        return stateSaver.listPointNames();
    }
}