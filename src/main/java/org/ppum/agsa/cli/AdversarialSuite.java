package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.PatternEvaluator;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Generates small targeted databases and checks miner/sanitizer properties exhaustively. */
final class AdversarialSuite {
    private AdversarialSuite() {}
    private record CaseData(String category,String purpose,UtilityDatabase db,PeriodicityConstraints c) {}
    private record Failure(int id,String category,String component,String detail) {}

    static void run(Map<String,String> args)throws Exception{
        int cases=Integer.parseInt(args.getOrDefault("cases","200"));
        long seed=Long.parseLong(args.getOrDefault("seed","20260930"));
        Path out=Path.of(args.getOrDefault("out","datasets/adversarial_200"));
        if(cases<=0)throw new IllegalArgumentException("cases must be positive");
        Files.createDirectories(out);
        ArrayList<Failure> failures=new ArrayList<>();
        int minerChecks=0,periodChecks=0,channelChecks=0,sanitizerChecks=0,casesWithPhui=0;
        try(BufferedWriter manifest=Files.newBufferedWriter(out.resolve("manifest.csv"),StandardCharsets.UTF_8)){
            manifest.write("case_id,category,purpose,transactions,items,min_util,min_per,max_per,min_avg,max_avg,phuis,sensitive_count\n");
            for(int id=1;id<=cases;id++){
                CaseData data=makeCase(id,new Random(seed+104729L*id));
                Path dir=out.resolve(String.format(Locale.ROOT,"case_%03d",id));
                writeCase(dir,data);
                try{
                    UtilityDatabase loaded=DatabaseIO.readSimple(dir.resolve("item_count.txt"),dir.resolve("item_utility.txt"));
                    if(!sameDatabase(data.db(),loaded))
                        failures.add(new Failure(id,data.category(),"io-roundtrip","written database differs after reload"));
                    data=new CaseData(data.category(),data.purpose(),loaded,data.c());
                }catch(Throwable e){failures.add(new Failure(id,data.category(),"io-read",message(e)));continue;}
                periodChecks+=verifyPeriodUpdates(id,data,failures);
                channelChecks+=verifyChannelTheorem(id,data,failures);
                Map<ItemsetKey,PatternInfo> mined;
                try{
                    mined=new VerticalHuiMiner().mine(data.db(),data.c());
                    minerChecks+=verifyMiner(id,data,mined,failures);
                }catch(Throwable e){failures.add(new Failure(id,data.category(),"miner-exception",message(e)));continue;}
                List<ItemsetKey> phuis=mined.values().stream().filter(PatternInfo::periodicHighUtility)
                        .map(PatternInfo::key).sorted().toList();
                LinkedHashSet<ItemsetKey> sensitive=new LinkedHashSet<>();
                if(!phuis.isEmpty()){
                    casesWithPhui++;
                    sensitive.add(phuis.get(0));
                    if(phuis.size()>2&&id%4==0)sensitive.add(phuis.get(phuis.size()-1));
                    writeSensitive(dir.resolve("sensitive.txt"),sensitive);
                    for(Sanitizer sanitizer:algorithms()){
                        sanitizerChecks++;
                        verifySanitizer(id,data,mined,sensitive,sanitizer,failures);
                    }
                }else Files.writeString(dir.resolve("sensitive.txt"),"",StandardCharsets.UTF_8);
                manifest.write(csv(id)+","+csv(data.category())+","+csv(data.purpose())+","+
                        data.db().size()+","+data.db().profits().size()+","+data.c().minUtil()+","+
                        data.c().minPer()+","+data.c().maxPer()+","+fmt(data.c().minAvg())+","+
                        fmt(data.c().maxAvg())+","+phuis.size()+","+sensitive.size()+"\n");
            }
        }
        writeFailures(out.resolve("failures.csv"),failures);
        String summary="cases="+cases+" miner_checks="+minerChecks+" period_checks="+periodChecks+" channel_checks="+channelChecks+" cases_with_phui="+casesWithPhui+
                " sanitizer_runs="+sanitizerChecks+" failures="+failures.size();
        Files.writeString(out.resolve("summary.txt"),summary+"\n",StandardCharsets.UTF_8);
        System.out.println("ADVERSARIAL SUITE: "+summary);
        if(!failures.isEmpty()){
            for(Failure f:failures.stream().limit(20).toList())
                System.out.println("FAIL case="+f.id()+" "+f.component()+": "+f.detail());
            throw new AssertionError("Adversarial suite found "+failures.size()+" failures; see "+out.resolve("failures.csv"));
        }
    }

