package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;

import java.nio.file.Path;
import java.util.*;

/** Classify how each one-cell deletion hides the 10-transaction running example. */
final class HideChannelStudy {
    private HideChannelStudy(){}
    static void run(Map<String,String> a) throws Exception {
        UtilityDatabase d=DatabaseIO.readSimple(
                Path.of(a.getOrDefault("db","data/example_db.txt")),
                Path.of(a.getOrDefault("profits","data/example_profits.txt")));
        PeriodicityConstraints c=new PeriodicityConstraints(260,1,6,1,2);
        Map<ItemsetKey,PatternInfo> orig=new VerticalHuiMiner().mine(d,c,0);
        Set<ItemsetKey> P=new LinkedHashSet<>();
        for(PatternInfo p:orig.values()) if(p.periodicHighUtility()) P.add(p.key());
        ItemsetKey abi=ItemsetKey.parse("a,b,i");
        ItemsetKey ai=ItemsetKey.parse("a,i");
        System.out.printf(Locale.ROOT,"example tx=%d TU=%d H=%d P=%d%n",d.size(),d.totalUtility(),orig.size(),P.size());
        System.out.println("target,tid,item,newQ,loss,hidden,channel,stillHUI,MC,damaged");
        dump(d,c,orig,P,abi);
        System.out.println("-- after T8:i=0, remaining {a,i} --");
        UtilityDatabase d2=d.copy(); d2.transactionByTid(8).setQuantity("i",0);
        Map<ItemsetKey,PatternInfo> o2=new VerticalHuiMiner().mine(d2,c,0);
        Set<ItemsetKey> P2=new LinkedHashSet<>();
        for(PatternInfo p:o2.values()) if(p.periodicHighUtility()) P2.add(p.key());
        dump(d2,c,o2,P2,ai);
    }
    private static void dump(UtilityDatabase d,PeriodicityConstraints c,Map<ItemsetKey,PatternInfo> orig,Set<ItemsetKey> P,ItemsetKey target){
        PatternInfo t=orig.get(target);
        if(t==null){System.out.println(target+" not mined");return;}
        Set<ItemsetKey> N=new LinkedHashSet<>(P); N.remove(target);
        for(int tid:t.tids()){
            Transaction tx=d.transactionByTid(tid);
            for(String item:target.items()){
                int q=tx.quantity(item); if(q<=0) continue;
                for(int newQ:new int[]{Math.max(0,q-1),0}){
                    if(newQ>=q) continue;
                    UtilityDatabase d2=d.copy(); d2.transactionByTid(tid).setQuantity(item,newQ);
                    PatternInfo after=org.ppum.agsa.mining.PatternEvaluator.evaluate(target,d2,c);
                    boolean hidden=!after.periodicHighUtility();
                    String ch=channel(t,after,c);
                    long loss=(long)(q-newQ)*d.profit(item);
                    List<String> damaged=new ArrayList<>();
                    for(ItemsetKey n:N){
                        PatternInfo b=orig.get(n);
                        if(b==null||!b.periodicHighUtility()) continue;
                        PatternInfo a2=org.ppum.agsa.mining.PatternEvaluator.evaluate(n,d2,c);
                        if(!a2.periodicHighUtility()) damaged.add(n+"/"+channel(b,a2,c));
                    }
                    System.out.printf(Locale.ROOT,"%s,T%d,%s,%d,%d,%s,%s,hui=%s,MC=%d,%s%n",
                            target,tid,item,newQ,loss,hidden,ch,after.highUtility(),damaged.size(),damaged);
                }
            }
        }
    }
    private static String channel(PatternInfo before,PatternInfo after,PeriodicityConstraints c){
        if(after.periodicHighUtility()) return "still-PHUI";
        List<String> why=new ArrayList<>();
        if(before.utility()>=c.minUtil() && after.utility()<c.minUtil()) why.add("U");
        if(before.periodicity().maxPeriod()<=c.maxPer() && after.periodicity().maxPeriod()>c.maxPer()) why.add("maxPer");
        if(before.periodicity().avgPeriod()<=c.maxAvg() && after.periodicity().avgPeriod()>c.maxAvg()) why.add("avgPer");
        if(before.periodicity().minPeriod()>=c.minPer() && after.periodicity().minPeriod()<c.minPer()) why.add("minPer-down");
        if(why.isEmpty()) why.add("other");
        return String.join("+",why);
    }
}
