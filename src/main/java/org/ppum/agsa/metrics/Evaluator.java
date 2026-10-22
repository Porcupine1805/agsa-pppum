package org.ppum.agsa.metrics;

import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.*;
import java.util.*;

public final class Evaluator {
    private Evaluator(){}
    public static Metrics evaluate(String algorithm,UtilityDatabase original,UtilityDatabase sanitized,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo>originalHuis,Map<ItemsetKey,PatternInfo>sanitizedHuis,SanitizationResult result){return evaluate(algorithm,original,sanitized,sanitized,sensitive,originalHuis,sanitizedHuis,result);}
    public static Metrics evaluate(String algorithm,UtilityDatabase original,UtilityDatabase sanitizedStorage,UtilityDatabase sanitizedPatternDb,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo>originalHuis,Map<ItemsetKey,PatternInfo>sanitizedHuis,SanitizationResult result){
        Set<ItemsetKey>p0=phuis(originalHuis),p1=phuis(sanitizedHuis),nonSensitive=new HashSet<>(p0);nonSensitive.removeAll(sensitive);
        int hf=0;for(ItemsetKey s:sensitive)if(p1.contains(s))hf++;int mc=0;for(ItemsetKey x:nonSensitive)if(!p1.contains(x))mc++;int ac=0;for(ItemsetKey x:p1)if(!p0.contains(x))ac++;
        double hfPct=100.0*hf/Math.max(1,sensitive.size()),mcPct=100.0*mc/Math.max(1,nonSensitive.size()),acPct=100.0*ac/Math.max(1,p1.size());
        long sum0=0,sum1=0;for(PatternInfo p:originalHuis.values())if(p.periodicHighUtility())sum0+=p.utility();for(PatternInfo p:sanitizedHuis.values())if(p.periodicHighUtility())sum1+=p.utility();double ius=100.0*sum1/Math.max(1L,sum0);
        long otu=original.totalUtility(),stu=sanitizedStorage.totalUtility(),loss=Math.max(0L,otu-stu);double lossPct=100.0*loss/Math.max(1.0,otu),dus=100.0*stu/Math.max(1.0,otu);
        double dss=databaseStructureSimilarityPct(original,sanitizedStorage),l2=transactionPatternL2(original,sanitizedStorage);
        int modified=0,cells=0,n=Math.min(original.size(),sanitizedStorage.size());for(int tid=1;tid<=n;tid++){int c=original.transactionByTid(tid).changedCellsAgainst(sanitizedStorage.transactionByTid(tid));if(c>0){modified++;cells+=c;}}
        SanitizationDiagnostics diag=result.diagnostics()==null?SanitizationDiagnostics.none(originalHuis.size()):result.diagnostics();double rejectPct=100.0*diag.guardRejections()/Math.max(1.0,diag.candidateEvaluations());
        return new Metrics(algorithm,originalHuis.size(),sanitizedHuis.size(),p0.size(),p1.size(),sensitive.size(),hf,mc,ac,hfPct,mcPct,acPct,ius,dus,dss,l2,100.0*modified/Math.max(1,n),cells,result.edits().size(),loss,lossPct,diag.candidateEvaluations(),diag.guardRejections(),rejectPct,diag.witnessChecks(),diag.witnessUniverseSize(),result.runtimeMillis(),result.feasible());
    }
    private static Set<ItemsetKey> phuis(Map<ItemsetKey,PatternInfo>m){HashSet<ItemsetKey>s=new HashSet<>();for(PatternInfo p:m.values())if(p.periodicHighUtility())s.add(p.key());return s;}

    /** Exact transaction item-presence key. Item Strings are canonical references from the profit table. */
    private static final class PatternKey {
        final String[] items; final int hash;
        PatternKey(String[] items){this.items=items;this.hash=Arrays.hashCode(items);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object o){return o instanceof PatternKey k&&Arrays.equals(items,k.items);}
    }
    private static Map<PatternKey,Integer> freq(UtilityDatabase db){
        HashMap<PatternKey,Integer> f=new HashMap<>(Math.max(16,(int)Math.min(1_500_000L,db.size()*4L/3L+1)));
        for(Transaction t:db.transactions())f.merge(new PatternKey(t.activeItemsArray()),1,Integer::sum);return f;
    }
    private static double databaseStructureSimilarityPct(UtilityDatabase a,UtilityDatabase b){
        Map<PatternKey,Integer> fa=freq(a),fb=freq(b);double dot=0,na=0,nb=0;
        for(var e:fa.entrySet()){double x=e.getValue(),y=fb.getOrDefault(e.getKey(),0);dot+=x*y;na+=x*x;}for(double y:fb.values())nb+=y*y;
        if(na==0&&nb==0)return 100;if(na==0||nb==0)return 0;return 100*dot/(Math.sqrt(na)*Math.sqrt(nb));
    }
    private static double transactionPatternL2(UtilityDatabase a,UtilityDatabase b){
        Map<PatternKey,Integer> fa=freq(a),fb=freq(b);double sum=0;HashSet<PatternKey>seen=new HashSet<>(Math.max(16,fa.size()*2));
        for(var e:fa.entrySet()){double d=e.getValue()-fb.getOrDefault(e.getKey(),0);sum+=d*d;seen.add(e.getKey());}
        for(var e:fb.entrySet())if(!seen.contains(e.getKey()))sum+=(double)e.getValue()*e.getValue();return Math.sqrt(sum);
    }
}
