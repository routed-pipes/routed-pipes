package routedpipes.domain.guide;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuideChapterTest {

    @Test
    void pipesAndTubesAreTogether() {
        assertThat(GuideChapter.of("transport_pipe", true)).isEqualTo(GuideChapter.PIPES);
        assertThat(GuideChapter.of("chassis_pipe_mk3", true)).isEqualTo(GuideChapter.PIPES);
        assertThat(GuideChapter.of("fluid_supplier_pipe", true)).isEqualTo(GuideChapter.PIPES);
        assertThat(GuideChapter.of("curve_tube", true)).isEqualTo(GuideChapter.PIPES);
    }

    @Test
    void modulesAndUpgradesHaveChaptersOfTheirOwn() {
        assertThat(GuideChapter.of("module_item_sink", false)).isEqualTo(GuideChapter.MODULES);
        assertThat(GuideChapter.of("upgrade_sneaky", false)).isEqualTo(GuideChapter.UPGRADES);
    }

    @Test
    void theBlankModuleIsAPartLikeTheChips() {
        assertThat(GuideChapter.of("module_blank", false)).isEqualTo(GuideChapter.TOOLS);
        assertThat(GuideChapter.of("chip_fpga", false)).isEqualTo(GuideChapter.TOOLS);
    }

    @Test
    void otherBlocksAndItems() {
        assertThat(GuideChapter.of("request_table", true)).isEqualTo(GuideChapter.BLOCKS);
        assertThat(GuideChapter.of("power_junction", true)).isEqualTo(GuideChapter.BLOCKS);
        assertThat(GuideChapter.of("pipe_controller", false)).isEqualTo(GuideChapter.TOOLS);
        assertThat(GuideChapter.of("sign_creator", false)).isEqualTo(GuideChapter.TOOLS);
    }

    @Test
    void nothingRegisteredBelongsToGettingStarted() {
        assertThat(GuideChapter.of("guide_book", false)).isNotEqualTo(GuideChapter.GETTING_STARTED);
    }
}
