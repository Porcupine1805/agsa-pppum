package org.ppum.agsa.mining;

import org.ppum.agsa.model.*;
import java.util.*;

public final class PatternEvaluator {
    private PatternEvaluator() {}

    public static PatternInfo evaluate(ItemsetKey key, UtilityDatabase db, PeriodicityConstraints c) {
        long utility = 0L;
        List<Integer> tids = new ArrayList<>();
        for (Transaction t : db.transactions()) {
            if (t.containsAll(key)) {
                tids.add(t.tid());
                utility += t.itemsetUtility(key, db.profits());
            }
        }
        PeriodicityStats ps = PeriodicityStats.fromTids(tids, db.size());
        boolean hui = utility >= c.minUtil();
        boolean phui = hui && c.accepts(ps);
        return new PatternInfo(key, utility, ps, hui, phui, List.copyOf(tids));
    }
}
