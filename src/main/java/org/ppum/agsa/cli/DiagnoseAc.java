package org.ppum.agsa.cli;

import org.ppum.agsa.io.DatabaseIO;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.*;

import java.nio.file.Path;
import java.util.*;

/** Print every artificial PHUI and whether it existed in the original HUI universe. */
final class DiagnoseAc {
    private DiagnoseAc(){}
    static void run(Map<String,String> a) throws Exception {
        Path dbp=Path.of(a.get("db")), pp=Path.of(a.get("profits"));
        PeriodicityConstraints c=new PeriodicityConstraints(
                Long.parseLong(a.getOrDefault("minUtil","3000")),
                Integer.parseInt(a.getOrDefault("minPer","1")),
                Integer.parseInt(a.getOrDefault("maxPer","1000")),
                Double.parseDouble(a.getOrDefault("minAvg","1")),
                Double.parseDouble(a.getOrDefault("maxAvg","500")));
        String timeline=a.getOrDefault("timeline","fixed");
        UtilityDatabase original=DatabaseIO.readSimple(dbp,pp);
        Map<ItemsetKey,PatternInfo> origH=new VerticalHuiMiner().mine(original,c,0);
        Set<ItemsetKey> origP=phui(origH);
        List<ItemsetKey> allP=new ArrayList<>(origP);
        Collections.sort(allP);
        double sep=Double.parseDouble(a.getOrDefault("sepPct","1"));
        int n=Math.max(1,(int)Math.ceil(allP.size()*sep/100.0));
        Collections.shuffle(allP,new Random(Long.parseLong(a.getOrDefault("seed","202601"))));
        Set<ItemsetKey> sensitive=new LinkedHashSet<>(allP.subList(0,Math.min(n,allP.size())));
        System.out.printf(Locale.ROOT,"tx=%d nonempty=%d H=%d P=%d S=%d timeline=%s minPer=%d minAvg=%s%n",
                original.size(),original.nonEmptyTransactions(),origH.size(),origP.size(),sensitive.size(),timeline,c.minPer(),String.valueOf(c.minAvg()));
        for(String raw:a.getOrDefault("algorithms","SMAU-REF|AGSA").split("[|,]")){
            String name=raw.trim(); if(name.isEmpty()) continue;
            Sanitizer alg=switch(name.toUpperCase(Locale.ROOT)){
                case "SMAU","SMAU-REF" -> new SmauSanitizer();
                case "SMAU-PHUI" -> new SmauSanitizer(true);
                case "SMSE","SMSE-REF" -> new SmseSanitizer();
                case "SMSE-PHUI" -> new SmseSanitizer(true);
                case "AGSA" -> new AgsaSanitizer(true);
                case "AGSA-NOGUARD" -> new AgsaSanitizer(false);
                case "MU-MAP" -> new MuMapSanitizer();
                default -> throw new IllegalArgumentException("algorithm "+name);
            };
            SanitizationResult r=alg.sanitize(original,c,sensitive,origH);
            UtilityDatabase storage=r.sanitizedDatabase();
            UtilityDatabase patternDb="compressed".equals(timeline)?storage.compactedTimeline():storage;
            Map<ItemsetKey,PatternInfo> sanH="fixed".equals(timeline)&&r.finalWitnessStates()!=null
                    ? r.finalWitnessStates() : new VerticalHuiMiner().mine(patternDb,c,0);
            Map<ItemsetKey,PatternInfo> remine=new VerticalHuiMiner().mine(patternDb,c,0);
            Set<ItemsetKey> p1=phui(sanH), pRem=phui(remine);
            int ac=0; for(ItemsetKey x:p1) if(!origP.contains(x)) ac++;
            int acR=0; for(ItemsetKey x:pRem) if(!origP.contains(x)) acR++;
            System.out.printf(Locale.ROOT,"%s edits=%d emptyTx=%d trackerAC=%d remineAC=%d remineH=%d remineP=%d%n",
                    r.algorithm(), r.edits().size(), storage.size()-storage.nonEmptyTransactions(), ac, acR, remine.size(), pRem.size());
            Set<ItemsetKey> arts=new TreeSet<>(Comparator.comparing(ItemsetKey::toString));
            arts.addAll(p1); arts.addAll(pRem); arts.removeAll(origP);
            for(ItemsetKey k:arts){
                PatternInfo o=origH.get(k);
                PatternInfo t=sanH.get(k);
                PatternInfo m=remine.get(k);
                System.out.printf(Locale.ROOT,"  AC %s origH=%s origPHUI=%s orig(u=%s per=%s tids=%s) tracker=%s remine=%s%n",
                        k, o!=null, o!=null&&o.periodicHighUtility(),
                        o==null?"NA":Long.toString(o.utility()), o==null?"NA":fmt(o.periodicity()),o==null?"NA":o.tids(),
                        t==null?"NA":fmt(t.periodicity())+" u="+t.utility()+" phui="+t.periodicHighUtility(),
                        m==null?"NA":fmt(m.periodicity())+" u="+m.utility()+" phui="+m.periodicHighUtility());
                if(o!=null&&m!=null){
                    System.out.printf(Locale.ROOT,"    failedBefore=%s crossed=%s afterTids=%s edits=%s%n",
                            failed(o,c),crossed(o,m,c),m.tids(),relevantEdits(k,r.edits()));
                }
            }
        }
    }
    private static String fmt(PeriodicityStats s){
        return String.format(Locale.ROOT,"sup=%d minP=%d maxP=%d avg=%.4f",s.support(),s.minPeriod(),s.maxPeriod(),s.avgPeriod());
    }
    private static Set<ItemsetKey> phui(Map<ItemsetKey,PatternInfo> m){
        HashSet<ItemsetKey> s=new HashSet<>();
        for(PatternInfo p:m.values()) if(p.periodicHighUtility()) s.add(p.key());
        return s;
    }
    private static String failed(PatternInfo p,PeriodicityConstraints c){
        ArrayList<String> out=new ArrayList<>();PeriodicityStats s=p.periodicity();
        if(p.utility()<c.minUtil())out.add("utility");
        if(s.minPeriod()<c.minPer())out.add("lower-min");
        if(s.maxPeriod()>c.maxPer())out.add("upper-max");
        if(s.avgPeriod()<c.minAvg())out.add("lower-avg");
        if(s.avgPeriod()>c.maxAvg())out.add("upper-avg");
        return out.toString();
    }
    private static String crossed(PatternInfo before,PatternInfo after,PeriodicityConstraints c){
        ArrayList<String> out=new ArrayList<>();PeriodicityStats b=before.periodicity(),n=after.periodicity();
        if(b.minPeriod()<c.minPer()&&n.minPeriod()>=c.minPer())out.add("lower-min");
        if(b.avgPeriod()<c.minAvg()&&n.avgPeriod()>=c.minAvg())out.add("lower-avg");
        if(b.maxPeriod()>c.maxPer()&&n.maxPeriod()<=c.maxPer())out.add("upper-max");
        if(b.avgPeriod()>c.maxAvg()&&n.avgPeriod()<=c.maxAvg())out.add("upper-avg");
        if(before.utility()<c.minUtil()&&after.utility()>=c.minUtil())out.add("utility-INCONSISTENT");
        return out.isEmpty()?"UNEXPLAINED":out.toString();
    }
    private static String relevantEdits(ItemsetKey key,List<Edit> edits){
        ArrayList<String> out=new ArrayList<>();
        for(Edit e:edits)if(key.contains(e.item()))out.add("#"+e.iteration()+":T"+e.tid()+":"+e.item()+" "+e.oldQuantity()+"->"+e.newQuantity());
        return out.toString();
    }
}
