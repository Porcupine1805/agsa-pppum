package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Internal deterministic regression test for the balanced gap tracker. */
public final class TrackerVerifier {
    private TrackerVerifier(){}
    public static void run(){
        Random rnd=new Random(20260905L);
        for(int trial=0;trial<200;trial++){
            int n=5+rnd.nextInt(80);TreeSet<Integer> s=new TreeSet<>();for(int t=1;t<=n;t++)if(rnd.nextDouble()<0.25)s.add(t);if(s.isEmpty())s.add(1+rnd.nextInt(n));
            ArrayList<Integer> ids=new ArrayList<>(s);GapTracker g=new GapTracker(ids,n);assertSame(g.stats(),PeriodicityStats.fromTids(ids,n));
            while(!ids.isEmpty()){
                int tid=ids.get(rnd.nextInt(ids.size()));ArrayList<Integer> after=new ArrayList<>(ids);after.remove(Integer.valueOf(tid));
                assertSame(g.virtualStatsAfterRemove(tid),PeriodicityStats.fromTids(after,n));
                g.remove(tid);ids=after;assertSame(g.stats(),PeriodicityStats.fromTids(ids,n));
            }
        }
    }
    private static void assertSame(PeriodicityStats a,PeriodicityStats b){
        if(a.support()!=b.support()||a.minPeriod()!=b.minPeriod()||a.maxPeriod()!=b.maxPeriod()||Math.abs(a.avgPeriod()-b.avgPeriod())>1e-12)
            throw new AssertionError("GapTracker mismatch: "+a+" vs "+b);
    }
}
