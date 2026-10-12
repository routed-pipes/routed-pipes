package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.MarkerMenu;

// a screen with marker slots, tells others where it sits on the display
public interface MarkerScreen {

    int left();

    int top();

    MarkerMenu markers();

    int containerId();
}
