package routedpipes.adapter.minecraft.gametest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class GameTestsTest {

    // a class left off the list is never run, and nothing would say so
    @Test
    void everyClassWithGameTestsIsListed() {
        Set<String> withTests = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("routedpipes.adapter.minecraft.gametest").stream()
                .filter(type -> type.getMethods().stream()
                        .anyMatch(method -> method.isAnnotatedWith("net.minecraft.gametest.framework.GameTest")))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        assertThat(withTests).isNotEmpty();
        assertThat(GameTests.CLASSES.stream().map(Class::getName).collect(Collectors.toSet())).isEqualTo(withTests);
    }
}
