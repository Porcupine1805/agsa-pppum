package org.ppum.agsa.model;

import java.util.List;

/** Compact PHM/PPPUM periodicity statistics (no duplicate period list retained). */
public record PeriodicityStats(int support, int minPeriod, int maxPeriod, double avgPeriod) {
    public static PeriodicityStats fromTids(List<Integer> tids, int databaseSize) {
        int k=tids.size();
        if(k==0)return new PeriodicityStats(0,Integer.MAX_VALUE,Integer.MAX_VALUE,databaseSize);
        int prev=0,max=0,min=Integer.MAX_VALUE;
        for(int i=0;i<k;i++){
            int tid=(tids instanceof PrimitiveIntList p)?p.getInt(i):tids.get(i);
            int gap=tid-prev; if(gap>max)max=gap;
            if(i>0&&gap<min)min=gap;
            prev=tid;
        }
        int tail=databaseSize-prev; if(tail>max)max=tail;
        // PHM excludes the two boundary periods from minPer. A singleton has
        // no internal period, hence minPer=+infinity (Integer.MAX_VALUE).
        double avg=databaseSize/(double)(k+1);
        return new PeriodicityStats(k,min,max,avg);
    }
    /** Computes periodicity after deleting one known occurrence without allocating a new TID list. */
    public static PeriodicityStats afterRemovingSorted(List<Integer> tids,int removeTid,int databaseSize){
        int prev=0,max=0,min=Integer.MAX_VALUE,seen=0;boolean found=false;
        for(int i=0;i<tids.size();i++){int tid=(tids instanceof PrimitiveIntList p)?p.getInt(i):tids.get(i);if(tid==removeTid){found=true;continue;}int gap=tid-prev;if(gap>max)max=gap;if(seen>0&&gap<min)min=gap;prev=tid;seen++;}
        if(!found)return fromTids(tids,databaseSize);
        if(seen==0)return new PeriodicityStats(0,Integer.MAX_VALUE,Integer.MAX_VALUE,databaseSize);
        int tail=databaseSize-prev;if(tail>max)max=tail;
        return new PeriodicityStats(seen,min,max,databaseSize/(double)(seen+1));
    }

}
