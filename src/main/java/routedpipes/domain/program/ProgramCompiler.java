package routedpipes.domain.program;

import java.util.Optional;
import java.util.function.IntSupplier;

// works on one task at a time, as fast as the pipes next to it power it
public final class ProgramCompiler {

    private CompilerTask task;
    private int work;
    private boolean powered;

    public Optional<CompilerTask> task() {
        return Optional.ofNullable(task);
    }

    public int work() {
        return work;
    }

    // from 0 to 1
    public float progress() {
        return task == null ? 0 : Math.min(1f, (float) work / task.type().work());
    }

    // false when the last tick of a task found no pipe that could pay
    public boolean powered() {
        return powered;
    }

    // there is no way to stop a task, so a new one has to wait
    public boolean start(CompilerTask newTask, Optional<CompilerKnowledge> disk, boolean programmer) {
        if (task != null || !newTask.possible(disk, programmer)) {
            return false;
        }
        task = newTask;
        work = 0;
        powered = true;
        return true;
    }

    // a task that was running when the compiler was saved
    public void restore(CompilerTask savedTask, int savedWork) {
        task = savedTask;
        work = Math.max(0, savedWork);
        powered = true;
    }

    // one tick, payingPipes takes the energy and says how many pipes paid, returns the task when it is done
    // a task is dropped when the disk or the programmer it needs is taken out
    public Optional<CompilerTask> tick(Optional<CompilerKnowledge> disk, boolean programmer, IntSupplier payingPipes) {
        if (task == null) {
            return Optional.empty();
        }
        if (!task.possible(disk, programmer)) {
            task = null;
            return Optional.empty();
        }
        int pipes = payingPipes.getAsInt();
        powered = pipes > 0;
        work += pipes;
        if (work < task.type().work()) {
            return Optional.empty();
        }
        CompilerTask done = task;
        task = null;
        return Optional.of(done);
    }
}
