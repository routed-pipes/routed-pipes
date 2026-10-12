package logisticspipes.domain.hud;

import java.util.List;
import java.util.stream.Stream;

// a line of text as the key of its translation and what goes into its gaps
public record HudText(String key, List<String> args) {

    public HudText {
        args = List.copyOf(args);
    }

    public static HudText of(String key, Object... args) {
        return new HudText(key, Stream.of(args).map(String::valueOf).toList());
    }
}
