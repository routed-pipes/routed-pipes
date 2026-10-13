package routedpipes.domain.guide;

// the chapters of the guide book in the order they are listed
public enum GuideChapter {
    GETTING_STARTED,
    PIPES,
    MODULES,
    UPGRADES,
    BLOCKS,
    TOOLS;

    // where a thing of the mod is described, by its id and whether it can be placed
    public static GuideChapter of(String id, boolean block) {
        if (id.startsWith("module_") && !id.equals("module_blank")) {
            return MODULES;
        }
        if (id.startsWith("upgrade_")) {
            return UPGRADES;
        }
        if (block && (id.contains("pipe") || id.endsWith("_tube"))) {
            return PIPES;
        }
        return block ? BLOCKS : TOOLS;
    }
}
