package logisticspipes.domain.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// what a disk holds: a name of its own and macros, no two of them with the same name
public record MacroDisk(String name, List<Macro> macros) {

    public static final MacroDisk EMPTY = new MacroDisk("", List.of());

    public MacroDisk {
        name = name.strip();
        // of two macros with the same name the later one counts, what could not be saved is left out
        Map<String, Macro> byName = new LinkedHashMap<>();
        macros.stream().filter(macro -> macro.problem().isEmpty()).forEach(macro -> byName.put(macro.name(), macro));
        macros = List.copyOf(byName.values());
    }

    public MacroDisk named(String newName) {
        return new MacroDisk(newName, macros);
    }

    public Optional<Macro> macro(String macroName) {
        return macros.stream().filter(macro -> macro.name().equals(macroName.strip())).findFirst();
    }

    // a macro with the same name is overwritten
    public MacroDisk saved(Macro macro) {
        macro.problem().ifPresent(problem -> {
            throw new IllegalArgumentException("macro cannot be saved: " + problem);
        });
        List<Macro> list = new ArrayList<>(macros);
        list.add(macro);
        return new MacroDisk(name, list);
    }

    public MacroDisk without(String macroName) {
        return new MacroDisk(name, macros.stream().filter(macro -> !macro.name().equals(macroName.strip())).toList());
    }
}
