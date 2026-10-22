package org.ppum.agsa.cli;

import org.ppum.agsa.metrics.Metrics;
import java.util.Locale;

record RunRecord(String configId,String dataset,String timeline,int replicate,long seed,
                 double sepPct,int prefixTx,int txCount,long nonEmptyTx,long totalUtility,long minUtil,int minPer,int maxPer,
                 double minAvg,double maxAvg,int maxLen,int topK,String sensitiveHash,int originalPHUIs,
                 long originalMiningMs,double originalMiningPeakMiB,boolean originalCacheHit,
                 long postMiningMs,long algorithmWallMs,double algorithmPeakMiB,Metrics m,String status,String message) {
    static String header(){return "config_id,dataset,timeline,replicate,seed,sep_pct,prefix_tx,tx_count,nonempty_tx,total_utility,"+
            "min_util,min_per,max_per,min_avg,max_avg,max_len,top_k,sensitive_hash,"+
            "original_HUIs,sanitized_HUIs,original_PHUIs,sanitized_PHUIs,sensitive_count,"+
            "algorithm,feasible,HF,MC,AC,HF_pct,MC_pct,AC_pct,IUS_pct,DUS_pct,DSS_pct,structureL2,"+
            "modifiedTx_pct,editedCells,editOps,utilityLossAbs,utilityLoss_pct,candidateEvaluations,guardRejections,guardReject_pct,witnessChecks,witnessUniverseSize,"+
            "originalMining_ms,originalCacheHit,postMining_ms,sanitizer_ms,algorithmWall_ms,originalMiningPeak_MiB,algorithmPeak_MiB,status,message";}
    String csv(){return String.format(Locale.ROOT,
            "%s,%s,%s,%d,%d,%.6f,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%d,%d,%s,"+
            "%d,%d,%d,%d,%d,%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%.6f,"+
            "%d,%d,%.6f,%d,%d,%d,%s,%d,%d,%d,%.6f,%.6f,%s,\"%s\"",
            q(configId),q(dataset),q(timeline),replicate,seed,sepPct,prefixTx,txCount,nonEmptyTx,totalUtility,minUtil,minPer,maxPer,minAvg,maxAvg,maxLen,topK,q(sensitiveHash),
            m.originalHUIs(),m.sanitizedHUIs(),m.originalPHUIs(),m.sanitizedPHUIs(),m.sensitiveCount(),q(m.algorithm()),m.feasible(),m.hidingFailure(),m.missingCost(),m.artificialCost(),m.hfRatePct(),m.mcRatePct(),m.acRatePct(),
            m.itemsetUtilitySimilarityPct(),m.databaseUtilitySimilarityPct(),m.databaseStructureSimilarityPct(),m.transactionPatternL2(),m.modifiedTransactionPct(),m.editedCells(),m.editOperations(),m.utilityLossAbs(),m.utilityLossPct(),
            m.candidateEvaluations(),m.guardRejections(),m.guardRejectPct(),m.witnessChecks(),m.witnessUniverseSize(),originalMiningMs,originalCacheHit,postMiningMs,m.runtimeMillis(),algorithmWallMs,originalMiningPeakMiB,algorithmPeakMiB,q(status),esc(message));}
    private static String q(String s){return esc(s);}private static String esc(String s){return s==null?"":s.replace("\"","'").replace("\n"," ").replace("\r"," ");}
}
