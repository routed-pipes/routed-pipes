package routedpipes.adapter.computercraft;

import java.util.Arrays;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.IDynamicLuaObject;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import routedpipes.application.computer.Caller;
import routedpipes.application.computer.ComputerException;
import routedpipes.application.computer.PipeCommands;

// another pipe of the network as a script gets it from getPipeForUUID, with the commands that work from afar
public final class RemotePipe implements IDynamicLuaObject {

    private final PipeCommands commands;
    private final String[] names;

    private RemotePipe(PipeCommands commands) {
        this.commands = commands;
        this.names = commands.names(false).toArray(String[]::new);
    }

    // runs a command for a script: errors become errors of the script, pipes in the answer become objects with methods
    static Object[] run(PipeCommands commands, String name, Caller caller, Object[] arguments) throws LuaException {
        try {
            Object[] results = commands.call(name, caller, Arrays.asList(arguments));
            for (int index = 0; index < results.length; index++) {
                if (results[index] instanceof PipeCommands other) {
                    results[index] = new RemotePipe(other);
                }
            }
            return results;
        } catch (ComputerException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @Override
    public String[] getMethodNames() {
        return names.clone();
    }

    @Override
    public MethodResult callMethod(ILuaContext context, int method, IArguments arguments) throws LuaException {
        Object[] given = arguments.getAll();
        return context.executeMainThreadTask(() -> call(names[method], given));
    }

    // server thread only
    public Object[] call(String name, Object... arguments) throws LuaException {
        return run(commands, name, Caller.REMOTE, arguments);
    }
}
