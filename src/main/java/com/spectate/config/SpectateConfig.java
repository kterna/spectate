package com.spectate.config;

import java.util.HashMap;
import java.util.Map;

/**
 * SpectateConfig 是一个数据类，用于映射 config.json 的结构。
 * 使用 Gson 进行序列化和反序列化。
 */
public class SpectateConfig {

    public Defaults defaults = new Defaults();
    public Messages messages = new Messages();

    public static class Defaults {
        public int cycle_interval_seconds = 60;
        public double spectate_distance = 20.0;
        public double spectate_height_offset = 5.0;
        public double spectate_rotation_speed = 1.0;
        public double floating_strength = 0.5;
        public double floating_speed = 0.3;
        public double floating_orbit_radius = 8.0;
        public double floating_height_variation = 0.8;
        public double floating_breathing_frequency = 0.5;
        public double floating_damping_factor = 0.95;
        public double floating_attraction_factor = 0.3;
        public double floating_prediction_factor = 2.0;
    }

    public static class Messages {
        public String point_added = "已添加观察点: {name}";
        public String point_removed = "已移除观察点: {name}";
        public String point_not_found = "未找到观察点: {name}";
        public String point_list_header = "观察点列表:";
        public String point_list_item = " - {name}";
        public String point_list_empty = "没有已保存的观察点。";

        public String cycle_point_added = "已添加至循环: {name}";
        public String cycle_point_removed = "已从循环中移除: {name}";
        public String cycle_list_header = "你的循环列表:";
        public String cycle_list_item = "{index}. {name}";
        public String cycle_list_empty = "你的循环列表是空的。";
        public String cycle_cleared = "已清空循环列表。";
        public String cycle_interval_set = "循环间隔已设为 {seconds} 秒。";
        public String cycle_started = "已开始观察循环。";
        public String cycle_stopped = "已停止观察循环。";
        public String cycle_next_point = "已切换至下个观察点: {index}/{total}";
        public String cycle_not_running = "您不在循环模式中，或您的循环列表为空。";

        public String player_not_found = "未找到玩家: {name}";
        public String spectate_start_player = "你现在正在观察玩家: {name}";
        public String spectate_start_player_with_mode = "你现在正在观察玩家: {name} ({mode})";
        public String spectate_start_point = "你现在正在观察点: {name}";
        public String spectate_start_point_with_mode = "你现在正在观察点: {name} ({mode})";
        public String spectate_start_coords = "你现在正在观察坐标: {coords}";
        public String spectate_stop = "你已停止观察。";
        public String spectate_already_running = "已在观察中。请先使用 /cspectate stop 停止。";
        public String must_be_player = "该命令只能由玩家执行。";
    }
}