    private static List<Sanitizer> algorithms(){return List.of(
            new SmauSanitizer(),new SmauSanitizer(true),
            new SmseSanitizer(),new SmseSanitizer(true),new SmiuSanitizer(),
            new MuMapSanitizer(),new MuMipSanitizer(),new AgsaSanitizer(),
            new FastAgsaSanitizer(5),new MphSanitizer());}

    private static int verifyMiner(int id,CaseData data,Map<ItemsetKey,PatternInfo> mined,List<Failure> failures){
        List<String> items=new ArrayList<>(data.db().profits().keySet());
        int checks=0;
        for(int mask=1;mask<(1<<items.size());mask++){
            ArrayList<String> subset=new ArrayList<>();
            for(int b=0;b<items.size();b++)if((mask&(1<<b))!=0)subset.add(items.get(b));
            ItemsetKey key=new ItemsetKey(subset);
            PatternInfo exact=PatternEvaluator.evaluate(key,data.db(),data.c());
            PatternInfo actual=mined.get(key);checks++;
            if((actual!=null)!=exact.highUtility())
                failures.add(new Failure(id,data.category(),"miner-membership",key+" expectedHUI="+exact.highUtility()+" actual="+(actual!=null)));
            else if(actual!=null&&(actual.utility()!=exact.utility()||
                    actual.periodicHighUtility()!=exact.periodicHighUtility()||
                    !actual.periodicity().equals(exact.periodicity())))
                failures.add(new Failure(id,data.category(),"miner-value",key+" expected="+exact+" actual="+actual));
        }
        return checks;
    }

    private static int verifyPeriodUpdates(int id,CaseData data,List<Failure> failures){
        int n=data.db().size(),checks=0;
        for(int mask=1;mask<(1<<n);mask++){
            ArrayList<Integer> tids=new ArrayList<>();
            for(int bit=0;bit<n;bit++)if((mask&(1<<bit))!=0)tids.add(bit+1);
            PeriodicityStats before=PeriodicityStats.fromTids(tids,n);
            for(int removed:tids){
                ArrayList<Integer> remaining=new ArrayList<>(tids);remaining.remove(Integer.valueOf(removed));
                PeriodicityStats expected=PeriodicityStats.fromTids(remaining,n);
                PeriodicityStats actual=PeriodicityStats.afterRemovingSorted(tids,removed,n);checks++;
                if(!expected.equals(actual))failures.add(new Failure(id,data.category(),"period-update",
                        "tids="+tids+" remove="+removed+" expected="+expected+" actual="+actual));
                if(actual.minPeriod()<before.minPeriod()||actual.maxPeriod()<before.maxPeriod()||
                        actual.avgPeriod()+1e-12<before.avgPeriod())
                    failures.add(new Failure(id,data.category(),"period-monotonicity","tids="+tids+" remove="+removed));
            }
        }
        return checks;
    }

