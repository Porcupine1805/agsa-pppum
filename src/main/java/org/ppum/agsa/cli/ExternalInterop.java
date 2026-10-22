package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.metrics.Evaluator;
import org.ppum.agsa.metrics.Metrics;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.SanitizationDiagnostics;
import org.ppum.agsa.sanitize.SanitizationResult;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Interoperability tools for running frozen external/upstream sanitizers while
 * evaluating every sanitized database with the same AGSA metrics implementation.
 * No upstream source code is redistributed by this project.
 */
final class ExternalInterop {
    private ExternalInterop() {}

    static void prepareSensitive(Map<String,String> a) throws Exception {
        Path dbPath=Path.of(req(a,"db")), profitPath=Path.of(req(a,"profits"));
        int prefix=intv(a,"prefixTx",0), maxLen=intv(a,"maxLen",0);
        PeriodicityConstraints c=constraints(a);
        UtilityDatabase db=DatabaseIO.readSimple(dbPath,profitPath,prefix);
        Map<ItemsetKey,PatternInfo> huis=new VerticalHuiMiner().mine(db,c,maxLen);
        Set<ItemsetKey> sensitive;
        String sens=a.getOrDefault("sensitive","").trim();
        if(!sens.isEmpty()) sensitive=DatabaseIO.readSensitive(Path.of(sens));
        else sensitive=selectSensitive(huis,doublev(a,"sepPct",0),intv(a,"sensitiveCount",3),longv(a,"seed",42));
        validateSensitive(sensitive,huis);
        Path simple=Path.of(a.getOrDefault("simpleOut","results/upstream_case.simple.txt"));
        Path upstream=Path.of(a.getOrDefault("upstreamOut","results/upstream_case.upstream.txt"));
        writeSensitiveFiles(sensitive,huis,simple,upstream);
        System.out.printf(Locale.ROOT,"Prepared sensitive set: PHUI=%d sensitive=%d hash=%s%n",
                huis.values().stream().filter(PatternInfo::periodicHighUtility).count(),sensitive.size(),hashSensitive(sensitive));
        System.out.println("simple="+simple.toAbsolutePath());
        System.out.println("upstream="+upstream.toAbsolutePath());
    }

