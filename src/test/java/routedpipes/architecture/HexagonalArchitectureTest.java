package routedpipes.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "routedpipes", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainOnlyUsesJavaAndItself = classes()
            .that().resideInAPackage("routedpipes.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("routedpipes.domain..", "java..");

    @ArchTest
    static final ArchRule applicationOnlyUsesDomainAndItself = classes()
            .that().resideInAPackage("routedpipes.application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("routedpipes.application..", "routedpipes.domain..", "java..");

    @ArchTest
    static final ArchRule minecraftStaysInAdapters = noClasses()
            .that().resideOutsideOfPackage("routedpipes.adapter..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("net.minecraft..", "net.neoforged..", "com.mojang..");

    // keeps the loader swappable, shared game code goes in adapter.minecraft
    @ArchTest
    static final ArchRule loaderStaysInItsOwnAdapter = noClasses()
            .that().resideOutsideOfPackage("routedpipes.adapter.neoforge..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("net.neoforged..", "routedpipes.adapter.neoforge..");

    // dedicated servers do not have these classes
    @ArchTest
    static final ArchRule clientCodeStaysInClientClasses = noClasses()
            .that().resideOutsideOfPackages("routedpipes.adapter.minecraft.client..",
                    "routedpipes.adapter.neoforge.visualtest..", "routedpipes.adapter.jei..",
                    "routedpipes.adapter.jade..")
            // the client entry class and the classes nested in it
            .and().haveNameNotMatching(".*\\.LogisticsPipesClient(\\$.*)?")
            .should().dependOnClassesThat()
            .resideInAnyPackage("net.minecraft.client..", "net.neoforged.neoforge.client..", "com.mojang.blaze3d..",
                    "routedpipes.adapter.minecraft.client..", "routedpipes.adapter.neoforge.visualtest..");

    // recipe viewers are optional, nothing else may need their classes
    @ArchTest
    static final ArchRule recipeViewerStaysInItsOwnAdapter = noClasses()
            .that().resideOutsideOfPackage("routedpipes.adapter.jei..")
            .should().dependOnClassesThat().resideInAnyPackage("mezz.jei..", "routedpipes.adapter.jei..");

    @ArchTest
    static final ArchRule blockInfoOverlayStaysInItsOwnAdapter = noClasses()
            .that().resideOutsideOfPackage("routedpipes.adapter.jade..")
            .should().dependOnClassesThat().resideInAnyPackage("snownee.jade..", "routedpipes.adapter.jade..");

    // computers are optional too, the peripherals are registered by one class and tried out by another
    @ArchTest
    static final ArchRule computersStayInTheirOwnAdapter = noClasses()
            .that().resideOutsideOfPackage("routedpipes.adapter.computercraft..")
            .and().haveNameNotMatching(".*\\.ComputerCraft(Support|Checks)(\\$.*)?")
            .should().dependOnClassesThat()
            .resideInAnyPackage("dan200.computercraft..", "routedpipes.adapter.computercraft..");

    @ArchTest
    static final ArchRule everythingLivesInALayer = classes()
            .should().resideInAnyPackage(
                    "routedpipes.domain..", "routedpipes.application..", "routedpipes.adapter..");
}
