package logisticspipes.domain.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import logisticspipes.domain.hud.HudAction.Kind;
import logisticspipes.domain.hud.HudGaze.Target;
import org.junit.jupiter.api.Test;

class HudGazeTest {

    private static final Target NEXT = new Target("pipe", new HudAction(Kind.TURN_PAGE, 0, 1));
    private static final Target BACK = new Target("pipe", new HudAction(Kind.TURN_PAGE, 0, 0));

    private final HudGaze gaze = new HudGaze();

    @Test
    void aButtonIsPressedOnceAfterBeingLookedAtForMoreThan400Milliseconds() {
        assertThat(gaze.look(Optional.of(NEXT), 1000)).isEmpty();
        assertThat(gaze.focused()).contains(NEXT);
        assertThat(gaze.look(Optional.of(NEXT), 1400)).isEmpty();
        assertThat(gaze.look(Optional.of(NEXT), 1401)).contains(NEXT);
        assertThat(gaze.look(Optional.of(NEXT), 5000)).isEmpty();
    }

    @Test
    void lookingAwayAndBackStartsAgain() {
        gaze.look(Optional.of(NEXT), 0);
        gaze.look(Optional.of(NEXT), 500);

        gaze.look(Optional.empty(), 600);
        assertThat(gaze.focused()).isEmpty();
        gaze.look(Optional.of(NEXT), 700);

        assertThat(gaze.look(Optional.of(NEXT), 1000)).isEmpty();
        assertThat(gaze.look(Optional.of(NEXT), 1101)).contains(NEXT);
    }

    @Test
    void movingToAnotherButtonStartsItsOwnTime() {
        gaze.look(Optional.of(NEXT), 0);
        gaze.look(Optional.of(BACK), 300);

        assertThat(gaze.look(Optional.of(BACK), 600)).isEmpty();
        assertThat(gaze.look(Optional.of(BACK), 701)).contains(BACK);
    }

    @Test
    void theSameButtonOnAnotherPanelIsAnotherButton() {
        Target elsewhere = new Target("other pipe", NEXT.action());
        gaze.look(Optional.of(NEXT), 0);

        assertThat(gaze.look(Optional.of(elsewhere), 500)).isEmpty();
        assertThat(gaze.look(Optional.of(elsewhere), 901)).contains(elsewhere);
    }
}