    static void evaluateExternal(Map<String,String> a) throws Exception {
        Path dbPath=Path.of(req(a,"db")), profitPath=Path.of(req(a,"profits")), sanitizedPath=Path.of(req(a,"sanitized")), sensitivePath=Path.of(req(a,"sensitive"));
        int prefix=intv(a,"prefixTx",0), maxLen=intv(a,"maxLen",0), topK=intv(a,"topK",0), replicate=intv(a,"replicate",1);
        long seed=longv(a,"seed",0), runtimeMs=longv(a,"runtimeMs",0), editOps=longv(a,"editOps",-1);
        double algorithmPeak=doublev(a,"algorithmPeakMiB",-1), sepPct=doublev(a,"sepPct",0);
        String timeline=a.getOrDefault("timeline","fixed").toLowerCase(Locale.ROOT), algorithm=a.getOrDefault("algorithm","UPSTREAM");
        if(!timeline.equals("fixed")&&!timeline.equals("compressed")) throw new IllegalArgumentException("timeline must be fixed or compressed");
        PeriodicityConstraints c=constraints(a);
        UtilityDatabase original=DatabaseIO.readSimple(dbPath,profitPath,prefix);
        UtilityDatabase sanitizedStorage=DatabaseIO.readSimple(sanitizedPath,profitPath,prefix);
        if(original.size()!=sanitizedStorage.size() && timeline.equals("fixed"))
            throw new IllegalArgumentException("Fixed-timeline external evaluation requires the sanitized file to preserve transaction count: original="+original.size()+" sanitized="+sanitizedStorage.size());
        Set<ItemsetKey> sensitive=DatabaseIO.readSensitive(sensitivePath);

        long omStart=System.nanoTime(); Map<ItemsetKey,PatternInfo> originalHuis; double originalPeak;
        try(MemorySampler ms=new MemorySampler()){ originalHuis=new VerticalHuiMiner().mine(original,c,maxLen); originalPeak=ms.peakMiB(); }
        long originalMiningMs=(System.nanoTime()-omStart)/1_000_000L;
        validateSensitive(sensitive,originalHuis);

        UtilityDatabase patternDb=timeline.equals("compressed")?sanitizedStorage.compactedTimeline():sanitizedStorage;
        long pmStart=System.nanoTime(); Map<ItemsetKey,PatternInfo> sanitizedHuis;
        try(MemorySampler ignored=new MemorySampler()){ sanitizedHuis=new VerticalHuiMiner().mine(patternDb,c,maxLen); }
        long postMiningMs=(System.nanoTime()-pmStart)/1_000_000L;

        SanitizationResult dummy=new SanitizationResult(algorithm,sanitizedStorage,List.of(),true,"external frozen-upstream run",runtimeMs,
                SanitizationDiagnostics.none(originalHuis.size()),null);
        Metrics m0=Evaluator.evaluate(algorithm,original,sanitizedStorage,patternDb,sensitive,originalHuis,sanitizedHuis,dummy);
        int ops=editOps<0?-1:(editOps>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)editOps);
        Metrics m=new Metrics(m0.algorithm(),m0.originalHUIs(),m0.sanitizedHUIs(),m0.originalPHUIs(),m0.sanitizedPHUIs(),m0.sensitiveCount(),
                m0.hidingFailure(),m0.missingCost(),m0.artificialCost(),m0.hfRatePct(),m0.mcRatePct(),m0.acRatePct(),
                m0.itemsetUtilitySimilarityPct(),m0.databaseUtilitySimilarityPct(),m0.databaseStructureSimilarityPct(),m0.transactionPatternL2(),
                m0.modifiedTransactionPct(),m0.editedCells(),ops,m0.utilityLossAbs(),m0.utilityLossPct(),0,0,0,0,originalHuis.size(),runtimeMs,true);
        RunRecord rec=new RunRecord(a.getOrDefault("configId","external"),a.getOrDefault("dataset","dataset"),timeline,replicate,seed,sepPct,prefix,
                original.size(),original.nonEmptyTransactions(),original.totalUtility(),c.minUtil(),c.minPer(),c.maxPer(),c.minAvg(),c.maxAvg(),maxLen,topK,
                hashSensitive(sensitive),(int)originalHuis.values().stream().filter(PatternInfo::periodicHighUtility).count(),originalMiningMs,originalPeak,false,
                postMiningMs,runtimeMs,algorithmPeak,m,"OK","frozen upstream output evaluated by common AGSA evaluator; editOps=-1 means unavailable");
        Path out=Path.of(a.getOrDefault("out","results/external_runs.csv"));
        boolean append=Boolean.parseBoolean(a.getOrDefault("append","false"));
        ExperimentRunner.writeRunRecords(List.of(rec),out,append);
        System.out.printf(Locale.ROOT,"%s HF=%d MC=%d AC=%d DUS=%.6f DSS=%.6f -> %s%n",algorithm,m.hidingFailure(),m.missingCost(),m.artificialCost(),m.databaseUtilitySimilarityPct(),m.databaseStructureSimilarityPct(),out.toAbsolutePath());
    }

    static void writeSensitiveFiles(Set<ItemsetKey> sensitive, Map<ItemsetKey,PatternInfo> huis, Path simple, Path upstream) throws Exception {
        Path p=simple.toAbsolutePath().getParent(); if(p!=null) Files.createDirectories(p);
        p=upstream.toAbsolutePath().getParent(); if(p!=null) Files.createDirectories(p);
        try(BufferedWriter sw=Files.newBufferedWriter(simple,StandardCharsets.UTF_8); BufferedWriter uw=Files.newBufferedWriter(upstream,StandardCharsets.UTF_8)){
            for(ItemsetKey k:sensitive.stream().sorted().toList()){
                PatternInfo pi=huis.get(k); if(pi==null) throw new IllegalArgumentException("Sensitive pattern absent from original HUI universe: "+k);
                sw.write(String.join(",",k.items())); sw.newLine();
                PeriodicityStats ps=pi.periodicity();
                uw.write(String.join(" ",k.items())); uw.write(':'); uw.write(Long.toString(pi.utility())); uw.write(':');
                uw.write(Integer.toString(ps.support())); uw.write(':'); uw.write(Integer.toString(ps.minPeriod())); uw.write(':');
                uw.write(Integer.toString(ps.maxPeriod())); uw.write(':'); uw.write(String.format(Locale.ROOT,"%.12f",ps.avgPeriod())); uw.newLine();
            }
        }
    }

    static String hashSensitive(Set<ItemsetKey> sensitive) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        byte[] d=md.digest(String.join(";",sensitive.stream().map(ItemsetKey::toString).sorted().toList()).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); for(int i=0;i<8;i++) sb.append(String.format("%02x",d[i])); return sb.toString();
    }

    private static Set<ItemsetKey> selectSensitive(Map<ItemsetKey,PatternInfo> huis,double sepPct,int sensitiveCount,long seed){
        List<ItemsetKey> p=new ArrayList<>(huis.values().stream().filter(PatternInfo::periodicHighUtility).map(PatternInfo::key).toList());
        int n=sepPct>0?Math.max(1,(int)Math.ceil(p.size()*sepPct/100.0)):sensitiveCount;
        if(p.size()<n) throw new IllegalArgumentException("Only "+p.size()+" PHUIs; cannot select "+n+" sensitive patterns");
        Collections.sort(p); Collections.shuffle(p,new Random(seed)); return new LinkedHashSet<>(p.subList(0,n));
    }
    private static void validateSensitive(Set<ItemsetKey> s,Map<ItemsetKey,PatternInfo> huis){ for(ItemsetKey k:s){PatternInfo p=huis.get(k);if(p==null||!p.periodicHighUtility())throw new IllegalArgumentException("Sensitive itemset is not a PHUI: "+k);} }
    private static PeriodicityConstraints constraints(Map<String,String>a){return new PeriodicityConstraints(longv(a,"minUtil",260),intv(a,"minPer",1),intv(a,"maxPer",6),doublev(a,"minAvg",1),doublev(a,"maxAvg",2));}
    private static String req(Map<String,String>a,String k){String v=a.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing --"+k);return ExperimentSpec.expand(v);}
    private static int intv(Map<String,String>a,String k,int d){return Integer.parseInt(a.getOrDefault(k,Integer.toString(d)));}
    private static long longv(Map<String,String>a,String k,long d){return Long.parseLong(a.getOrDefault(k,Long.toString(d)));}
    private static double doublev(Map<String,String>a,String k,double d){return Double.parseDouble(a.getOrDefault(k,Double.toString(d)));}
}
