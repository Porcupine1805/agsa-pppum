package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Artificial-cost-Guarded Side-effect-Aware (AGSA) sanitizer. */
public class AgsaSanitizer implements Sanitizer {
    protected final AgsaWeights weights; protected final int topKTransactions; protected final boolean hardGuard; protected final String label;
    public AgsaSanitizer(){this(AgsaWeights.defaults(),0,true,"AGSA");}
    public AgsaSanitizer(boolean hardGuard){this(AgsaWeights.defaults(),0,hardGuard,hardGuard?"AGSA":"AGSA-NOGUARD");}
    public AgsaSanitizer(double lm,double lu,double le,boolean guard){this(new AgsaWeights(1000,60,lm,lu,le),0,guard,guard?"AGSA":"AGSA-NOGUARD");}
    public AgsaSanitizer(AgsaWeights w,boolean guard,String label){this(w,0,guard,label);}
    protected AgsaSanitizer(AgsaWeights w,int topK,boolean guard,String label){this.weights=w;this.topKTransactions=topK;this.hardGuard=guard;this.label=label;}
    protected String name(){return label;}

    private record Candidate(int tid,String item,int newQ,double score,int artificialDelta,int missingDelta,int hiddenDelta,double progress,long utilLoss,long witnessChecks){}

    @Override public SanitizationResult sanitize(UtilityDatabase original,PeriodicityConstraints c,Set<ItemsetKey>sensitive,Map<ItemsetKey,PatternInfo> originalHuiUniverse){
        long start=System.nanoTime();UtilityDatabase db=original.copy();List<Edit> edits=new ArrayList<>();
        Set<ItemsetKey> originalPhui=new HashSet<>();for(PatternInfo p:originalHuiUniverse.values())if(p.periodicHighUtility())originalPhui.add(p.key());
        Set<ItemsetKey> nonSensitive=new HashSet<>(originalPhui);nonSensitive.removeAll(sensitive);
        for(ItemsetKey s:sensitive)if(!originalHuiUniverse.containsKey(s))throw new IllegalArgumentException("Sensitive pattern outside HUI witness universe: "+s);
        PatternStateTracker tracker=new PatternStateTracker(originalHuiUniverse);ImpactIndex index=tracker.index();long originalTU=Math.max(1L,original.totalUtility());
        long iter=0,maxIter=Math.max(20_000L,(long)original.size()*Math.max(1,sensitive.size())*8L),candidateEvaluations=0,guardRejections=0,witnessChecks=0;
        while(true){
            ArrayList<PatternInfo> active=new ArrayList<>();for(ItemsetKey s:sensitive){PatternInfo p=tracker.info(s,db,c);if(p.periodicHighUtility())active.add(p);}
            if(active.isEmpty())return result(db,edits,true,hardGuard?"All sensitive PHUIs hidden with hard AC guard":"All sensitive PHUIs hidden (guard disabled)",start,candidateEvaluations,guardRejections,witnessChecks,originalHuiUniverse.size(),tracker,c);
            if(++iter>maxIter)return result(db,edits,false,"Iteration safeguard reached",start,candidateEvaluations,guardRejections,witnessChecks,originalHuiUniverse.size(),tracker,c);
            active.sort(Comparator.<PatternInfo>comparingInt(p->p.key().size()).reversed().thenComparing(Comparator.comparingLong(PatternInfo::utility).reversed()));
            PatternInfo target=active.get(0);Candidate best=null;
            for(int tid:candidateTids(target,db)){
                Transaction tx=db.transactionByTid(tid);
                for(String item:candidateItems(target.key(),tx,index)){
                    int oldQ=tx.quantity(item);if(oldQ<=0)continue;
                    LinkedHashSet<Integer> qChoices=criticalActions(item,tid,oldQ,tracker,index,db,c);qChoices.add(0);
                    for(int newQ:qChoices){if(newQ<0||newQ>=oldQ)continue;
                        Candidate cand=evaluateCandidate(db,c,sensitive,originalPhui,nonSensitive,tracker,index,tid,item,newQ,originalTU);
                        candidateEvaluations++;witnessChecks+=cand.witnessChecks();if(hardGuard&&cand.artificialDelta()>0){guardRejections++;continue;}
                        if(best==null||cand.score()>best.score()||(cand.score()==best.score()&&cand.utilLoss()<best.utilLoss()))best=cand;
                    }
                }
            }
            if(best==null)return result(db,edits,false,hardGuard?"No AC-safe local edit under current candidate policy":"No local edit under current candidate policy",start,candidateEvaluations,guardRejections,witnessChecks,originalHuiUniverse.size(),tracker,c);
            int oldQ=db.transactionByTid(best.tid()).quantity(best.item());tracker.commit(best.tid(),best.item(),best.newQ(),db);
            String reason=(hardGuard?"AC-guarded":"unguarded")+" candidate: hiddenDelta="+best.hiddenDelta()+
                    ", missingDelta="+best.missingDelta()+", artificialDelta="+best.artificialDelta()+
                    ", progress="+String.format(Locale.ROOT,"%.4f",best.progress())+", utilityLoss="+best.utilLoss()+
                    ", impactDegree="+index.affected(best.item()).size();
            edits.add(new Edit(Math.toIntExact(iter),best.tid(),best.item(),oldQ,best.newQ(),target.key().toString(),best.score(),reason));
        }
    }

