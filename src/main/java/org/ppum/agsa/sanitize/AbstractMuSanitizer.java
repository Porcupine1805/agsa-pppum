package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Clean-room independent implementation of the MU-MAP/MU-MIP decision rules. */
abstract class AbstractMuSanitizer implements Sanitizer {
    protected abstract String name();
    /** true for MU-MAP (maximum singleton maxPer), false for MU-MIP. */
    protected abstract boolean chooseMaximumMaxPer();

    @Override public SanitizationResult sanitize(UtilityDatabase original,PeriodicityConstraints c,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo> originalHuiUniverse){
        long start=System.nanoTime();UtilityDatabase db=original.copy();List<Edit> edits=new ArrayList<>();
        PatternStateTracker tracker=new PatternStateTracker(originalHuiUniverse);OccurrenceIndex occ=new OccurrenceIndex(db);
        List<ItemsetKey> ordered=new ArrayList<>(sensitive);ordered.sort(Comparator.<ItemsetKey>comparingInt(ItemsetKey::size).reversed().thenComparing(ItemsetKey::compareTo));
        long iter=0,maxIter=Math.max(10_000L,(long)original.size()*Math.max(1,sensitive.size())*4L);
        while(true){
            ItemsetKey target=null;PatternInfo targetInfo=null;
            for(ItemsetKey s:ordered){PatternInfo p=tracker.info(s,db,c);if(p.periodicHighUtility()){target=s;targetInfo=p;break;}}
            if(target==null)return result(db,edits,true,"All supplied sensitive PHUIs hidden",start,tracker,c,originalHuiUniverse.size());
            if(++iter>maxIter)return result(db,edits,false,"Iteration safeguard reached",start,tracker,c,originalHuiUniverse.size());
            final ItemsetKey tk=target;
            int tid=targetInfo.tids().stream().max(Comparator.<Integer>comparingLong(t->db.transactionByTid(t).itemsetUtility(tk,db.profits())).thenComparingInt(Integer::intValue)).orElseThrow();
            Transaction tx=db.transactionByTid(tid);
            String item=null;int best=chooseMaximumMaxPer()?Integer.MIN_VALUE:Integer.MAX_VALUE;
            for(String i:target.items())if(tx.contains(i)){int mp=occ.maxPer(i);if(item==null||(chooseMaximumMaxPer()?mp>best:mp<best)||(mp==best&&i.compareTo(item)<0)){item=i;best=mp;}}
            if(item==null)return result(db,edits,false,"No victim item available",start,tracker,c,originalHuiUniverse.size());
            int oldQ=tx.quantity(item);long profit=db.profit(item),excess=Math.max(1L,targetInfo.utility()-c.minUtil()+1L);long units=(excess+profit-1)/profit;
            int newQ=(int)Math.max(0L,oldQ-Math.min(oldQ,units));if(newQ==oldQ)newQ=Math.max(0,oldQ-1);
            boolean deletes=newQ==0;tracker.commit(tid,item,newQ,db);if(deletes)occ.commitDeletion(item,tid);
            edits.add(new Edit(Math.toIntExact(iter),tid,item,oldQ,newQ,target.toString(),0.0,name()+" reference rule: max sensitive-utility TID; "+(chooseMaximumMaxPer()?"max":"min")+" singleton maxPer item"));
        }
    }
    private SanitizationResult result(UtilityDatabase db,List<Edit>edits,boolean feasible,String msg,long start,PatternStateTracker tracker,PeriodicityConstraints c,int witness){
        return new SanitizationResult(name(),db,List.copyOf(edits),feasible,msg,(System.nanoTime()-start)/1_000_000L,SanitizationDiagnostics.none(witness),tracker.snapshotAll(db,c));
    }
}
