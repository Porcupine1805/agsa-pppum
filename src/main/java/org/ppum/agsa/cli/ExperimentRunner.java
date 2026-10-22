package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.metrics.*;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.mining.PatternEvaluator;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

final class ExperimentRunner {
    private ExperimentRunner() {}
    record Output(List<RunRecord> records,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo> originalHuis,int originalHuiCount,int originalPhuiCount){}
    /** HUI membership and period statistics do not depend on period thresholds. */
    private record CacheKey(String db,String profits,int prefixTx,long minUtil,int maxLen){}
    private record CachedInput(CacheKey key,UtilityDatabase db,Map<ItemsetKey,PatternInfo> huis,long miningMs,double miningPeakMiB){}
    private static CachedInput lastCache;

    static Output run(ExperimentSpec spec,Path artifactBase)throws Exception{
        return run(spec,artifactBase,null);
    }

    static Output run(ExperimentSpec spec,Path artifactBase,Path liveOut)throws Exception{
        if(!spec.timeline().equals("fixed")&&!spec.timeline().equals("compressed"))throw new IllegalArgumentException("timeline must be fixed or compressed");
        PeriodicityConstraints c=spec.constraints();CacheKey ck=new CacheKey(spec.db().toAbsolutePath().normalize().toString(),spec.profits().toAbsolutePath().normalize().toString(),spec.prefixTx(),c.minUtil(),spec.maxLen());
        CachedInput ci;boolean cacheHit;
        synchronized(ExperimentRunner.class){cacheHit=lastCache!=null&&lastCache.key().equals(ck);if(cacheHit)ci=lastCache;else{UtilityDatabase d=DatabaseIO.readSimple(spec.db(),spec.profits(),spec.prefixTx());long st=System.nanoTime();Map<ItemsetKey,PatternInfo> h;double peak;System.out.printf(Locale.ROOT,"  mine-start tx=%d minUtil=%d maxLen=%d%n",d.size(),c.minUtil(),spec.maxLen());try(MemorySampler ms=new MemorySampler()){h=new VerticalHuiMiner().mine(d,c,spec.maxLen());peak=ms.peakMiB();}ci=new CachedInput(ck,d,h,(System.nanoTime()-st)/1_000_000L,peak);lastCache=ci;System.out.printf(Locale.ROOT,"  mine-done HUI=%d ms=%d peakMiB=%.1f%n",h.size(),ci.miningMs(),peak);}}
        UtilityDatabase original=ci.db();Map<ItemsetKey,PatternInfo> originalHuis=relabel(ci.huis(),c);long phuiCount=originalHuis.values().stream().filter(PatternInfo::periodicHighUtility).count();
        int want=spec.sepPct()>0?Math.max(1,(int)Math.ceil(phuiCount*spec.sepPct()/100.0)):spec.sensitiveCount();
        if(phuiCount<want){
            String msg="Only "+phuiCount+" PHUIs; cannot select "+want+" sensitive patterns";
            List<RunRecord> skip=new ArrayList<>();
            for(String label:algorithmLabels(spec)){
                Metrics m=new Metrics(label,originalHuis.size(),originalHuis.size(),(int)phuiCount,(int)phuiCount,0,
                        0,0,0,0,0,0,100,100,100,0,0,0,0,0,0,0,0,0,0,originalHuis.size(),0,false);
                skip.add(new RunRecord(spec.configId(),spec.dataset(),spec.timeline(),spec.replicate(),spec.seed(),spec.sepPct(),spec.prefixTx(),original.size(),original.nonEmptyTransactions(),original.totalUtility(),c.minUtil(),c.minPer(),c.maxPer(),c.minAvg(),c.maxAvg(),spec.maxLen(),spec.topK(),"none",(int)phuiCount,ci.miningMs(),ci.miningPeakMiB(),cacheHit,0,0,ci.miningPeakMiB(),m,"NO_PHUI",msg));
            }
            System.out.println("  SKIP: "+msg);
            if(liveOut!=null)writeRunRecords(skip,liveOut,true);
            return new Output(skip,Set.of(),originalHuis,originalHuis.size(),(int)phuiCount);
        }
        Set<ItemsetKey>sensitive=selectSensitive(spec,originalHuis);for(ItemsetKey s:sensitive)if(!originalHuis.containsKey(s)||!originalHuis.get(s).periodicHighUtility())throw new IllegalArgumentException("Sensitive itemset is not a PHUI: "+s);String sensitiveHash=hashSensitive(sensitive);
        List<RunRecord> records=new ArrayList<>();
        for(Sanitizer alg:algorithms(spec)){
            System.out.printf(Locale.ROOT,"  algorithm-start %s%n",alg.getClass().getSimpleName());
            long algStart=System.nanoTime();SanitizationResult r;Map<ItemsetKey,PatternInfo> sanHuis;long postMiningMs=0;double algorithmPeak;
            try(MemorySampler ms=new MemorySampler()){
                r=alg.sanitize(original,c,sensitive,originalHuis);
                if(spec.timeline().equals("fixed")&&r.finalWitnessStates()!=null&&!spec.forceRemine()){
                    // Exact under decrease-only positive-utility semantics: no sanitized HUI can lie outside original H(D).
                    sanHuis=r.finalWitnessStates();
                }else{
                    UtilityDatabase patternDb=spec.timeline().equals("compressed")?r.sanitizedDatabase().compactedTimeline():r.sanitizedDatabase();long pst=System.nanoTime();sanHuis=new VerticalHuiMiner().mine(patternDb,c,spec.maxLen());postMiningMs=(System.nanoTime()-pst)/1_000_000L;
                }
                if(spec.timeline().equals("fixed")&&spec.forceRemine()&&r.finalWitnessStates()!=null)
                    verifyTrackerAgainstRemine(originalHuis,r.finalWitnessStates(),sanHuis,r.sanitizedDatabase(),c);
                algorithmPeak=ms.peakMiB();
            }
            long algorithmWallMs=(System.nanoTime()-algStart)/1_000_000L;UtilityDatabase patternDb=spec.timeline().equals("compressed")?r.sanitizedDatabase().compactedTimeline():r.sanitizedDatabase();
            Metrics m=Evaluator.evaluate(r.algorithm(),original,r.sanitizedDatabase(),patternDb,sensitive,originalHuis,sanHuis,r);
            if(spec.saveArtifacts()&&artifactBase!=null){Path parent=artifactBase.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);String safe=r.algorithm().replaceAll("[^A-Za-z0-9]+","");DatabaseIO.writeSimple(r.sanitizedDatabase(),artifactBase.resolveSibling(artifactBase.getFileName()+"_"+safe+"_sanitized.txt"));writeEdits(r,artifactBase.resolveSibling(artifactBase.getFileName()+"_"+safe+"_edits.csv"));writeSensitive(sensitive,artifactBase.resolveSibling(artifactBase.getFileName()+"_sensitive.txt"));}
            RunRecord rec=new RunRecord(spec.configId(),spec.dataset(),spec.timeline(),spec.replicate(),spec.seed(),spec.sepPct(),spec.prefixTx(),original.size(),original.nonEmptyTransactions(),original.totalUtility(),c.minUtil(),c.minPer(),c.maxPer(),c.minAvg(),c.maxAvg(),spec.maxLen(),spec.topK(),sensitiveHash,(int)phuiCount,ci.miningMs(),ci.miningPeakMiB(),cacheHit,postMiningMs,algorithmWallMs,algorithmPeak,m,r.feasible()?"OK":"INFEASIBLE",r.message());
            records.add(rec);
            if(liveOut!=null)writeRunRecords(List.of(rec),liveOut,true);
            System.out.printf(Locale.ROOT,"  algorithm-done %s wallMs=%d HF=%d MC=%d AC=%d%n",r.algorithm(),algorithmWallMs,m.hidingFailure(),m.missingCost(),m.artificialCost());
        }
        return new Output(records,sensitive,originalHuis,originalHuis.size(),(int)phuiCount);
    }

    private static List<String> algorithmLabels(ExperimentSpec s){
        List<String> out=new ArrayList<>();
        for(String raw:s.algorithms().split("[|,]")){
            String name=raw.trim().toUpperCase(Locale.ROOT);
            if(name.isBlank()) continue;
            out.add(switch(name){
                case "SMAU","SMAU-REF" -> "SMAU-REF";
                case "SMAU-PHUI" -> "SMAU-PHUI";
                case "SMIU","SMIU-REF" -> "SMIU-REF";
                case "SMSE","SMSE-REF" -> "SMSE-REF";
                case "SMSE-PHUI" -> "SMSE-PHUI";
                case "FAGSA" -> "FAGSA-K"+s.topK();
                case "FAGSA-ALL" -> "FAGSA-KALL";
                case "FAGSA-NOGUARD" -> "FAGSA-NOGUARD-K"+s.topK();
                default -> name;
            });
        }
        return out;
    }

    private static List<Sanitizer> algorithms(ExperimentSpec s){
        AgsaWeights w=new AgsaWeights(s.lambdaHidden(),s.lambdaProgress(),s.lambdaMissing(),s.lambdaUtility(),s.lambdaEdit());List<Sanitizer>out=new ArrayList<>();
        for(String raw:s.algorithms().split("[|,]")){String name=raw.trim().toUpperCase(Locale.ROOT);if(name.isBlank())continue;switch(name){
            case "MU-MAP" -> out.add(new MuMapSanitizer());case "MU-MIP" -> out.add(new MuMipSanitizer());
            case "SMAU","SMAU-REF" -> out.add(new SmauSanitizer());case "SMAU-PHUI" -> out.add(new SmauSanitizer(true));case "SMIU","SMIU-REF" -> out.add(new SmiuSanitizer());case "SMSE","SMSE-REF" -> out.add(new SmseSanitizer());case "SMSE-PHUI" -> out.add(new SmseSanitizer(true));
            case "AGSA" -> out.add(new AgsaSanitizer(w,true,"AGSA"));case "AGSA-NOGUARD" -> out.add(new AgsaSanitizer(w,false,"AGSA-NOGUARD"));
            case "AGSA-MC-ONLY" -> out.add(new AgsaSanitizer(new AgsaWeights(s.lambdaHidden(),0,s.lambdaMissing(),0,0),true,"AGSA-MC-ONLY"));
            case "AGSA-UTILITY-ONLY" -> out.add(new AgsaSanitizer(new AgsaWeights(s.lambdaHidden(),0,0,s.lambdaUtility(),0),true,"AGSA-UTILITY-ONLY"));
            case "AGSA-NOPROGRESS" -> out.add(new AgsaSanitizer(new AgsaWeights(s.lambdaHidden(),0,s.lambdaMissing(),s.lambdaUtility(),s.lambdaEdit()),true,"AGSA-NOPROGRESS"));
            case "FAGSA" -> out.add(new FastAgsaSanitizer(s.topK(),w,true,"FAGSA-K"+s.topK()));case "FAGSA-ALL" -> out.add(new AgsaSanitizer(w,true,"FAGSA-KALL"));case "FAGSA-NOGUARD" -> out.add(new FastAgsaSanitizer(s.topK(),w,false,"FAGSA-NOGUARD-K"+s.topK()));
            case "MPH" -> out.add(new MphSanitizer());
            default -> throw new IllegalArgumentException("Unknown algorithm: "+raw);
        }}if(out.isEmpty())throw new IllegalArgumentException("No algorithms configured");return out;
    }

    private static Set<ItemsetKey> selectSensitive(ExperimentSpec spec,Map<ItemsetKey,PatternInfo> huis)throws IOException{
        if(spec.sensitive()!=null)return DatabaseIO.readSensitive(spec.sensitive());List<ItemsetKey>p=new ArrayList<>(huis.values().stream().filter(PatternInfo::periodicHighUtility).map(PatternInfo::key).toList());int n=spec.sepPct()>0?Math.max(1,(int)Math.ceil(p.size()*spec.sepPct()/100.0)):spec.sensitiveCount();if(p.size()<n)throw new IllegalArgumentException("Only "+p.size()+" PHUIs; cannot select "+n+" sensitive patterns");Collections.sort(p);Collections.shuffle(p,new Random(spec.seed()));return new LinkedHashSet<>(p.subList(0,n));
    }
    private static void verifyTrackerAgainstRemine(Map<ItemsetKey,PatternInfo> original,
            Map<ItemsetKey,PatternInfo> tracked,Map<ItemsetKey,PatternInfo> remine,
            UtilityDatabase sanitized,PeriodicityConstraints c){
        for(ItemsetKey key:remine.keySet())if(!original.containsKey(key))
            throw new IllegalStateException("Utility monotonicity/miner violation: re-mine produced a new HUI "+key);
        for(var e:tracked.entrySet()){
            PatternInfo mined=remine.get(e.getKey()),state=e.getValue();
            boolean minedHui=mined!=null&&mined.highUtility();
            boolean minedPhui=mined!=null&&mined.periodicHighUtility();
            if(state.highUtility()!=minedHui||state.periodicHighUtility()!=minedPhui){
                PatternInfo exact=PatternEvaluator.evaluate(e.getKey(),sanitized,c);
                throw new IllegalStateException("Tracker/re-mine mismatch for "+e.getKey()+
                        ": tracker(H="+state.highUtility()+",P="+state.periodicHighUtility()+") remine(H="+
                        minedHui+",P="+minedPhui+") trackerUtility="+state.utility()+
                        " exactUtility="+exact.utility()+" trackerTids="+state.tids()+" exactTids="+exact.tids());
            }
            if(mined!=null&&state.utility()!=mined.utility())
                throw new IllegalStateException("Tracker/re-mine utility mismatch for "+e.getKey()+
                        ": "+state.utility()+" vs "+mined.utility());
        }
    }
    private static Map<ItemsetKey,PatternInfo> relabel(Map<ItemsetKey,PatternInfo> huis,PeriodicityConstraints c){
        LinkedHashMap<ItemsetKey,PatternInfo> out=new LinkedHashMap<>(Math.max(16,huis.size()*2));
        for(PatternInfo p:huis.values())out.put(p.key(),new PatternInfo(p.key(),p.utility(),p.periodicity(),
                true,c.accepts(p.periodicity()),p.tids()));
        return out;
    }
    private static String hashSensitive(Set<ItemsetKey>sensitive)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[]d=md.digest(String.join(";",sensitive.stream().map(ItemsetKey::toString).sorted().toList()).getBytes(StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();for(int i=0;i<8;i++)sb.append(String.format("%02x",d[i]));return sb.toString();}
    static void writeRunRecords(List<RunRecord>rows,Path out,boolean append)throws IOException{Path parent=out.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);boolean header=!append||!Files.exists(out)||Files.size(out)==0;try(BufferedWriter w=Files.newBufferedWriter(out,StandardCharsets.UTF_8,append?new StandardOpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.APPEND}:new StandardOpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING})){if(header){w.write(RunRecord.header());w.newLine();}for(RunRecord r:rows){w.write(r.csv());w.newLine();}}}
    private static void writeEdits(SanitizationResult r,Path out)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(out,StandardCharsets.UTF_8)){w.write("iteration,tid,item,oldQ,newQ,target,score,reason\n");for(Edit e:r.edits())w.write(String.format(Locale.ROOT,"%d,%d,%s,%d,%d,\"%s\",%.8f,\"%s\"%n",e.iteration(),e.tid(),e.item(),e.oldQuantity(),e.newQuantity(),e.targetSensitiveItemset(),e.score(),e.reason().replace("\"","'")));}}
    private static void writeSensitive(Set<ItemsetKey>s,Path out)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(out,StandardCharsets.UTF_8)){for(ItemsetKey k:s.stream().sorted().toList()){w.write(String.join(",",k.items()));w.newLine();}}}
}
