package com.spectate.data;

import com.spectate.service.CinematicMode;
import com.spectate.service.ViewMode;

public class PlayerPreference {
    public ViewMode lastSpectateViewMode = ViewMode.ORBIT;
    public CinematicMode lastSpectateCinematicMode = null;

    public ViewMode lastCycleViewMode = ViewMode.ORBIT;
    public CinematicMode lastCycleCinematicMode = null;
}
