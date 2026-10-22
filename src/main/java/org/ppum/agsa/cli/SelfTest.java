package org.ppum.agsa.cli;

import org.ppum.agsa.io.*;
import org.ppum.agsa.mining.*;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.*;
import java.nio.file.*;
import java.util.*;

final class SelfTest {
    static void run() throws Exception {
        TrackerVerifier.run();
        verifyPeriodSemantics();
        verifyConstraintValidation();
        verifyMinerJoinCompleteness();
        verifyMinerAgainstBruteForce();
        UtilityDatabase db=DatabaseIO.readSimple(Path.of("data/example_db.txt"),Path.of("data/example_profits.txt"));
        PeriodicityConstraints c=new PeriodicityConstraints(260,1,6,1,2);
        Map<ItemsetKey,PatternInfo> huis=new VerticalHuiMiner().mine(db,c);
        check(db.totalUtility()==1285,"paper example total utility");
        PatternInfo ai=huis.get(ItemsetKey.parse("a,i"));check(ai!=null&&ai.utility()==440,"U({a,i})=440");
        check(ai.periodicHighUtility(),"{a,i} is PHUI");
        check(huis.values().stream().filter(PatternInfo::periodicHighUtility).count()==8,"paper example has 8 PHUIs");
        Set<ItemsetKey>s=DatabaseIO.readSensitive(Path.of("data/example_sensitive.txt"));
        SanitizationResult r=new AgsaSanitizer().sanitize(db,c,s,huis);
        Map<ItemsetKey,PatternInfo> after=new VerticalHuiMiner().mine(r.sanitizedDatabase(),c);
        for(ItemsetKey x:s)check(!after.containsKey(x)||!after.get(x).periodicHighUtility(),"sensitive hidden: "+x);
        // The optimized fixed-timeline evaluator uses the final witness tracker instead of
        // re-mining the sanitized database. Verify exact equivalence on the source example.
        Map<ItemsetKey,PatternInfo> tracked=r.finalWitnessStates();
        check(tracked!=null,"final witness snapshot available");
        for(var e:tracked.entrySet()){
            PatternInfo q=after.get(e.getKey());
            PatternInfo t=e.getValue();
            boolean qH=q!=null&&q.highUtility(), qP=q!=null&&q.periodicHighUtility();
            check(t.highUtility()==qH,"tracker HUI equivalence: "+e.getKey());
            check(t.periodicHighUtility()==qP,"tracker PHUI equivalence: "+e.getKey());
            if(q!=null)check(t.utility()==q.utility(),"tracker utility equivalence: "+e.getKey());
        }

        Set<ItemsetKey> p0=new HashSet<>(),p1=new HashSet<>();
        for(PatternInfo p:huis.values())if(p.periodicHighUtility())p0.add(p.key());
        for(PatternInfo p:after.values())if(p.periodicHighUtility())p1.add(p.key());
        Set<ItemsetKey>ac=new HashSet<>(p1);ac.removeAll(p0);check(ac.isEmpty(),"AGSA hard guard produced zero AC");

        // Monotonicity sanity check for all original HUIs: utilities cannot increase after reductions.
        for(PatternInfo p:huis.values()){
            PatternInfo q=PatternEvaluator.evaluate(p.key(),r.sanitizedDatabase(),c);
            check(q.utility()<=p.utility(),"utility monotonicity: "+p.key());
        }
        System.out.println("SELFTEST PASS: paper example reproduced (8 PHUIs); AGSA hides all supplied sensitive PHUIs with AC=0.");
    }
    private static void verifyPeriodSemantics(){
        int inf=Integer.MAX_VALUE;
        PeriodicityStats singleton=PeriodicityStats.fromTids(List.of(2),10);
        check(singleton.minPeriod()==inf,"PHM singleton minPer is +infinity");
        check(singleton.maxPeriod()==8,"singleton maxPer includes boundary gaps");
        check(Math.abs(singleton.avgPeriod()-5.0)<1e-12,"singleton avgPer=n/2");

        PeriodicityStats pair=PeriodicityStats.fromTids(List.of(2,8),10);
        check(pair.minPeriod()==6,"pair minPer is its internal gap");
        check(PeriodicityStats.afterRemovingSorted(List.of(2,8),8,10).minPeriod()==inf,
                "k=2 to k=1 deletion preserves minPer monotonicity");

        List<Integer> tids=List.of(2,5,8);
        PeriodicityStats before=PeriodicityStats.fromTids(tids,10);
        for(int removed:tids){
            ArrayList<Integer> afterTids=new ArrayList<>(tids);afterTids.remove(Integer.valueOf(removed));
            PeriodicityStats after=PeriodicityStats.fromTids(afterTids,10);
            check(after.minPeriod()>=before.minPeriod(),"minPer non-decreasing after deletion at T"+removed);
            check(after.maxPeriod()>=before.maxPeriod(),"maxPer non-decreasing after deletion at T"+removed);
            check(after.avgPeriod()>before.avgPeriod(),"avgPer increases after deletion at T"+removed);
        }

        ArrayList<Integer> full=new ArrayList<>();for(int t=1;t<=10;t++)full.add(t);
        PeriodicityStats fullBefore=PeriodicityStats.fromTids(full,10);
        full.remove(Integer.valueOf(5));
        PeriodicityStats fullAfter=PeriodicityStats.fromTids(full,10);
        check(fullBefore.avgPeriod()<1.0&&Math.abs(fullAfter.avgPeriod()-1.0)<1e-12,
                "lower-average channel opens at beta_min=1 for full support");

        Map<String,Integer> q=Map.of("x",1);Map<String,Long> p=Map.of("x",1L);
        boolean rejected=false;
        try{new UtilityDatabase(List.of(new Transaction(1,q),new Transaction(1,q)),p);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"duplicate/non-contiguous TIDs rejected at database boundary");

        Transaction duplicateItems=new Transaction(1,new String[]{"x","x"},new int[]{2,3},2);
        check(duplicateItems.activeItemCount()==1&&duplicateItems.quantity("x")==5,
                "duplicate item tokens are normalized to one transaction cell");

        UtilityDatabase withEmpty=new UtilityDatabase(List.of(
                new Transaction(1,q),new Transaction(2,Map.of()),new Transaction(3,q)),p);
        UtilityDatabase compact=withEmpty.compactedTimeline();
        check(compact.size()==2&&compact.transactionByTid(2).contains("x"),
                "compressed timeline drops empty transaction and reindexes TIDs");
    }
    private static void verifyConstraintValidation(){
        expectIllegal(() -> new PeriodicityConstraints(0,1,2,0,2),"non-positive minUtil rejected");
        expectIllegal(() -> new PeriodicityConstraints(1,0,2,0,2),"minPer below one rejected");
        expectIllegal(() -> new PeriodicityConstraints(1,3,2,0,2),"inverted integer period bounds rejected");
        expectIllegal(() -> new PeriodicityConstraints(1,1,2,3,2),"inverted average period bounds rejected");
        expectIllegal(() -> new PeriodicityConstraints(1,1,2,Double.NaN,2),"NaN average bound rejected");
    }
    private static void expectIllegal(Runnable action,String msg){
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,msg);
    }
    private static void verifyMinerJoinCompleteness(){
        UtilityDatabase db=new UtilityDatabase(List.of(new Transaction(1,Map.of("A",1,"B",100,"C",1))),
                Map.of("A",1L,"B",1L,"C",1L));
        PeriodicityConstraints c=new PeriodicityConstraints(100,1,1,0,1);
        Map<ItemsetKey,PatternInfo> huis=new VerticalHuiMiner().mine(db,c);
        PatternInfo abc=huis.get(ItemsetKey.parse("A,B,C"));
        check(abc!=null&&abc.utility()==102,
                "low-bound right join operand retained for high-utility superset");
    }
    private static void verifyMinerAgainstBruteForce(){
        Random rnd=new Random(20260929L);
        List<String> items=List.of("A","B","C","D","E","F");
        for(int trial=0;trial<40;trial++){
            LinkedHashMap<String,Long> profits=new LinkedHashMap<>();
            for(String item:items)profits.put(item,1L+rnd.nextInt(5));
            ArrayList<Transaction> txs=new ArrayList<>();
            int n=5+rnd.nextInt(5);
            for(int tid=1;tid<=n;tid++){
                LinkedHashMap<String,Integer> q=new LinkedHashMap<>();
                for(String item:items)if(rnd.nextDouble()<0.45)q.put(item,1+rnd.nextInt(3));
                txs.add(new Transaction(tid,q));
            }
            UtilityDatabase db=new UtilityDatabase(txs,profits);
            PeriodicityConstraints c=new PeriodicityConstraints(5+rnd.nextInt(20),1,n,0,n);
            Map<ItemsetKey,PatternInfo> mined=new VerticalHuiMiner().mine(db,c);
            for(int mask=1;mask<(1<<items.size());mask++){
                ArrayList<String> subset=new ArrayList<>();
                for(int bit=0;bit<items.size();bit++)if((mask&(1<<bit))!=0)subset.add(items.get(bit));
                ItemsetKey key=new ItemsetKey(subset);
                PatternInfo exact=PatternEvaluator.evaluate(key,db,c);
                check(mined.containsKey(key)==exact.highUtility(),
                        "vertical miner/brute-force HUI equivalence trial="+trial+" key="+key);
                if(exact.highUtility())check(mined.get(key).utility()==exact.utility(),
                        "vertical miner/brute-force utility equivalence trial="+trial+" key="+key);
            }
        }
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError("FAIL: "+msg);}
}
