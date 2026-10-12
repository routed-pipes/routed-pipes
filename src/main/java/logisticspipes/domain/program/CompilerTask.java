package logisticspipes.domain.program;

import java.util.Optional;

// one job of a program compiler: a category for unlocking, a program for the other two
public record CompilerTask(Type type, ProgramCategory category, Program program) {

    // what every pipe that powers the compiler pays per tick
    public static final int ENERGY_PER_TICK = 10;

    public enum Type {
        // a category is unlocked on the disk
        UNLOCK(2000),
        // a program of an unlocked category is written on the disk
        COMPILE(400),
        // a program on the disk is put on the programmer
        FLASH(100);

        private final int work;

        Type(int work) {
            this.work = work;
        }

        // ticks it takes with one pipe powering the compiler
        public int work() {
            return work;
        }
    }

    public static CompilerTask unlock(ProgramCategory category) {
        return new CompilerTask(Type.UNLOCK, category, null);
    }

    public static CompilerTask compile(Program program) {
        return new CompilerTask(Type.COMPILE, ProgramCatalog.categoryOf(program), program);
    }

    public static CompilerTask flash(Program program) {
        return new CompilerTask(Type.FLASH, ProgramCatalog.categoryOf(program), program);
    }

    // whether the compiler can work on this with the disk in it, nothing is done twice
    public boolean possible(Optional<CompilerKnowledge> disk, boolean programmer) {
        if (disk.isEmpty()) {
            return false;
        }
        return switch (type) {
            case UNLOCK -> !disk.get().unlocked(category);
            case COMPILE -> disk.get().unlocked(category) && !disk.get().compiled(program);
            case FLASH -> disk.get().compiled(program) && programmer;
        };
    }

    // what the disk knows once this is done
    public CompilerKnowledge writtenTo(CompilerKnowledge disk) {
        return switch (type) {
            case UNLOCK -> disk.with(category);
            case COMPILE -> disk.with(program);
            case FLASH -> disk;
        };
    }
}
