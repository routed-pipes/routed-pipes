package routedpipes.domain.program;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ProgramCompilerTest {

    private static final Program REQUEST = ProgramCatalog.byId("request_pipe").orElseThrow();
    private static final Program CRAFTER = ProgramCatalog.byId("module_crafter").orElseThrow();
    private static final Optional<CompilerKnowledge> BLANK = Optional.of(CompilerKnowledge.EMPTY);
    private static final Optional<CompilerKnowledge> BASIC = Optional.of(CompilerKnowledge.EMPTY.with(ProgramCategory.BASIC));
    private static final Optional<CompilerKnowledge> COMPILED = Optional.of(BASIC.get().with(REQUEST));

    private int paid;

    // ticks until the task is done with that many pipes paying, counting what they pay
    private int ticksFor(CompilerTask task, Optional<CompilerKnowledge> disk, int pipes) {
        ProgramCompiler compiler = new ProgramCompiler();
        assertThat(compiler.start(task, disk, true)).isTrue();
        int ticks = 0;
        Optional<CompilerTask> done = Optional.empty();
        while (done.isEmpty()) {
            done = compiler.tick(disk, true, () -> {
                paid += pipes * CompilerTask.ENERGY_PER_TICK;
                return pipes;
            });
            ticks++;
        }
        assertThat(done).contains(task);
        assertThat(compiler.task()).isEmpty();
        return ticks;
    }

    @Test
    void unlockingTakesAHundredSecondsAndTwentyThousandEnergy() {
        assertThat(ticksFor(CompilerTask.unlock(ProgramCategory.BASIC), BLANK, 1)).isEqualTo(2000);
        assertThat(paid).isEqualTo(20_000);
    }

    @Test
    void compilingTakesTwentySecondsAndFourThousandEnergy() {
        assertThat(ticksFor(CompilerTask.compile(REQUEST), BASIC, 1)).isEqualTo(400);
        assertThat(paid).isEqualTo(4_000);
    }

    @Test
    void flashingTakesFiveSecondsAndAThousandEnergy() {
        assertThat(ticksFor(CompilerTask.flash(REQUEST), COMPILED, 1)).isEqualTo(100);
        assertThat(paid).isEqualTo(1_000);
    }

    @Test
    void morePipesAreFasterForTheSameEnergy() {
        assertThat(ticksFor(CompilerTask.unlock(ProgramCategory.BASIC), BLANK, 5)).isEqualTo(400);
        assertThat(paid).isEqualTo(20_000);
    }

    @Test
    void withoutPowerNothingMoves() {
        ProgramCompiler compiler = new ProgramCompiler();
        compiler.start(CompilerTask.flash(REQUEST), COMPILED, true);
        compiler.tick(COMPILED, true, () -> 1);
        assertThat(compiler.powered()).isTrue();

        assertThat(compiler.tick(COMPILED, true, () -> 0)).isEmpty();

        assertThat(compiler.powered()).isFalse();
        assertThat(compiler.work()).isEqualTo(1);
        assertThat(compiler.progress()).isEqualTo(0.01f);
    }

    @Test
    void aSecondTaskHasToWait() {
        ProgramCompiler compiler = new ProgramCompiler();
        assertThat(compiler.start(CompilerTask.compile(REQUEST), BASIC, false)).isTrue();

        assertThat(compiler.start(CompilerTask.unlock(ProgramCategory.FLUID), BASIC, false)).isFalse();

        assertThat(compiler.task()).contains(CompilerTask.compile(REQUEST));
    }

    @Test
    void nothingStartsWithoutADisk() {
        ProgramCompiler compiler = new ProgramCompiler();

        assertThat(compiler.start(CompilerTask.unlock(ProgramCategory.BASIC), Optional.empty(), true)).isFalse();
        assertThat(compiler.start(CompilerTask.compile(REQUEST), Optional.empty(), true)).isFalse();
        assertThat(compiler.start(CompilerTask.flash(REQUEST), Optional.empty(), true)).isFalse();
    }

    @Test
    void aProgramOfALockedCategoryIsNotCompiled() {
        assertThat(CompilerTask.compile(CRAFTER).possible(BASIC, true)).isFalse();
        assertThat(CompilerTask.compile(REQUEST).possible(BLANK, true)).isFalse();
        assertThat(CompilerTask.compile(REQUEST).possible(BASIC, true)).isTrue();
    }

    @Test
    void onlyACompiledProgramIsFlashedAndOnlyOntoAProgrammer() {
        assertThat(CompilerTask.flash(REQUEST).possible(BASIC, true)).isFalse();
        assertThat(CompilerTask.flash(REQUEST).possible(COMPILED, false)).isFalse();
        assertThat(CompilerTask.flash(REQUEST).possible(COMPILED, true)).isTrue();
    }

    @Test
    void nothingIsDoneTwice() {
        assertThat(CompilerTask.unlock(ProgramCategory.BASIC).possible(BASIC, false)).isFalse();
        assertThat(CompilerTask.compile(REQUEST).possible(COMPILED, false)).isFalse();
    }

    @Test
    void takingTheDiskOutDropsTheTask() {
        ProgramCompiler compiler = new ProgramCompiler();
        compiler.start(CompilerTask.compile(REQUEST), BASIC, false);
        compiler.tick(BASIC, false, () -> 1);

        assertThat(compiler.tick(Optional.empty(), false, () -> 1)).isEmpty();

        assertThat(compiler.task()).isEmpty();
        assertThat(compiler.progress()).isZero();
    }

    @Test
    void aDiskThatLacksTheCategoryDropsTheTaskWithoutTakingEnergy() {
        ProgramCompiler compiler = new ProgramCompiler();
        compiler.start(CompilerTask.compile(REQUEST), BASIC, false);

        compiler.tick(BLANK, false, () -> {
            paid++;
            return 1;
        });

        assertThat(compiler.task()).isEmpty();
        assertThat(paid).isZero();
    }

    @Test
    void takingTheProgrammerOutDropsFlashing() {
        ProgramCompiler compiler = new ProgramCompiler();
        compiler.start(CompilerTask.flash(REQUEST), COMPILED, true);

        compiler.tick(COMPILED, false, () -> 1);

        assertThat(compiler.task()).isEmpty();
    }

    @Test
    void aFinishedTaskIsWrittenToTheDisk() {
        assertThat(CompilerTask.unlock(ProgramCategory.BASIC).writtenTo(CompilerKnowledge.EMPTY)).isEqualTo(BASIC.get());
        assertThat(CompilerTask.compile(REQUEST).writtenTo(BASIC.get())).isEqualTo(COMPILED.get());
        assertThat(CompilerTask.flash(REQUEST).writtenTo(COMPILED.get())).isEqualTo(COMPILED.get());
    }

    @Test
    void aRestoredTaskGoesOnWhereItWas() {
        ProgramCompiler compiler = new ProgramCompiler();
        compiler.restore(CompilerTask.flash(REQUEST), 99);

        assertThat(compiler.tick(COMPILED, true, () -> 1)).contains(CompilerTask.flash(REQUEST));
    }
}
