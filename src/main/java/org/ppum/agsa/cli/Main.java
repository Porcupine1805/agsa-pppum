package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;

import java.nio.file.*;
import java.util.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        if(args.length==0 || args[0].equals("--help") || args[0].equals("help")){usage();return;}
        String cmd=args[0]; Map<String,String> a=parse(Arrays.copyOfRange(args,1,args.length));
        switch(cmd){
            case "benchmark" -> benchmark(a);
            case "batch" -> BatchRunner.run(a);
            case "counterexample" -> CounterexampleStudy.run(a);
            case "mine" -> mine(a);
            case "selftest" -> SelfTest.run();
            case "env" -> EnvironmentInfo.write(Path.of(a.getOrDefault("out","results/run_environment.txt")));
            case "prepare-upstream-sensitive" -> ExternalInterop.prepareSensitive(a);
            case "evaluate-external" -> ExternalInterop.evaluateExternal(a);
            case "diagnose-ac" -> DiagnoseAc.run(a);
            case "hide-channel" -> HideChannelStudy.run(a);
            case "compaction" -> CompactionStudy.run(a);
            case "directions" -> DirectionsStudy.run(a);
            case "adversarial-suite" -> AdversarialSuite.run(a);
            default -> throw new IllegalArgumentException("Unknown command: "+cmd);
        }
    }

    private static void mine(Map<String,String>a)throws Exception{
        Path db=Path.of(a.getOrDefault("db","data/example_db.txt"));
        Path profits=Path.of(a.getOrDefault("profits","data/example_profits.txt"));
        int prefix=i(a,"prefixTx",0);
        UtilityDatabase d=DatabaseIO.readSimple(db,profits,prefix);
        PeriodicityConstraints c=new PeriodicityConstraints(l(a,"minUtil",260),i(a,"minPer",1),i(a,"maxPer",6),
                dbl(a,"minAvg",1.0),dbl(a,"maxAvg",2.0));
        int maxLen=i(a,"maxLen",0);
        Map<ItemsetKey,PatternInfo> huis=new VerticalHuiMiner().mine(d,c,maxLen);
        List<PatternInfo> ps=huis.values().stream().filter(PatternInfo::periodicHighUtility)
                .sorted(Comparator.comparing(p->p.key().toString())).toList();
        System.out.printf(Locale.ROOT,"tx=%d HUI=%d PHUI=%d totalUtility=%d%n",d.size(),huis.size(),ps.size(),d.totalUtility());
        for(PatternInfo p:ps) System.out.printf(Locale.ROOT,"%-24s util=%d sup=%d minPer=%d maxPer=%d avg=%.3f%n",
                p.key(),p.utility(),p.periodicity().support(),p.periodicity().minPeriod(),p.periodicity().maxPeriod(),p.periodicity().avgPeriod());
    }

    private static void benchmark(Map<String,String>a)throws Exception{
        ExperimentSpec spec=ExperimentSpec.from(a);
        Path out=Path.of(a.getOrDefault("out","results/benchmark.csv"));
        String stem=out.getFileName().toString().replaceFirst("\\.csv$","");
        Path artifactBase=out.resolveSibling(stem);
        ExperimentRunner.Output rr=ExperimentRunner.run(spec,artifactBase);
        ExperimentRunner.writeRunRecords(rr.records(),out,false);
        System.out.printf(Locale.ROOT,"Original HUI=%d PHUI=%d sensitive=%d%n",rr.originalHuiCount(),rr.originalPhuiCount(),rr.sensitive().size());
        for(RunRecord r:rr.records()){
            var m=r.m();
            System.out.printf(Locale.ROOT,"%-12s feasible=%s HF=%d MC=%d AC=%d DUS=%.3f loss=%d runtime=%dms peak=%.1fMiB%n",
                    m.algorithm(),m.feasible(),m.hidingFailure(),m.missingCost(),m.artificialCost(),m.databaseUtilitySimilarityPct(),
                    m.utilityLossAbs(),m.runtimeMillis(),r.algorithmPeakMiB());
        }
        System.out.println("Wrote: "+out.toAbsolutePath());
    }

    static Map<String,String> parse(String[]args){
        Map<String,String>m=new LinkedHashMap<>();
        for(int k=0;k<args.length;k++){
            String s=args[k];if(!s.startsWith("--"))throw new IllegalArgumentException("Expected --key: "+s);
            String key=s.substring(2);String val=(k+1<args.length&&!args[k+1].startsWith("--"))?args[++k]:"true";m.put(key,val);
        }
        return m;
    }
    private static int i(Map<String,String>a,String k,int v){return Integer.parseInt(a.getOrDefault(k,String.valueOf(v)));}
    private static long l(Map<String,String>a,String k,long v){return Long.parseLong(a.getOrDefault(k,String.valueOf(v)));}
    private static double dbl(Map<String,String>a,String k,double v){return Double.parseDouble(a.getOrDefault(k,String.valueOf(v)));}

    private static void usage(){System.out.println("""
AGSA-PPPUM reproducibility package (Java 17+, no external Java dependencies)

Commands:
  selftest
  adversarial-suite --out datasets/adversarial_200 --cases 200 --seed 20260930
  directions --out results/three_directions
  compaction --out results/compaction
  counterexample --out results/counterexample.csv
  hide-channel --out results/hide_channel
  diagnose-ac --db <...> --profits <...> --minUtil N --minPer N --maxPer N --minAvg X --maxAvg X --sepPct P --seed S --timeline fixed --algorithms SMAU-REF|AGSA
  env --out results/run_environment.txt
  mine --db <item_count.txt> --profits <item_utility.txt> --minUtil N --minPer N --maxPer N --minAvg X --maxAvg X
  benchmark --db <...> --profits <...> [--sensitive file | --sepPct P --seed S] --out results/run.csv
  batch --config experiments/tdp_paper.csv --out results/tdp_paper_runs.csv --continueOnError true

Benchmark options:
  --algorithms SMAU-REF|SMSE-REF|SMAU-PHUI|SMSE-PHUI|MU-MAP|MU-MIP|AGSA|MPH
  --algorithms MU-MIP|SMIU-REF|FAGSA|AGSA-NOGUARD   (implemented, not in the TDP tables)
  --topK 5
  --timeline fixed|compressed
  --prefixTx 0
  --maxLen 0
  --forceRemine true|false
  --saveArtifacts true|false

The DSI-Lab1/PPUM files pppum_database/.../item_count/*.txt and item_utility/*.txt
are compatible with --db and --profits. Liquor_11 lives at datasets/liquor_11/.
""");}
}
