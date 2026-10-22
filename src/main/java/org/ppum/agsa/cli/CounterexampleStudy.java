package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.*;
import org.ppum.agsa.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reproducible constructive counterexamples on the source paper's running example. */
final class CounterexampleStudy {
    private CounterexampleStudy() {}

    static void run(Map<String,String> args)throws Exception{
        Path out=Path.of(args.getOrDefault("out","results/counterexample.csv"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        UtilityDatabase d=DatabaseIO.readSimple(Path.of(args.getOrDefault("db","data/example_db.txt")),
                Path.of(args.getOrDefault("profits","data/example_profits.txt")));
        PeriodicityConstraints c=new PeriodicityConstraints(260,1,6,1,2);
        VerticalHuiMiner miner=new VerticalHuiMiner();
        Map<ItemsetKey,PatternInfo> orig=miner.mine(d,c,0);
        ItemsetKey abi=ItemsetKey.parse("a,b,i"), ai=ItemsetKey.parse("a,i"), agi=ItemsetKey.parse("a,g,i"), ab=ItemsetKey.parse("a,b");
        assertEq(orig.get(abi).utility(),405,"U(abi)"); assertEq(orig.get(ai).utility(),440,"U(ai)");
        assertEq(orig.get(agi).utility(),384,"U(agi)"); assertEq(orig.get(ab).utility(),369,"U(ab)");

        Path selectionOut=out.resolveSibling(out.getFileName().toString().replace(".csv","")+"_selection.csv");
        try(BufferedWriter sw=Files.newBufferedWriter(selectionOut,StandardCharsets.UTF_8)){
            sw.write("kind,id,value,detail\n");
            for(int tid:orig.get(abi).tids()) {
                long u=d.transactionByTid(tid).itemsetUtility(abi,d.profits());
                sw.write(String.format(Locale.ROOT,"transaction,T%d,%d,utility of sensitive {a,b,i} in T%d%n",tid,u,tid));
            }
            for(String item:List.of("a","b","i")) {
                PatternInfo pi=PatternEvaluator.evaluate(new ItemsetKey(List.of(item)),d,c);
                sw.write(String.format(Locale.ROOT,"victim_item,%s,%d,singleton maxPer%n",item,pi.periodicity().maxPeriod()));
            }
        }

        PatternInfo agiAfter, abAfter;
        UtilityDatabase sourceSecond, alt;
        try(BufferedWriter w=Files.newBufferedWriter(out,StandardCharsets.UTF_8)){
            w.write("case,rule,target,tid,item,before_pattern,before_utility,after_utility,before_phui,after_phui,db_utility_loss,DUS_pct,interpretation\n");

            // MU-MAP: T8 is selected for {a,b,i}; max singleton maxPer selects i.
            UtilityDatabase map1=d.copy(); map1.transactionByTid(8).setQuantity("i",0);
            Map<ItemsetKey,PatternInfo> map1p=new VerticalHuiMiner().mine(map1,c,0);
            agiAfter=eval(agi,map1,map1p,c);
            row(w,"MU-MAP-MC","max sensitive utility T, then max maxPer item",abi,8,"i",agi,orig.get(agi),agiAfter,
                    d.totalUtility()-map1.totalUtility(),map1.totalUtility()*100.0/d.totalUtility(),
                    "Deleting i at T8 hides sensitive abi but also removes non-sensitive PHUI agi by raising avgPeriod above maxAvg.");

            // MU-MIP: same T8, but min singleton maxPer selects b; {a,b} drops below minUtil.
            UtilityDatabase mip1=d.copy(); mip1.transactionByTid(8).setQuantity("b",0);
            Map<ItemsetKey,PatternInfo> mip1p=new VerticalHuiMiner().mine(mip1,c,0);
            abAfter=eval(ab,mip1,mip1p,c);
            row(w,"MU-MIP-MC","max sensitive utility T, then min maxPer item",abi,8,"b",ab,orig.get(ab),abAfter,
                    d.totalUtility()-mip1.totalUtility(),mip1.totalUtility()*100.0/d.totalUtility(),
                    "Deleting b at T8 reduces U(ab) from 369 to 250 < minUtil=260, so a non-sensitive PHUI is lost immediately.");

            // MU-MAP second-step utility-preservation counterexample, conditioned on its first T8:i deletion.
            PatternInfo aiAfterFirst=eval(ai,map1,map1p,c); assertEq(aiAfterFirst.utility(),364,"U(ai) after T8:i");
            sourceSecond=map1.copy(); sourceSecond.transactionByTid(5).setQuantity("i",0);
            Map<ItemsetKey,PatternInfo> sourceP=new VerticalHuiMiner().mine(sourceSecond,c,0);
            PatternInfo aiSource=eval(ai,sourceSecond,sourceP,c);
            row(w,"MU-MAP-UTILITY-SOURCE","second max sensitive-utility transaction",ai,5,"i",ai,aiAfterFirst,aiSource,
                    d.totalUtility()-sourceSecond.totalUtility(),sourceSecond.totalUtility()*100.0/d.totalUtility(),
                    "Source-rule path: T5 contributes 154 to ai, so deleting i costs 90 database utility units.");

            alt=map1.copy(); alt.transactionByTid(10).setQuantity("a",0);
            Map<ItemsetKey,PatternInfo> altP=new VerticalHuiMiner().mine(alt,c,0);
            PatternInfo aiAlt=eval(ai,alt,altP,c);
            row(w,"MU-MAP-UTILITY-ALTERNATIVE","feasible local alternative after identical first edit",ai,10,"a",ai,aiAfterFirst,aiAlt,
                    d.totalUtility()-alt.totalUtility(),alt.totalUtility()*100.0/d.totalUtility(),
                    "Deleting a at T10 costs only 8 database utility units and still makes ai non-PHUI (avgPeriod=2.5>2). ");
        }

        // Machine-checkable assertions forming the constructive argument.
        check(!agiAfter.periodicHighUtility(),"MU-MAP first edit must make {a,g,i} missing");
        assertEq(abAfter.utility(),250,"U(ab) after T8:b"); check(!abAfter.periodicHighUtility(),"MU-MIP first edit must make {a,b} missing");
        check(!eval(ai,sourceSecond,new VerticalHuiMiner().mine(sourceSecond,c,0),c).periodicHighUtility(),"source second edit hides ai");
        PatternInfo altAi=eval(ai,alt,new VerticalHuiMiner().mine(alt,c,0),c); check(!altAi.periodicHighUtility(),"alternative hides ai");
        assertEq(d.totalUtility()-sourceSecond.totalUtility(),110,"MU-MAP source-rule total utility loss");
        assertEq(d.totalUtility()-alt.totalUtility(),28,"alternative total utility loss");
        System.out.println("COUNTEREXAMPLE PASS: MU-MAP/MU-MIP collateral-damage claims verified; wrote "+out.toAbsolutePath()+" and "+selectionOut.toAbsolutePath());
    }

    private static PatternInfo eval(ItemsetKey k,UtilityDatabase db,Map<ItemsetKey,PatternInfo> mined,PeriodicityConstraints c){
        PatternInfo p=mined.get(k);return p!=null?p:PatternEvaluator.evaluate(k,db,c);
    }
    private static void row(BufferedWriter w,String id,String rule,ItemsetKey target,int tid,String item,ItemsetKey affected,
                            PatternInfo before,PatternInfo after,long loss,double dus,String interp)throws IOException{
        w.write(String.format(Locale.ROOT,"%s,\"%s\",\"%s\",%d,%s,\"%s\",%d,%d,%s,%s,%d,%.6f,\"%s\"%n",
                id,rule,target,tid,item,affected,before.utility(),after.utility(),before.periodicHighUtility(),after.periodicHighUtility(),loss,dus,interp.replace("\"","'")));
    }
    private static void assertEq(long a,long b,String msg){if(a!=b)throw new AssertionError(msg+": expected "+b+" got "+a);}
    private static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
}
