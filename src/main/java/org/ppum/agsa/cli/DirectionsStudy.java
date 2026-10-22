package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.PatternEvaluator;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.MphSanitizer;
import org.ppum.agsa.sanitize.MuMapSanitizer;
import org.ppum.agsa.sanitize.SanitizationResult;
import org.ppum.agsa.sanitize.SmauSanitizer;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Machine-checked write-up of the three completed directions. */
final class DirectionsStudy {
    private DirectionsStudy(){}
    static void run(Map<String,String> a) throws Exception {
        Path out=Path.of(a.getOrDefault("out","results/three_directions"));
        Files.createDirectories(out);
        UtilityDatabase d=DatabaseIO.readSimple(Path.of("data/example_db.txt"),Path.of("data/example_profits.txt"));
        regime(d,out);
        actionSet(d,out);
        compaction(out);
        algorithmOnExample(d,out);
        System.out.println("DIRECTIONS PASS: wrote "+out.toAbsolutePath());
    }

    /** Direction 1: Θ (Zhou-like minPer=1) vs Θ_g (minPer=2). */
    private static void regime(UtilityDatabase d,Path out)throws Exception{
        PeriodicityConstraints theta=new PeriodicityConstraints(260,1,6,1,2);
        PeriodicityConstraints thetaG=new PeriodicityConstraints(260,2,6,1,2);
        ItemsetKey ai=ItemsetKey.parse("a,i");
        PatternInfo b1=PatternEvaluator.evaluate(ai,d,theta);
        PatternInfo b2=PatternEvaluator.evaluate(ai,d,thetaG);
        UtilityDatabase d2=d.copy(); d2.transactionByTid(6).setQuantity("i",0);
        PatternInfo a1=PatternEvaluator.evaluate(ai,d2,theta);
        PatternInfo a2=PatternEvaluator.evaluate(ai,d2,thetaG);
        try(BufferedWriter w=Files.newBufferedWriter(out.resolve("direction1_regimes.csv"),StandardCharsets.UTF_8)){
            w.write("regime,minPer,pattern,when,utility,minP,maxP,avgP,PHUI,interpretation\n");
            row(w,"Theta",1,ai,"before",b1,"minPer>=1 already holds; deletion cannot create AC");
            row(w,"Theta",1,ai,"after T6:i=0",a1,"still not an artificial PHUI under Theta");
            row(w,"Theta_g",2,ai,"before",b2,"minP=1 < 2 so {a,i} is HUI but not PHUI");
            row(w,"Theta_g",2,ai,"after T6:i=0",a2,"minP rises; {a,i} becomes PHUI: AC channel");
        }
        if(!b1.periodicHighUtility()) throw new AssertionError("{a,i} must be PHUI under Theta (minPer=1) before deletion");
        if(b2.periodicHighUtility()) throw new AssertionError("{a,i} must not be PHUI under Theta_g before deletion");
        if(!a2.periodicHighUtility()) throw new AssertionError("{a,i} must become PHUI under Theta_g after T6:i=0");
        System.out.printf(Locale.ROOT,"D1 Theta {a,i} before PHUI=%s after PHUI=%s | Theta_g before PHUI=%s after PHUI=%s%n",
                b1.periodicHighUtility(),a1.periodicHighUtility(),b2.periodicHighUtility(),a2.periodicHighUtility());
    }

    /** Direction 2: no interior decrement can hide via period; deletion is the period action. */
    private static void actionSet(UtilityDatabase d,Path out)throws Exception{
        PeriodicityConstraints c=new PeriodicityConstraints(260,1,6,1,2);
        ItemsetKey abi=ItemsetKey.parse("a,b,i");
        PatternInfo t=PatternEvaluator.evaluate(abi,d,c);
        int interiorPeriod=0,interiorUtility=0,interiorNone=0,deletionPeriod=0,deletionUtility=0,bestLoss=Integer.MAX_VALUE;
        String best="";
        try(BufferedWriter w=Files.newBufferedWriter(out.resolve("direction2_action_set.csv"),StandardCharsets.UTF_8)){
            w.write("tid,item,oldQ,newQ,loss,hidden,channel,MC0_hide,note\n");
            for(int tid:t.tids()){
                Transaction tx=d.transactionByTid(tid);
                for(String item:abi.items()){
                    int q=tx.quantity(item); if(q<=0) continue;
                    for(int newQ=q-1;newQ>=0;newQ--){
                        UtilityDatabase d2=d.copy(); d2.transactionByTid(tid).setQuantity(item,newQ);
                        PatternInfo after=PatternEvaluator.evaluate(abi,d2,c);
                        boolean hidden=!after.periodicHighUtility();
                        String ch=channel(t,after,c);
                        long loss=(long)(q-newQ)*d.profit(item);
                        boolean interior=newQ>0;
                        if(interior){
                            if(hidden && (ch.contains("avgPer")||ch.contains("maxPer"))) interiorPeriod++;
                            else if(hidden && ch.equals("U")) interiorUtility++;
                            else if(!hidden) interiorNone++;
                            if(hidden && (ch.contains("avgPer")||ch.contains("maxPer")))
                                throw new AssertionError("period hide with residual quantity at T"+tid+":"+item+"->"+newQ);
                        }else{
                            if(hidden && (ch.contains("avgPer")||ch.contains("maxPer"))) deletionPeriod++;
                            if(hidden && ch.contains("U")) deletionUtility++;
                        }
                        if(hidden && loss<bestLoss){bestLoss=(int)loss; best="T"+tid+":"+item+"->"+newQ+" loss="+loss+" "+ch;}
                        w.write(String.format(Locale.ROOT,"%d,%s,%d,%d,%d,%s,%s,%s,%s%n",
                                tid,item,q,newQ,loss,hidden,ch,hidden&&loss==8,""));
                    }
                }
            }
        }
        Files.writeString(out.resolve("direction2_summary.txt"),
                String.format(Locale.ROOT,"interior_period_hides=%d interior_U_hides=%d interior_nohide=%d deletion_period_hides=%d deletion_U_hides=%d%nbest_hide=%s%n",
                        interiorPeriod,interiorUtility,interiorNone,deletionPeriod,deletionUtility,best),
                StandardCharsets.UTF_8);
        if(interiorPeriod!=0) throw new AssertionError("period channel opened for d<q");
        System.out.println("D2 best hide="+best+" interiorPeriodHides="+interiorPeriod);
    }

