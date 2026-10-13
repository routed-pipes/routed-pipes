package routedpipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import routedpipes.domain.item.ItemType;
import routedpipes.testing.Fixtures.TestItem;
import routedpipes.testing.Fixtures.TraitItem;
import org.junit.jupiter.api.Test;

class TraitSinkTest {

    private static final ItemType INGOT = new TraitItem("ingot", "metals", Set.of("c:ingots", "c:ingots/iron"), Set.of("ingredients"), false);
    private static final ItemType PLAIN = new TestItem("stone");

    private static boolean takes(SinkRule rule, ItemType type) {
        return rule.priority(type, Map.of()).isPresent();
    }

    @Test
    void takesItemsOfAListedMod() {
        TraitSink sink = new TraitSink(TraitSink.Trait.MOD, Set.of("metals", "other"));

        assertThat(takes(sink, INGOT)).isTrue();
        assertThat(takes(sink, PLAIN)).isFalse();
    }

    @Test
    void takesItemsWithAListedTag() {
        assertThat(takes(new TraitSink(TraitSink.Trait.TAG, Set.of("c:ingots")), INGOT)).isTrue();
        assertThat(takes(new TraitSink(TraitSink.Trait.TAG, Set.of("c:gems")), INGOT)).isFalse();
    }

    @Test
    void takesItemsOfAListedCreativeTab() {
        assertThat(takes(new TraitSink(TraitSink.Trait.GROUP, Set.of("ingredients")), INGOT)).isTrue();
        assertThat(takes(new TraitSink(TraitSink.Trait.GROUP, Set.of("combat")), INGOT)).isFalse();
    }

    @Test
    void isWantedLessThanSinksThatNameTheItem() {
        assertThat(TraitSink.Trait.MOD.priority()).isGreaterThan(ItemSink.DEFAULT_ROUTE_PRIORITY);
        assertThat(TraitSink.Trait.TAG.priority()).isGreaterThan(TraitSink.Trait.MOD.priority());
        assertThat(EnchantmentSink.PRIORITY).isGreaterThan(TraitSink.Trait.TAG.priority());
        assertThat(Terminus.PRIORITY).isGreaterThan(EnchantmentSink.PRIORITY + 1);
    }

    @Test
    void hasToBeAskedAboutEveryItemOnceItListsSomething() {
        assertThat(new TraitSink(TraitSink.Trait.MOD, Set.of("metals")).takesUnlisted()).isTrue();
        assertThat(new TraitSink(TraitSink.Trait.MOD, Set.of()).takesUnlisted()).isFalse();
    }

    @Test
    void enchantmentSinkTakesAnythingEnchanted() {
        ItemType sword = new TraitItem("sword", "", Set.of(), Set.of(), true);

        assertThat(EnchantmentSink.any().priority(sword, Map.of())).hasValue(EnchantmentSink.PRIORITY);
        assertThat(takes(EnchantmentSink.any(), PLAIN)).isFalse();
    }

    @Test
    void enchantmentSinkWithAListTakesOnlyEnchantedVersionsOfThose() {
        ItemType sword = new TraitItem("sword", "", Set.of(), Set.of(), true);
        ItemType bow = new TraitItem("bow", "", Set.of(), Set.of(), true);
        ItemType dullSword = new TraitItem("sword", "", Set.of(), Set.of(), false);
        EnchantmentSink sink = EnchantmentSink.of(Set.of(new TestItem("sword")));

        assertThat(sink.priority(sword, Map.of())).hasValue(EnchantmentSink.PRIORITY + 1);
        assertThat(takes(sink, bow)).isFalse();
        assertThat(takes(sink, dullSword)).isFalse();
    }

    @Test
    void extractionListIsAboutItemsWhateverTheirData() {
        ItemType sword = new TraitItem("sword", "", Set.of(), Set.of(), true);
        Extraction only = new Extraction(ExtractionMode.EXTRACTOR, Set.of(new TestItem("sword")), false);
        Extraction allBut = new Extraction(ExtractionMode.EXTRACTOR, Set.of(new TestItem("sword")), true);

        assertThat(only.takes(sword)).isTrue();
        assertThat(only.takes(PLAIN)).isFalse();
        assertThat(allBut.takes(sword)).isFalse();
        assertThat(allBut.takes(PLAIN)).isTrue();
        assertThat(Extraction.of(ExtractionMode.EXTRACTOR).takes(PLAIN)).isTrue();
        assertThat(new Extraction(ExtractionMode.EXTRACTOR, Set.of(), false).takes(PLAIN)).isFalse();
    }
}
