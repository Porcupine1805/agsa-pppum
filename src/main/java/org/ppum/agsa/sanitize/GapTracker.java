package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Ordered occurrence/gap tracker, created lazily for patterns touched by deletion. */
final class GapTracker {
    private final int n;
    private final TreeSet<Integer> tids=new TreeSet<>();
    private final TreeMap<Integer,Integer> allGaps=new TreeMap<>();
    private final TreeMap<Integer,Integer> internalGaps=new TreeMap<>();

    GapTracker(List<Integer> ids,int databaseSize){this.n=databaseSize;for(int tid:ids)tids.add(tid);rebuildGaps();}
    boolean contains(int tid){return tids.contains(tid);} int support(){return tids.size();}
    List<Integer> tidsSnapshot(){int[] a=new int[tids.size()];int p=0;for(int x:tids)a[p++]=x;return new PrimitiveIntList(a);}
    PeriodicityStats stats(){return statsFromCurrent();}

    PeriodicityStats virtualStatsAfterRemove(int tid){
        if(!tids.contains(tid))return statsFromCurrent();
        int old=tids.size();if(old==1)return new PeriodicityStats(0,Integer.MAX_VALUE,Integer.MAX_VALUE,n);
        Integer prev=tids.lower(tid),next=tids.higher(tid);
        applyGapDelta(prev,tid,next,-1);
        PeriodicityStats ps=statsForSupport(old-1,prev,next);
        applyGapDelta(prev,tid,next,+1);
        return ps;
    }

    void remove(int tid){
        if(!tids.contains(tid))return;
        if(tids.size()==1){tids.remove(tid);allGaps.clear();internalGaps.clear();return;}
        Integer prev=tids.lower(tid),next=tids.higher(tid);applyGapDelta(prev,tid,next,-1);tids.remove(tid);
    }

    private PeriodicityStats statsForSupport(int support,Integer prev,Integer next){
        if(support<=0)return new PeriodicityStats(0,Integer.MAX_VALUE,Integer.MAX_VALUE,n);
        int max=allGaps.isEmpty()?n:allGaps.lastKey();int min;
        if(support>=2)min=internalGaps.isEmpty()?Integer.MAX_VALUE:internalGaps.firstKey();
        else min=Integer.MAX_VALUE;
        return new PeriodicityStats(support,min,max,n/(double)(support+1));
    }
    private PeriodicityStats statsFromCurrent(){
        int k=tids.size();if(k==0)return new PeriodicityStats(0,Integer.MAX_VALUE,Integer.MAX_VALUE,n);
        int max=allGaps.lastKey();int min=k>=2?internalGaps.firstKey():Integer.MAX_VALUE;
        return new PeriodicityStats(k,min,max,n/(double)(k+1));
    }
    /** direction -1 applies removal; +1 restores pre-removal counts. */
    private void applyGapDelta(Integer prev,int tid,Integer next,int direction){
        if(direction==-1){
            if(prev==null){dec(allGaps,tid);dec(allGaps,next-tid);inc(allGaps,next);dec(internalGaps,next-tid);}
            else if(next==null){dec(allGaps,tid-prev);dec(allGaps,n-tid);inc(allGaps,n-prev);dec(internalGaps,tid-prev);}
            else{dec(allGaps,tid-prev);dec(allGaps,next-tid);inc(allGaps,next-prev);dec(internalGaps,tid-prev);dec(internalGaps,next-tid);inc(internalGaps,next-prev);}
        }else{
            if(prev==null){dec(allGaps,next);inc(allGaps,tid);inc(allGaps,next-tid);inc(internalGaps,next-tid);}
            else if(next==null){dec(allGaps,n-prev);inc(allGaps,tid-prev);inc(allGaps,n-tid);inc(internalGaps,tid-prev);}
            else{dec(allGaps,next-prev);inc(allGaps,tid-prev);inc(allGaps,next-tid);dec(internalGaps,next-prev);inc(internalGaps,tid-prev);inc(internalGaps,next-tid);}
        }
    }
    private void rebuildGaps(){allGaps.clear();internalGaps.clear();if(tids.isEmpty())return;int prev=0;boolean first=true;for(int t:tids){int g=t-prev;inc(allGaps,g);if(!first)inc(internalGaps,g);prev=t;first=false;}inc(allGaps,n-prev);}
    private static void inc(TreeMap<Integer,Integer>m,int x){m.merge(x,1,Integer::sum);}
    private static void dec(TreeMap<Integer,Integer>m,int x){Integer c=m.get(x);if(c==null)throw new IllegalStateException("Missing gap "+x);if(c==1)m.remove(x);else m.put(x,c-1);}
}