    private LinkedHashSet<Integer> criticalActions(String item,int tid,int oldQ,PatternStateTracker tracker,ImpactIndex index,UtilityDatabase db,PeriodicityConstraints c){
        LinkedHashSet<Integer> out=new LinkedHashSet<>();long profit=db.profit(item);
        // Include every currently published PHUI affected by this cell, not
        // only active sensitive targets. Otherwise an MC-label breakpoint can
        // be skipped even though MC is part of the weighted candidate score.
        for(ItemsetKey key:index.affected(item)){
            if(!tracker.containsTid(key,tid))continue;
            PatternSnapshot p=tracker.status(key,db,c);if(!p.periodicHighUtility())continue;
            long excess=p.utility()-c.minUtil()+1L;
            long units=(excess+profit-1)/profit;
            int q=(int)Math.max(0L,oldQ-Math.min(oldQ,units));out.add(q);
        }
        return out;
    }

    protected List<Integer> candidateTids(PatternInfo target,UtilityDatabase db){
        ArrayList<Integer> tids=new ArrayList<>(target.tids());tids.sort(Comparator.<Integer>comparingLong(t->db.transactionByTid(t).itemsetUtility(target.key(),db.profits())).reversed().thenComparingInt(Integer::intValue));
        if(topKTransactions>0&&tids.size()>topKTransactions)return new ArrayList<>(tids.subList(0,topKTransactions));return tids;
    }
    protected List<String> candidateItems(ItemsetKey target,Transaction tx,ImpactIndex index){
        ArrayList<String> items=new ArrayList<>();for(String i:target.items())if(tx.contains(i))items.add(i);
        if(topKTransactions>0&&items.size()>2){items.sort(Comparator.comparingInt((String i)->index.affected(i).size()).thenComparing(Comparator.naturalOrder()));return new ArrayList<>(items.subList(0,2));}return items;
    }

    private Candidate evaluateCandidate(UtilityDatabase db,PeriodicityConstraints c,Set<ItemsetKey>sensitive,Set<ItemsetKey>originalPhui,Set<ItemsetKey>nonSensitive,PatternStateTracker tracker,ImpactIndex index,int tid,String item,int newQ,long originalTU){
        Transaction tx=db.transactionByTid(tid);int oldQ=tx.quantity(item);long utilLoss=(long)(oldQ-newQ)*db.profit(item);int artificial=0,missing=0,hidden=0;long checks=0;
        for(ItemsetKey k:index.affected(item)){if(!tracker.containsTid(k,tid))continue;PatternSnapshot before=tracker.status(k,db,c),after=tracker.virtualStatus(k,tid,item,newQ,db,c);checks++;
            if(!originalPhui.contains(k)&&after.periodicHighUtility())artificial++;if(nonSensitive.contains(k)&&before.periodicHighUtility()&&!after.periodicHighUtility())missing++;}
        double progress=0;
        for(ItemsetKey s:sensitive){if(!s.contains(item)||!tracker.containsTid(s,tid))continue;PatternSnapshot before=tracker.status(s,db,c);if(!before.periodicHighUtility())continue;PatternSnapshot after=tracker.virtualStatus(s,tid,item,newQ,db,c);if(!after.periodicHighUtility())hidden++;
            double up=Math.max(0.0,before.utility()-after.utility())/Math.max(1.0,before.utility()-c.minUtil()+1.0);
            double maxp=Math.max(0.0,after.periodicity().maxPeriod()-before.periodicity().maxPeriod())/Math.max(1.0,c.maxPer());
            double avgp=Math.max(0.0,after.periodicity().avgPeriod()-before.periodicity().avgPeriod())/Math.max(1.0,c.maxAvg());
            progress+=Math.min(2.0,up+maxp+avgp);
        }
        double lossPct=100.0*utilLoss/originalTU,mag=(oldQ-newQ)/(double)Math.max(1,oldQ);
        double score=weights.hidden()*hidden+weights.progress()*progress-weights.missing()*missing-weights.utility()*lossPct-weights.edit()*mag;
        return new Candidate(tid,item,newQ,score,artificial,missing,hidden,progress,utilLoss,checks);
    }

    private SanitizationResult result(UtilityDatabase db,List<Edit>edits,boolean feasible,String msg,long start,long candidates,long rejected,long checks,int witnessSize,PatternStateTracker tracker,PeriodicityConstraints c){
        return new SanitizationResult(name(),db,List.copyOf(edits),feasible,msg,(System.nanoTime()-start)/1_000_000L,new SanitizationDiagnostics(candidates,rejected,checks,witnessSize),tracker.snapshotAll(db,c));
    }
}
