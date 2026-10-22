package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.PatternEvaluator;
import org.ppum.agsa.model.*;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Compaction can create a PHUI in a pattern that does not contain the edited item. */
final class CompactionStudy {
    private CompactionStudy(){}
    static void run(Map<String,String> a) throws Exception {
        UtilityDatabase d=DatabaseIO.readSimple(
                Path.of(a.getOrDefault("db","data/compaction_db.txt")),
                Path.of(a.getOrDefault("profits","data/compaction_profits.txt")));
        PeriodicityConstraints c=new PeriodicityConstraints(20,1,20,1,3);
        ItemsetKey x=ItemsetKey.parse("x");
        PatternInfo before=PatternEvaluator.evaluate(x,d,c);
        UtilityDatabase d2=d.copy();
        d2.transactionByTid(10).setQuantity("y",0);
        PatternInfo afterFixed=PatternEvaluator.evaluate(x,d2,c);
        PatternInfo afterComp=PatternEvaluator.evaluate(x,d2.compactedTimeline(),c);
        System.out.printf(Locale.ROOT,"n=%d nonempty_after=%d%n",d.size(),d2.nonEmptyTransactions());
        System.out.printf(Locale.ROOT,"BEFORE  U=%d PHUI=%s %s%n",before.utility(),before.periodicHighUtility(),fmt(before.periodicity()));
        System.out.printf(Locale.ROOT,"FIXED   U=%d PHUI=%s %s  (X does not contain y)%n",afterFixed.utility(),afterFixed.periodicHighUtility(),fmt(afterFixed.periodicity()));
        System.out.printf(Locale.ROOT,"COMPACT U=%d PHUI=%s %s%n",afterComp.utility(),afterComp.periodicHighUtility(),fmt(afterComp.periodicity()));
        if(afterComp.periodicHighUtility() && !before.periodicHighUtility() && !afterFixed.periodicHighUtility())
            System.out.println("COMPACTION AC EXAMPLE: {x} becomes PHUI only after reindexing.");
        else
            System.out.println("COMPACTION AC EXAMPLE FAILED");
    }
    private static String fmt(PeriodicityStats s){
        return String.format(Locale.ROOT,"sup=%d minP=%d maxP=%d avg=%.4f",s.support(),s.minPeriod(),s.maxPeriod(),s.avgPeriod());
    }
}