    private static int verifyChannelTheorem(int id,CaseData data,List<Failure> failures){
        int n=data.db().size(),checks=0;
        for(int mask=1;mask<(1<<n);mask++){
            ArrayList<Integer> tids=new ArrayList<>();
            for(int bit=0;bit<n;bit++)if((mask&(1<<bit))!=0)tids.add(bit+1);
            if(tids.size()<2)continue;
            PeriodicityStats before=PeriodicityStats.fromTids(tids,n);
            int k=tids.size();
            for(int removed:tids){
                ArrayList<Integer> remaining=new ArrayList<>(tids);remaining.remove(Integer.valueOf(removed));
                PeriodicityStats after=PeriodicityStats.fromTids(remaining,n);
                for(int alpha=1;alpha<=n+1;alpha++){
                    boolean observed=before.minPeriod()<alpha&&after.minPeriod()>=alpha;
                    boolean formula=before.minPeriod()<alpha&&alpha<=after.minPeriod();checks++;
                    if(observed!=formula)failures.add(new Failure(id,data.category(),"lower-min-theorem","alpha="+alpha+" tids="+tids));
                }
                for(int twiceBeta=0;twiceBeta<=2*n+2;twiceBeta++){
                    double beta=twiceBeta/2.0;
                    boolean observed=before.avgPeriod()<beta&&after.avgPeriod()>=beta;
                    boolean formula=n/(double)(k+1)<beta&&beta<=n/(double)k;checks++;
                    if(observed!=formula)failures.add(new Failure(id,data.category(),"lower-avg-theorem","beta="+beta+" tids="+tids));
                }
                for(int upper=1;upper<=n;upper++){
                    if(before.maxPeriod()>upper&&after.maxPeriod()<=upper)
                        failures.add(new Failure(id,data.category(),"upper-max-entry","upper="+upper+" tids="+tids));
                    checks++;
                }
            }
        }
        return checks;
    }

    private static void verifySanitizer(int id,CaseData data,Map<ItemsetKey,PatternInfo> original,
            Set<ItemsetKey> sensitive,Sanitizer sanitizer,List<Failure> failures){
        SanitizationResult result;
        try{result=sanitizer.sanitize(data.db(),data.c(),sensitive,original);}
        catch(Throwable e){failures.add(new Failure(id,data.category(),sanitizer.getClass().getSimpleName()+"-exception",message(e)));return;}
        UtilityDatabase afterDb=result.sanitizedDatabase();
        if(afterDb.size()!=data.db().size())
            failures.add(new Failure(id,data.category(),result.algorithm()+"-timeline","database size changed"));
        for(int tid=1;tid<=Math.min(data.db().size(),afterDb.size());tid++){
            Transaction before=data.db().transactionByTid(tid),after=afterDb.transactionByTid(tid);
            for(String item:after.activeItems()){
                if(!before.contains(item)||after.quantity(item)>before.quantity(item))
                    failures.add(new Failure(id,data.category(),result.algorithm()+"-edit","quantity/item creation at T"+tid+" item="+item));
            }
        }
        Map<ItemsetKey,PatternInfo> remine;
        try{remine=new VerticalHuiMiner().mine(afterDb,data.c());}
        catch(Throwable e){failures.add(new Failure(id,data.category(),result.algorithm()+"-remine",message(e)));return;}
        if(result.feasible())for(ItemsetKey key:sensitive){
            PatternInfo p=remine.get(key);
            if(p!=null&&p.periodicHighUtility())
                failures.add(new Failure(id,data.category(),result.algorithm()+"-HF","sensitive remains PHUI: "+key));
        }
        for(ItemsetKey key:remine.keySet())if(!original.containsKey(key))
            failures.add(new Failure(id,data.category(),result.algorithm()+"-new-HUI","new HUI under decrease-only edits: "+key));
        if((result.algorithm().equals("AGSA")||result.algorithm().startsWith("FAGSA"))&&result.feasible()){
            Set<ItemsetKey> beforeP=phuiKeys(original),afterP=phuiKeys(remine);afterP.removeAll(beforeP);
            if(!afterP.isEmpty())failures.add(new Failure(id,data.category(),"AGSA-AC","new PHUIs="+afterP));
        }
        if(result.finalWitnessStates()!=null)for(var entry:result.finalWitnessStates().entrySet()){
            PatternInfo exact=PatternEvaluator.evaluate(entry.getKey(),afterDb,data.c()),tracked=entry.getValue();
            if(exact.utility()!=tracked.utility()||exact.highUtility()!=tracked.highUtility()||
                    exact.periodicHighUtility()!=tracked.periodicHighUtility()||
                    !exact.periodicity().equals(tracked.periodicity()))
                failures.add(new Failure(id,data.category(),result.algorithm()+"-tracker","mismatch for "+entry.getKey()));
        }
    }

