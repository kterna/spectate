package com.spectate.service;

import com.spectate.data.SpectatePointData;
import com.spectate.data.SpectateStateSaver;

import java.util.Collection;
import java.util.Objects;

/**
 * SpectatePointManager 封装对 SpectateStateSaver 的调用，
 * 提供更纯粹的业务接口，不含任何文件 I/O 逻辑。
 */
public class SpectatePointManager {

    private static final SpectatePointManager INSTANCE = new SpectatePointManager();

    public static SpectatePointManager getInstance() {
        return INSTANCE;
    }

    private final SpectateStateSaver saver = SpectateStateSaver.getInstance();

    private SpectatePointManager() {
    }

    public void addPoint(String name, SpectatePointData data) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        saver.addSpectatePoint(name, data);
    }

    public SpectatePointData removePoint(String name) {
        return saver.removeSpectatePoint(name);
    }

    public SpectatePointData getPoint(String name) {
        return saver.getSpectatePoint(name);
    }

    public Collection<String> listPointNames() {
        return saver.listPointNames();
    }

    public boolean editPoint(String name, SpectatePointData newData) {
        if (!saver.listPointNames().contains(name)) {
            return false;
        }
        saver.addSpectatePoint(name, newData);
        return true;
    }
} 