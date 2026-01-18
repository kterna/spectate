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

    /**
     * 添加一个新的观察点。
     *
     * @param name 观察点名称。
     * @param data 观察点数据。
     */
    public void addPoint(String name, SpectatePointData data) {
        stateSaver.addSpectatePoint(name, data);
    }

    /**
     * 移除一个观察点。
     *
     * @param name 要移除的观察点名称。
     * @return 被移除的观察点数据，如果不存在则返回 null。
     */
    public SpectatePointData removePoint(String name) {
        return stateSaver.removeSpectatePoint(name);
    }

    /**
     * 获取指定名称的观察点数据。
     *
     * @param name 观察点名称。
     * @return 观察点数据，如果不存在则返回 null。
     */
    public SpectatePointData getPoint(String name) {
        return stateSaver.getSpectatePoint(name);
    }

    /**
     * 列出所有观察点的名称。
     *
     * @return 观察点名称集合。
     */
    public Collection<String> listPointNames() {
        return stateSaver.listPointNames();
    }

    /**
     * 列出指定分组的所有观察点名称。
     *
     * @param group 分组名称。
     * @return 匹配的观察点名称集合。
     */
    public java.util.Collection<String> listPointNamesByGroup(String group) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String name : listPointNames()) {
            SpectatePointData point = getPoint(name);
            if (point != null && group.equals(point.getGroup())) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * 列出所有现有的分组名称。
     *
     * @return 分组名称集合。
     */
    public java.util.Collection<String> listGroups() {
        java.util.Set<String> groups = new java.util.HashSet<>();
        for (String name : listPointNames()) {
            SpectatePointData point = getPoint(name);
            if (point != null) {
                groups.add(point.getGroup());
            }
        }
        return groups;
    }
}