    private static Set<ItemsetKey> phuiKeys(Map<ItemsetKey,PatternInfo> map){
        LinkedHashSet<ItemsetKey> out=new LinkedHashSet<>();
        for(PatternInfo p:map.values())if(p.periodicHighUtility())out.add(p.key());return out;
    }

    private static CaseData makeCase(int id,Random rnd){
        int family=(id-1)%10,variant=(id-1)/10;
        List<String> names=List.of("A","B","C","D","E","F");
        int itemCount=3+rnd.nextInt(4),n=4+rnd.nextInt(7);
        LinkedHashMap<String,Long> profits=new LinkedHashMap<>();
        for(int i=0;i<itemCount;i++)profits.put(names.get(i),1L+rnd.nextInt(9));
        ArrayList<Transaction> txs=new ArrayList<>();
        String category,purpose;
        switch(family){
            case 0 -> {category="singleton";purpose="singleton minPer=+infinity and boundary maxPer";
                n=6+variant%5;for(int t=1;t<=n;t++)txs.add(tx(t,t==2+variant%n?Map.of("A",2):Map.of()));}
            case 1 -> {category="avg-boundary";purpose="full support and k-to-k-1 lower-average crossing";
                n=5+variant%6;for(int t=1;t<=n;t++)txs.add(tx(t,Map.of("A",1,"B",t%2+1)));}
            case 2 -> {category="min-gap-tie";purpose="multiple equal minimum internal gaps";
                n=10;Set<Integer> occ=Set.of(2,4,6,9);for(int t=1;t<=n;t++)txs.add(tx(t,occ.contains(t)?Map.of("A",2,"C",1):Map.of("B",1)));}
            case 3 -> {category="max-gap-boundary";purpose="leading/trailing and merged maximum gaps";
                n=9+variant%3;Set<Integer> occ=Set.of(2,5,8);for(int t=1;t<=n;t++)txs.add(tx(t,occ.contains(t)?Map.of("A",3):Map.of("B",1)));}
            case 4 -> {category="utility-threshold";purpose="utilities exactly below, at, and above minUtil";
                n=6;for(int t=1;t<=n;t++)txs.add(tx(t,Map.of("A",1+(t%2),"B",1,"C",t%3+1)));}
            case 5 -> {category="join-completeness";purpose="low-bound join operands required by a high-utility superset";
                n=4;profits.put("A",1L);profits.put("B",50L);profits.put("C",1L);for(int t=1;t<=n;t++)txs.add(tx(t,t%2==0?Map.of("A",1,"B",2,"C",1):Map.of("B",1)));}
            case 6 -> {category="dense-overlap";purpose="overlapping sensitive patterns in dense transactions";
                n=6+variant%4;for(int t=1;t<=n;t++){LinkedHashMap<String,Integer> q=new LinkedHashMap<>();for(String x:profits.keySet())if(rnd.nextDouble()<.85)q.put(x,1+rnd.nextInt(3));txs.add(tx(t,q));}}
            case 7 -> {category="empty-sparse";purpose="empty transactions, long gaps, and sparse occurrences";
                n=8+variant%3;for(int t=1;t<=n;t++)txs.add(tx(t,(t==1||t==n||t==n/2)?Map.of("A",1+rnd.nextInt(3)):Map.of()));}
            case 8 -> {category="duplicate-normalization";purpose="repeated logical item quantities merged before mining";
                n=5+variant%4;for(int t=1;t<=n;t++)txs.add(new Transaction(t,new String[]{"A","A","B"},new int[]{1,1+t%2,1},3));}
            default -> {category="random-mixed";purpose="deterministic randomized mixed boundary regression";
                for(int t=1;t<=n;t++){LinkedHashMap<String,Integer> q=new LinkedHashMap<>();for(String x:profits.keySet())if(rnd.nextDouble()<.48)q.put(x,1+rnd.nextInt(4));txs.add(tx(t,q));}}
        }
        UtilityDatabase db=new UtilityDatabase(txs,profits);
        long base=Math.max(1,db.totalUtility()/(4+(variant%4)));
        long minUtil=family==4?Math.max(1,utilityOf(ItemsetKey.parse("A"),db)+(variant%3)-1):base;
        int minPer=(family==0?1:1+variant%3),maxPer=Math.max(minPer,n-(variant%3));
        double minAvg=family==1?(variant%2==0?1.0:n/(double)(n+1)):0.5+variant%3;
        double maxAvg=Math.max(minAvg,n/2.0+2);
        return new CaseData(category,purpose,db,new PeriodicityConstraints(minUtil,minPer,maxPer,minAvg,maxAvg));
    }