    /** Direction 3: compaction AC in a pattern that does not contain the edited item. */
    private static void compaction(Path out)throws Exception{
        UtilityDatabase d=DatabaseIO.readSimple(Path.of("data/compaction_db.txt"),Path.of("data/compaction_profits.txt"));
        PeriodicityConstraints c=new PeriodicityConstraints(20,1,20,1,3);
        ItemsetKey x=ItemsetKey.parse("x");
        PatternInfo before=PatternEvaluator.evaluate(x,d,c);
        UtilityDatabase d2=d.copy(); d2.transactionByTid(10).setQuantity("y",0);
        PatternInfo fix=PatternEvaluator.evaluate(x,d2,c);
        PatternInfo comp=PatternEvaluator.evaluate(x,d2.compactedTimeline(),c);
        Files.writeString(out.resolve("direction3_compaction.txt"),
                String.format(Locale.ROOT,"BEFORE PHUI=%s avg=%.4f%nFIXED PHUI=%s avg=%.4f (X does not contain y)%nCOMPACT PHUI=%s avg=%.4f n=9%n",
                        before.periodicHighUtility(),before.periodicity().avgPeriod(),
                        fix.periodicHighUtility(),fix.periodicity().avgPeriod(),
                        comp.periodicHighUtility(),comp.periodicity().avgPeriod()),
                StandardCharsets.UTF_8);
        if(!(!before.periodicHighUtility() && !fix.periodicHighUtility() && comp.periodicHighUtility()))
            throw new AssertionError("compaction AC example failed");
        System.out.println("D3 compaction AC example holds");
    }

    /** MPH vs MU-MAP on the 10-tx example with sensitive {a,b,i}. */
    private static void algorithmOnExample(UtilityDatabase d,Path out)throws Exception{
        PeriodicityConstraints c=new PeriodicityConstraints(260,1,6,1,2);
        Map<ItemsetKey,PatternInfo> orig=new VerticalHuiMiner().mine(d,c,0);
        Set<ItemsetKey> S=Set.of(ItemsetKey.parse("a,b,i"));
        SanitizationResult map=new MuMapSanitizer().sanitize(d,c,S,orig);
        SanitizationResult smau=new SmauSanitizer().sanitize(d,c,S,orig);
        SanitizationResult mph=new MphSanitizer().sanitize(d,c,S,orig);
        try(BufferedWriter w=Files.newBufferedWriter(out.resolve("direction2_example_algorithms.csv"),StandardCharsets.UTF_8)){
            w.write("algorithm,edits,loss,first_edit,feasible,message\n");
            line(w,map,d); line(w,smau,d); line(w,mph,d);
        }
        System.out.printf(Locale.ROOT,"D2 algs MU-MAP loss=%d edits=%d | SMAU loss=%d edits=%d | MPH loss=%d edits=%d first=%s%n",
                d.totalUtility()-map.sanitizedDatabase().totalUtility(),map.edits().size(),
                d.totalUtility()-smau.sanitizedDatabase().totalUtility(),smau.edits().size(),
                d.totalUtility()-mph.sanitizedDatabase().totalUtility(),mph.edits().size(),
                mph.edits().isEmpty()?"none":("T"+mph.edits().get(0).tid()+":"+mph.edits().get(0).item()+"->"+mph.edits().get(0).newQuantity()));
    }

    private static void line(BufferedWriter w,SanitizationResult r,UtilityDatabase orig)throws Exception{
        long loss=orig.totalUtility()-r.sanitizedDatabase().totalUtility();
        String first=r.edits().isEmpty()?"":("T"+r.edits().get(0).tid()+":"+r.edits().get(0).item()+"->"+r.edits().get(0).newQuantity());
        w.write(String.format(Locale.ROOT,"%s,%d,%d,%s,%s,\"%s\"%n",r.algorithm(),r.edits().size(),loss,first,r.feasible(),r.message().replace("\"","'")));
    }
    private static void row(BufferedWriter w,String regime,int minPer,ItemsetKey k,String when,PatternInfo p,String note)throws Exception{
        PeriodicityStats s=p.periodicity();
        w.write(String.format(Locale.ROOT,"%s,%d,%s,%s,%d,%d,%d,%.4f,%s,\"%s\"%n",
                regime,minPer,k,when,p.utility(),s.minPeriod(),s.maxPeriod(),s.avgPeriod(),p.periodicHighUtility(),note.replace("\"","'")));
    }
    private static String channel(PatternInfo before,PatternInfo after,PeriodicityConstraints c){
        if(after.periodicHighUtility()) return "still-PHUI";
        List<String> why=new ArrayList<>();
        if(before.utility()>=c.minUtil() && after.utility()<c.minUtil()) why.add("U");
        if(before.periodicity().maxPeriod()<=c.maxPer() && after.periodicity().maxPeriod()>c.maxPer()) why.add("maxPer");
        if(before.periodicity().avgPeriod()<=c.maxAvg() && after.periodicity().avgPeriod()>c.maxAvg()) why.add("avgPer");
        if(why.isEmpty()) why.add("other");
        return String.join("+",why);
    }
}
