package logisticspipes.domain.guide;

import java.util.ArrayList;
import java.util.List;

// lays the entries of a chapter out on pages of a fixed number of lines
// an entry starts on a new page when it does not fit on the rest of the current one, unless it does not fit on any page
public final class GuidePages {

    // the lines from up to before to of one entry, they are on one page
    public record Piece(int entry, int from, int to) {
    }

    private GuidePages() {
    }

    // lines holds how many lines each entry takes, a blank line goes between two entries on a page
    public static List<List<Piece>> layout(List<Integer> lines, int perPage) {
        if (perPage < 1) {
            throw new IllegalArgumentException("a page needs room for a line");
        }
        List<List<Piece>> pages = new ArrayList<>();
        List<Piece> page = new ArrayList<>();
        int used = 0;
        for (int entry = 0; entry < lines.size(); entry++) {
            int length = lines.get(entry);
            int gap = page.isEmpty() ? 0 : 1;
            boolean fits = used + gap + length <= perPage;
            if (!page.isEmpty() && !fits && (length <= perPage || used + gap >= perPage)) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
                gap = 0;
            }
            used += gap;
            int from = 0;
            while (length - from > perPage - used) {
                int to = from + perPage - used;
                page.add(new Piece(entry, from, to));
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
                from = to;
            }
            page.add(new Piece(entry, from, length));
            used += length - from;
        }
        if (!page.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }
}
