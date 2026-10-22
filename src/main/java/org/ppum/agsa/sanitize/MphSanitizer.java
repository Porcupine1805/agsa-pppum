package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/**
 * Minimum-loss period-aware hiding. Tracks only original PHUIs (S ∪ N), not the
 * full HUI universe. Stops when the target is no longer a PHUI (period channel
 * allowed). Chooses the feasible one-cell action with smallest (MC, utility loss).
 */
public final class MphSanitizer implements Sanitizer {
    @Override public SanitizationResult sanitize(UtilityDatabase original,PeriodicityConstraints c,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo> originalHuiUniverse){
        long start=System.nanoTime();UtilityDatabase db=original.copy();List<Edit> edits=new ArrayList<>();
        LinkedHashMap<ItemsetKey,PatternInfo> tracked=new LinkedHashMap<>();
        for(PatternInfo p:originalHuiUniverse.values()) if(p.periodicHighUtility()) tracked.put(p.key(),p);
        PatternStateTracker tracker=new PatternStateTracker(tracked);
        Set<ItemsetKey> N=new HashSet<>(tracked.keySet()); N.removeAll(sensitive);
        List<ItemsetKey> ordered=new ArrayList<>(sensitive);
        ordered.sort(Comparator.<ItemsetKey>comparingInt(ItemsetKey::size).reversed().thenComparing(ItemsetKey::compareTo));
        long iter=0,maxIter=Math.max(20_000L,(long)original.size()*Math.max(1,sensitive.size())*8L);
        long candidates=0;
        while(true){
            ItemsetKey target=null;PatternInfo tInfo=null;
            for(ItemsetKey s:ordered){PatternInfo p=tracker.info(s,db,c);if(p.periodicHighUtility()){target=s;tInfo=p;break;}}
            if(target==null) return done(db,edits,true,"Sensitive PHUIs hidden via period-aware min-loss edits",start,candidates,tracked.size(),tracker,c);
            if(++iter>maxIter) return done(db,edits,false,"Iteration safeguard reached",start,candidates,tracked.size(),tracker,c);
            Candidate best=null;
            for(int tid:tInfo.tids()){
                Transaction tx=db.transactionByTid(tid);
                for(String item:target.items()){
                    int oldQ=tx.quantity(item); if(oldQ<=0) continue;
                    long profit=db.profit(item);
                    LinkedHashSet<Integer> qs=utilityBreakpoints(item,tid,oldQ,tracker,db,c);
                    // In decrement notation B* also contains d=1.  This is the
                    // least-loss representative before the first label event.
                    // It is relevant to a one-step optimum only when it hides;
                    // using it merely as progress can cause O(quantity) loops.
                    boolean unitOnly=oldQ>1&&!qs.contains(oldQ-1);
                    if(oldQ>1)qs.add(oldQ-1);
                    qs.add(0);
                    PatternSnapshot beforeT=tracker.status(target,db,c);
                    for(int newQ:qs){
                        candidates++;
                        PatternSnapshot afterT=tracker.virtualStatus(target,tid,item,newQ,db,c);
                        boolean hidden=!afterT.periodicHighUtility();
                        if(unitOnly&&newQ==oldQ-1&&!hidden)continue;
                        boolean progress=afterT.utility()<beforeT.utility()
                                || afterT.periodicity().avgPeriod()>beforeT.periodicity().avgPeriod()
                                || afterT.periodicity().maxPeriod()>beforeT.periodicity().maxPeriod();
                        if(!hidden && !progress) continue;
                        int mc=0;
                        // Only patterns containing the edited item and still
                        // supported at this TID can change under this cell edit.
                        for(ItemsetKey n:tracker.index().affected(item)){
                            if(!N.contains(n)||!tracker.containsTid(n,tid))continue;
                            PatternSnapshot b=tracker.status(n,db,c);
                            if(!b.periodicHighUtility()) continue;
                            PatternSnapshot a2=tracker.virtualStatus(n,tid,item,newQ,db,c);
                            if(!a2.periodicHighUtility()) mc++;
                        }
                        long loss=(long)(oldQ-newQ)*profit;
                        Candidate cand=new Candidate(tid,item,newQ,hidden,mc,loss,oldQ-newQ);
                        if(best==null||cand.betterThan(best)) best=cand;
                    }
                }
            }
            if(best==null) return done(db,edits,false,"No PHUI-hiding local edit",start,candidates,tracked.size(),tracker,c);
            int oldQ=db.transactionByTid(best.tid).quantity(best.item);
            tracker.commit(best.tid,best.item,best.newQ,db);
            edits.add(new Edit(Math.toIntExact(iter),best.tid,best.item,oldQ,best.newQ,target.toString(),-best.mc,
                    "MPH min (hide="+best.hidden+", MC="+best.mc+", loss="+best.loss+")"));
        }
    }
    /**
     * Enumerates all utility events in the tracked objective domain.  Positive
     * residual quantities preserve support and period statistics, so a tracked
     * pattern can change HUI/PHUI membership only at this breakpoint.  The
     * caller adds deletion as the sole period-changing action.
     */
    private static LinkedHashSet<Integer> utilityBreakpoints(String item,int tid,int oldQ,
            PatternStateTracker tracker,UtilityDatabase db,PeriodicityConstraints c){
        LinkedHashSet<Integer> out=new LinkedHashSet<>();
        long profit=db.profit(item);
        for(ItemsetKey key:tracker.index().affected(item)){
            if(!tracker.containsTid(key,tid))continue;
            long utility=tracker.status(key,db,c).utility();
            if(utility<c.minUtil())continue;
            long units=(utility-c.minUtil()+profit)/profit;
            long q=(long)oldQ-units;
            if(q>0&&q<oldQ)out.add((int)q);
        }
        return out;
    }
    private record Candidate(int tid,String item,int newQ,boolean hidden,int mc,long loss,int dq){
        boolean betterThan(Candidate o){
            if(hidden!=o.hidden) return hidden;
            if(mc!=o.mc) return mc<o.mc;
            if(loss!=o.loss) return loss<o.loss;
            if(dq!=o.dq) return dq<o.dq;
            if(tid!=o.tid) return tid<o.tid;
            return item.compareTo(o.item)<0;
        }
    }
    private static SanitizationResult done(UtilityDatabase db,List<Edit> edits,boolean ok,String msg,long start,long cand,int tracked,PatternStateTracker tracker,PeriodicityConstraints c){
        return new SanitizationResult("MPH",db,List.copyOf(edits),ok,msg,(System.nanoTime()-start)/1_000_000L,
                new SanitizationDiagnostics(cand,0,cand,tracked),tracker.snapshotAll(db,c));
    }
}