    private static Transaction tx(int tid,Map<String,Integer> q){return new Transaction(tid,q);}
    private static boolean sameDatabase(UtilityDatabase a,UtilityDatabase b){
        if(a.size()!=b.size()||!a.profits().equals(b.profits())||a.totalUtility()!=b.totalUtility())return false;
        for(int tid=1;tid<=a.size();tid++){
            Transaction x=a.transactionByTid(tid),y=b.transactionByTid(tid);
            if(!x.quantities().equals(y.quantities()))return false;
        }
        return true;
    }
    private static long utilityOf(ItemsetKey key,UtilityDatabase db){
        return PatternEvaluator.evaluate(key,db,new PeriodicityConstraints(1,1,Integer.MAX_VALUE,0,Double.MAX_VALUE/2)).utility();
    }
    private static void writeCase(Path dir,CaseData data)throws IOException{
        Files.createDirectories(dir);DatabaseIO.writeSimple(data.db(),dir.resolve("item_count.txt"));
        try(BufferedWriter w=Files.newBufferedWriter(dir.resolve("item_utility.txt"),StandardCharsets.UTF_8)){
            for(var e:data.db().profits().entrySet())w.write(e.getKey()+" "+e.getValue()+"\n");
        }
        String metadata="category="+data.category()+"\npurpose="+data.purpose()+"\nminUtil="+data.c().minUtil()+
                "\nminPer="+data.c().minPer()+"\nmaxPer="+data.c().maxPer()+"\nminAvg="+fmt(data.c().minAvg())+
                "\nmaxAvg="+fmt(data.c().maxAvg())+"\n";
        Files.writeString(dir.resolve("case.properties"),metadata,StandardCharsets.UTF_8);
    }
    private static void writeSensitive(Path path,Set<ItemsetKey> sensitive)throws IOException{
        try(BufferedWriter w=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){for(ItemsetKey k:sensitive)w.write(String.join(",",k.items())+"\n");}
    }
    private static void writeFailures(Path path,List<Failure> failures)throws IOException{
        try(BufferedWriter w=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){
            w.write("case_id,category,component,detail\n");for(Failure f:failures)
                w.write(f.id()+","+csv(f.category())+","+csv(f.component())+","+csv(f.detail())+"\n");
        }
    }
    private static String message(Throwable e){return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}
    private static String fmt(double d){return String.format(Locale.ROOT,"%.12g",d);}
    private static String csv(Object value){String s=String.valueOf(value);return "\""+s.replace("\"","\"\"")+"\"";}
}
