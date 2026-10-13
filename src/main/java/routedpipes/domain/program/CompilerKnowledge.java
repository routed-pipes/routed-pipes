package routedpipes.domain.program;

import java.util.LinkedHashSet;
import java.util.Set;

// what a disk has learned in a program compiler
public record CompilerKnowledge(Set<ProgramCategory> categories, Set<Program> programs) {

    public static final CompilerKnowledge EMPTY = new CompilerKnowledge(Set.of(), Set.of());

    public CompilerKnowledge {
        categories = Set.copyOf(categories);
        programs = Set.copyOf(programs);
    }

    public boolean unlocked(ProgramCategory category) {
        return categories.contains(category);
    }

    public boolean compiled(Program program) {
        return programs.contains(program);
    }

    public CompilerKnowledge with(ProgramCategory category) {
        Set<ProgramCategory> more = new LinkedHashSet<>(categories);
        more.add(category);
        return new CompilerKnowledge(more, programs);
    }

    public CompilerKnowledge with(Program program) {
        Set<Program> more = new LinkedHashSet<>(programs);
        more.add(program);
        return new CompilerKnowledge(categories, more);
    }
}
