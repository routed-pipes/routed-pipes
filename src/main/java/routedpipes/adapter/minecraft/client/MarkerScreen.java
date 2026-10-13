package routedpipes.adapter.minecraft.client;

import routedpipes.adapter.minecraft.menu.MarkerMenu;

// a screen with marker slots, tells others where it sits on the display
public interface MarkerScreen {

    int left();

    int top();

    MarkerMenu markers();

    int containerId();
}
