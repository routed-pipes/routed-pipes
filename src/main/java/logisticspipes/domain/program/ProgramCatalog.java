package logisticspipes.domain.program;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// every program there is, pipes first, then modules, then upgrades
public final class ProgramCatalog {

    private static final List<Program> ALL = Stream.of(ProgramCategory.values())
            .flatMap(category -> category.programs().stream())
            .sorted(Comparator.comparing(Program::kind).thenComparing(Program::id)).toList();

    private ProgramCatalog() {
    }

    // the place of a program in this list is its number wherever one is needed
    public static List<Program> all() {
        return ALL;
    }

    public static Optional<Program> byId(String id) {
        return ALL.stream().filter(program -> program.id().equals(id)).findFirst();
    }

    public static ProgramCategory categoryOf(Program program) {
        return Stream.of(ProgramCategory.values()).filter(category -> category.programs().contains(program)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no such program: " + program.id()));
    }
}
