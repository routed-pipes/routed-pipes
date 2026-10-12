package logisticspipes.adapter.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PipeComputer;
import logisticspipes.application.computer.Caller;
import logisticspipes.application.computer.PipeCommands;

// a routed pipe for the computer next to it, what the methods do is in PipeCommands
public final class PipePeripheral implements IDynamicPeripheral {

    private final PipeBlockEntity pipe;
    private final PipeCommands commands;
    private final String[] names;

    private PipePeripheral(PipeBlockEntity pipe, PipeCommands commands) {
        this.pipe = pipe;
        this.commands = commands;
        this.names = commands.names(true).toArray(String[]::new);
    }

    // null for plain pipes, they are no peripherals
    public static PipePeripheral of(PipeBlockEntity pipe) {
        return PipeComputer.of(pipe).map(computer -> new PipePeripheral(pipe, computer.commands(AttachedComputers.INSTANCE)))
                .orElse(null);
    }

    @Override
    public String getType() {
        return commands.type();
    }

    @Override
    public String[] getMethodNames() {
        return names.clone();
    }

    // the commands look at the world, so they run with the server and the script waits for the answer
    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments arguments) throws LuaException {
        Object[] given = arguments.getAll();
        int id = computer.getID();
        return context.executeMainThreadTask(() -> call(names[method], id, given));
    }

    // server thread only
    public Object[] call(String name, int computer, Object... arguments) throws LuaException {
        return RemotePipe.run(commands, name, Caller.attached(computer), arguments);
    }

    @Override
    public void attach(IComputerAccess computer) {
        AttachedComputers.INSTANCE.attach(pipe.routerId(), computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        AttachedComputers.INSTANCE.detach(pipe.routerId(), computer);
    }

    @Override
    public Object getTarget() {
        return pipe;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PipePeripheral peripheral && peripheral.pipe == pipe;
    }
}
