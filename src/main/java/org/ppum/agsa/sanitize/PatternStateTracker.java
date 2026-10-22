package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Incremental states for the immutable original HUI witness universe. */
final class PatternStateTracker {
    private static final class State {
        final PatternInfo original; long utility; GapTracker gaps;
        State(PatternInfo p){original=p;utility=p.utility();}
        boolean containsTid(int tid){return gaps==null?original.tids().contains(tid):gaps.contains(tid);}
        PeriodicityStats periodicity(){return gaps==null?original.periodicity():gaps.stats();}
        List<Integer> tids(){return gaps==null?original.tids():gaps.tidsSnapshot();}
        GapTracker ensureGaps(int n){if(gaps==null)gaps=new GapTracker(original.tids(),n);return gaps;}
    }
    private final Map<ItemsetKey,State> states=new HashMap<>();
    private final ImpactIndex index;
    PatternStateTracker(Map<ItemsetKey,PatternInfo> original){for(PatternInfo p:original.values())states.put(p.key(),new State(p));index=new ImpactIndex(original.keySet());}
    ImpactIndex index(){return index;}
    boolean containsTid(ItemsetKey key,int tid){return require(key).containsTid(tid);}
    PatternSnapshot status(ItemsetKey key,UtilityDatabase db,PeriodicityConstraints c){
        State s=require(key);PeriodicityStats ps=s.periodicity();boolean hui=s.utility>=c.minUtil();return new PatternSnapshot(s.utility,ps,hui,hui&&c.accepts(ps));
    }
    PatternInfo info(ItemsetKey key,UtilityDatabase db,PeriodicityConstraints c){
        State s=require(key);PeriodicityStats ps=s.periodicity();boolean hui=s.utility>=c.minUtil();return new PatternInfo(key,s.utility,ps,hui,hui&&c.accepts(ps),s.tids());
    }
    PatternSnapshot virtualStatus(ItemsetKey key,int tid,String item,int newQ,UtilityDatabase db,PeriodicityConstraints c){
        State s=require(key);if(!key.contains(item)||!s.containsTid(tid))return status(key,db,c);
        Transaction tx=db.transactionByTid(tid);int oldQ=tx.quantity(item);if(newQ>=oldQ)return status(key,db,c);
        long u=s.utility;PeriodicityStats ps=s.periodicity();
        if(newQ>0)u-=(long)(oldQ-newQ)*db.profit(item);
        else{u-=tx.itemsetUtility(key,db.profits());ps=s.gaps==null?PeriodicityStats.afterRemovingSorted(s.original.tids(),tid,db.size()):s.gaps.virtualStatsAfterRemove(tid);}
        boolean hui=u>=c.minUtil();return new PatternSnapshot(u,ps,hui,hui&&c.accepts(ps));
    }
    void commit(int tid,String item,int newQ,UtilityDatabase db){
        Transaction tx=db.transactionByTid(tid);int oldQ=tx.quantity(item);if(newQ>=oldQ)return;
        for(ItemsetKey key:index.affected(item)){
            State s=states.get(key);if(!s.containsTid(tid))continue;
            if(newQ>0)s.utility-=(long)(oldQ-newQ)*db.profit(item);
            else{s.utility-=tx.itemsetUtility(key,db.profits());s.ensureGaps(db.size()).remove(tid);}
        }
        tx.setQuantity(item,newQ);
    }
    Map<ItemsetKey,PatternInfo> snapshotAll(UtilityDatabase db,PeriodicityConstraints c){
        LinkedHashMap<ItemsetKey,PatternInfo> out=new LinkedHashMap<>(Math.max(16,states.size()*2));
        for(ItemsetKey k:states.keySet())out.put(k,info(k,db,c));return out;
    }
    private State require(ItemsetKey k){State s=states.get(k);if(s==null)throw new IllegalArgumentException("Pattern outside original HUI witness universe: "+k);return s;}
}
