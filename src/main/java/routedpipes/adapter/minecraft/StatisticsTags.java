package routedpipes.adapter.minecraft;

import routedpipes.domain.statistics.PipeStatistics;

// only the lifetime counts of a pipe are saved, one number per kind
final class StatisticsTags {

    private StatisticsTags() {
    }

    static long[] save(PipeStatistics statistics) {
        PipeStatistics.Kind[] kinds = PipeStatistics.Kind.values();
        long[] saved = new long[kinds.length];
        for (PipeStatistics.Kind kind : kinds) {
            saved[kind.ordinal()] = statistics.lifetime(kind);
        }
        return saved;
    }

    // an array of another length is not from this pipe, updates for clients come without any
    static void load(PipeStatistics statistics, long[] saved) {
        PipeStatistics.Kind[] kinds = PipeStatistics.Kind.values();
        if (saved.length != kinds.length) {
            return;
        }
        for (PipeStatistics.Kind kind : kinds) {
            statistics.restoreLifetime(kind, saved[kind.ordinal()]);
        }
    }
}
