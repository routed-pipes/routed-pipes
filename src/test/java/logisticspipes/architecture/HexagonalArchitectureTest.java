package logisticspipes.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "logisticspipes", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainOnlyUsesJavaAndItself = classes()
            .that().resideInAPackage("logisticspipes.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("logisticspipes.domain..", "java..");

    @ArchTest
    static final ArchRule applicationOnlyUsesDomainAndItself = classes()
            .that().resideInAPackage("logisticspipes.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("logisticspipes.application..", "logisticspipes.domain..", "java..");

    @ArchTest
    static final ArchRule minecraftStaysInAdapters = noClasses()
            .that().resideOutsideOfPackage("logisticspipes.adapter..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("net.minecraft..", "net.neoforged..", "com.mojang..");

    // keeps the loader swappable, shared game code goes in adapter.minecraft
    @ArchTest
    static final ArchRule loaderStaysInItsOwnAdapter = noClasses()
            .that().resideOutsideOfPackage("logisticspipes.adapter.neoforge..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("net.neoforged..", "logisticspipes.adapter.neoforge..");

    // dedicated servers do not have these classes
    @ArchTest
    static final ArchRule clientCodeStaysInClientClasses = noClasses()
            .that().resideOutsideOfPackages("logisticspipes.adapter.minecraft.client..",
                    "logisticspipes.adapter.neoforge.visualtest..", "logisticspipes.adapter.jei..")
            .and().doNotHaveSimpleName("LogisticsPipesClient")
            .should().dependOnClassesThat()
            .resideInAnyPackage("net.minecraft.client..", "net.neoforged.neoforge.client..", "com.mojang.blaze3d..",
                    "logisticspipes.adapter.minecraft.client..", "logisticspipes.adapter.neoforge.visualtest..");

    // recipe viewers are optional, nothing else may need their classes
    @ArchTest
    static final ArchRule recipeViewerStaysInItsOwnAdapter = noClasses()
            .that().resideOutsideOfPackage("logisticspipes.adapter.jei..")
            .should().dependOnClassesThat().resideInAnyPackage("mezz.jei..", "logisticspipes.adapter.jei..");

    @ArchTest
    static final ArchRule everythingLivesInALayer = classes()
            .should().resideInAnyPackage(
                    "logisticspipes.domain..", "logisticspipes.application..", "logisticspipes.adapter..");
}
