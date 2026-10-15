package routedpipes.adapter.minecraft.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import routedpipes.adapter.minecraft.content.Content;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;

// the game tests every loader runs, a test is named after its method and its template is one of the mod's
public final class GameTests {

    public static final List<Class<?>> CLASSES = List.of(
            BulkStorageGameTest.class,
            CancelOrderGameTest.class,
            ChassisGameTest.class,
            ComputerGameTest.class,
            ConnectorGameTest.class,
            CraftingJamGameTest.class,
            CraftingTableGameTest.class,
            CraftingTableOrderGameTest.class,
            DiskGameTest.class,
            FluidCraftingGameTest.class,
            FluidGameTest.class,
            FuzzyGameTest.class,
            GuideBookGameTest.class,
            HudGameTest.class,
            ItemConservationGameTest.class,
            ItemTraceGameTest.class,
            KeptIngredientGameTest.class,
            NetworkOverviewGameTest.class,
            OpaqueUpgradeGameTest.class,
            PatternGameTest.class,
            PipeControllerGameTest.class,
            PipeDetailsGameTest.class,
            PipeLookGameTest.class,
            PipeTransportGameTest.class,
            PlacementPreviewGameTest.class,
            PowerProviderGameTest.class,
            PowerSupplierGameTest.class,
            PowerTransportGameTest.class,
            ProgramCompilerGameTest.class,
            RecipeGameTest.class,
            RemoteOrdererKeyGameTest.class,
            RequestGameTest.class,
            RequestPreviewGameTest.class,
            SatelliteGameTest.class,
            SaveLoadGameTest.class,
            SecurityGameTest.class,
            SettingsCopierGameTest.class,
            SignGameTest.class,
            SneakyGameTest.class,
            StatisticsTableGameTest.class,
            SystemEntranceGameTest.class,
            TeleportPipeGameTest.class,
            TubeGameTest.class,
            UpgradeGameTest.class,
            VoidPipeGameTest.class);

    private GameTests() {
    }

    public static List<TestFunction> functions() {
        List<TestFunction> functions = new ArrayList<>();
        for (Class<?> testClass : CLASSES) {
            Method[] methods = testClass.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method method : methods) {
                GameTest test = method.getAnnotation(GameTest.class);
                if (test != null) {
                    functions.add(function(method, test));
                }
            }
        }
        return functions;
    }

    // the same as vanilla makes of the annotation, only the names differ
    private static TestFunction function(Method method, GameTest test) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        String template = Content.MODID + ":" + (test.template().isEmpty() ? name : test.template());
        return new TestFunction(test.batch(), name, template, StructureUtils.getRotationForRotationSteps(test.rotationSteps()),
                test.timeoutTicks(), test.setupTicks(), test.required(), test.manualOnly(), test.requiredSuccesses(), test.attempts(),
                test.skyAccess(), helper -> run(method, helper));
    }

    private static void run(Method method, GameTestHelper helper) {
        try {
            Object instance = Modifier.isStatic(method.getModifiers()) ? null : method.getDeclaringClass().getConstructor().newInstance();
            method.invoke(instance, helper);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
