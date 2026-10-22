package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/**
 * Clean-room reference family for Liu-Wen-Zuo (Applied Intelligence 2020):
 * SMAU, SMIU and SMSE. These are non-periodic PPUM baselines; they hide a
 * sensitive PHUI by forcing it below minUtil and do not use periodicity in the
 * decision rule. Consequently they are contextual/legacy baselines for PPPUM.
 */
abstract class AbstractLiuSanitizer implements Sanitizer {
    private ImpactIndex impactIndex;
    protected abstract String name();
    protected boolean stopWhenNoLongerPhui(){return false;}
    protected abstract String chooseItem(ItemsetKey target,Transaction tx,UtilityDatabase db,PatternStateTracker tracker,Set<ItemsetKey> nonSensitiveHuis,PeriodicityConstraints c);

    @Override public SanitizationResult sanitize(UtilityDatabase original,PeriodicityConstraints c,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo> originalHuiUniverse){
        long start=System.nanoTime();UtilityDatabase db=original.copy();List<Edit> edits=new ArrayList<>();PatternStateTracker tracker=new PatternStateTracker(originalHuiUniverse);this.impactIndex=tracker.index();
        Set<ItemsetKey> nonSensitive=new HashSet<>(originalHuiUniverse.keySet());nonSensitive.removeAll(sensitive);
        int[] originalConflictCount=new int[original.size()+1];
        for(ItemsetKey x:nonSensitive){PatternInfo pi=originalHuiUniverse.get(x);for(int tid:pi.tids())if(tid>=1&&tid<originalConflictCount.length)originalConflictCount[tid]++;}
        List<ItemsetKey> ordered=new ArrayList<>(sensitive);ordered.sort(Comparator.<ItemsetKey>comparingInt(ItemsetKey::size).reversed().thenComparing(ItemsetKey::compareTo));
        long iter=0,maxIter=Math.max(20_000L,(long)original.size()*Math.max(1,sensitive.size())*8L);
        while(true){
            ItemsetKey target=null;PatternInfo targetInfo=null;for(ItemsetKey s:ordered){PatternInfo p=tracker.info(s,db,c);boolean active=stopWhenNoLongerPhui()?p.periodicHighUtility():p.highUtility();if(active){target=s;targetInfo=p;break;}}
            if(target==null)return result(db,edits,true,stopWhenNoLongerPhui()?"All sensitive PHUIs hidden (matched stopping predicate)":"All sensitive itemsets forced below minUtil (legacy PPUM semantics)",start,tracker,c,originalHuiUniverse.size());
            if(++iter>maxIter)return result(db,edits,false,"Iteration safeguard reached",start,tracker,c,originalHuiUniverse.size());
            int tid=selectVictimTransaction(target,targetInfo,db,originalConflictCount);Transaction tx=db.transactionByTid(tid);
            String item=chooseItem(target,tx,db,tracker,nonSensitive,c);if(item==null)return result(db,edits,false,"No victim item available",start,tracker,c,originalHuiUniverse.size());
            int oldQ=tx.quantity(item);long profit=db.profit(item),excess=Math.max(1L,targetInfo.utility()-c.minUtil()+1L);long units=(excess+profit-1)/profit;int newQ=(int)Math.max(0L,oldQ-Math.min(oldQ,units));if(newQ==oldQ)newQ=Math.max(0,oldQ-1);
            tracker.commit(tid,item,newQ,db);edits.add(new Edit(Math.toIntExact(iter),tid,item,oldQ,newQ,target.toString(),0.0,name()+" clean-room Liu-Wen-Zuo reference rule"));
        }
    }

    private int selectVictimTransaction(ItemsetKey target,PatternInfo p,UtilityDatabase db,int[] conflictCount){
        int bestTid=-1,bestConflicts=Integer.MAX_VALUE;long bestSensitive=Long.MIN_VALUE;
        for(int tid:p.tids()){
            int conflicts=tid<conflictCount.length?conflictCount[tid]:Integer.MAX_VALUE;
            long su=db.transactionByTid(tid).itemsetUtility(target,db.profits());
            if(conflicts<bestConflicts||(conflicts==bestConflicts&&su>bestSensitive)||(conflicts==bestConflicts&&su==bestSensitive&&(bestTid<0||tid<bestTid))){bestTid=tid;bestConflicts=conflicts;bestSensitive=su;}
        }
        if(bestTid<0)throw new IllegalStateException("Target has no support");return bestTid;
    }

    protected int sideEffectCount(String item,int tid,UtilityDatabase db,PatternStateTracker tracker,Set<ItemsetKey>nonSensitive,PeriodicityConstraints c){
        int n=0;for(ItemsetKey x:impactIndex.affected(item))if(nonSensitive.contains(x)&&tracker.containsTid(x,tid)&&tracker.status(x,db,c).highUtility())n++;return n;
    }
    private SanitizationResult result(UtilityDatabase db,List<Edit>edits,boolean feasible,String msg,long start,PatternStateTracker tracker,PeriodicityConstraints c,int witness){
        return new SanitizationResult(name(),db,List.copyOf(edits),feasible,msg,(System.nanoTime()-start)/1_000_000L,SanitizationDiagnostics.none(witness),tracker.snapshotAll(db,c));
    }
}
