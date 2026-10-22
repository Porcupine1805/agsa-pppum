package org.ppum.agsa.cli;

import org.ppum.agsa.model.PeriodicityConstraints;
import java.nio.file.*;
import java.util.*;

record ExperimentSpec(String configId,String dataset,Path db,Path profits,Path sensitive,
                      long minUtil,int minPer,int maxPer,double minAvg,double maxAvg,
                      double sepPct,int sensitiveCount,long seed,int replicate,int prefixTx,int maxLen,
                      String algorithms,int topK,String timeline,
                      double lambdaHidden,double lambdaProgress,double lambdaMissing,double lambdaUtility,double lambdaEdit,
                      boolean saveArtifacts,boolean forceRemine) {
    ExperimentSpec {
        if(sepPct<0||sepPct>100)throw new IllegalArgumentException("sepPct must be in [0,100]");
        if(sensitiveCount<0)throw new IllegalArgumentException("sensitiveCount must be non-negative");
        if(replicate<1)throw new IllegalArgumentException("replicate must be at least 1");
        if(prefixTx<0||maxLen<0)throw new IllegalArgumentException("prefixTx and maxLen must be non-negative");
        if(topK<1)throw new IllegalArgumentException("topK must be at least 1");
        for(double w:new double[]{lambdaHidden,lambdaProgress,lambdaMissing,lambdaUtility,lambdaEdit})
            if(!Double.isFinite(w)||w<0)throw new IllegalArgumentException("All objective weights must be finite and non-negative");
        // Validate the mathematical domain at the configuration boundary.
        new PeriodicityConstraints(minUtil,minPer,maxPer,minAvg,maxAvg);
    }
    static ExperimentSpec from(Map<String,String>a){
        String dbS=required(a,"db"),profitS=required(a,"profits"),sens=a.getOrDefault("sensitive","").trim();
        double sep=dbl(a,"sepPct",0);int count=integer(a,"sensitiveCount",3);
        if(sep>0&&count>0)throw new IllegalArgumentException(
                "Ambiguous sensitive-set size: set either sepPct>0 or sensitiveCount>0, not both");
        if(sep<=0&&count<=0&&sens.isEmpty())throw new IllegalArgumentException(
                "Provide a sensitive file, sepPct>0, or sensitiveCount>0");
        return new ExperimentSpec(a.getOrDefault("configId","single"),a.getOrDefault("dataset","dataset"),
                Path.of(expand(dbS)),Path.of(expand(profitS)),sens.isEmpty()?null:Path.of(expand(sens)),
                lng(a,"minUtil",260),integer(a,"minPer",1),integer(a,"maxPer",6),dbl(a,"minAvg",1),dbl(a,"maxAvg",2),
                sep,count,lng(a,"seed",42),integer(a,"replicate",1),integer(a,"prefixTx",0),integer(a,"maxLen",0),
                a.getOrDefault("algorithms","MU-MAP|MU-MIP|SMAU-REF|SMIU-REF|SMSE-REF|AGSA|FAGSA").replace(',', '|'),
                integer(a,"topK",5),a.getOrDefault("timeline","fixed").toLowerCase(Locale.ROOT),
                dbl(a,"lambdaHidden",1000),dbl(a,"lambdaProgress",60),dbl(a,"lambdaMissing",30),dbl(a,"lambdaUtility",8),dbl(a,"lambdaEdit",2),
                bool(a,"saveArtifacts",true),bool(a,"forceRemine",false));
    }
    PeriodicityConstraints constraints(){return new PeriodicityConstraints(minUtil,minPer,maxPer,minAvg,maxAvg);}
    private static String required(Map<String,String>a,String k){String v=a.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing --"+k);return v;}
    static String expand(String s){String out=s;int guard=0;while(out.contains("${")&&guard++<20){int a=out.indexOf("${"),b=out.indexOf('}',a+2);if(b<0)break;String key=out.substring(a+2,b),val=System.getenv(key);if(val==null)throw new IllegalArgumentException("Environment variable "+key+" is not set for path: "+s);out=out.substring(0,a)+val+out.substring(b+1);}return out;}
    private static int integer(Map<String,String>a,String k,int v){return Integer.parseInt(a.getOrDefault(k,String.valueOf(v)));}
    private static long lng(Map<String,String>a,String k,long v){return Long.parseLong(a.getOrDefault(k,String.valueOf(v)));}
    private static double dbl(Map<String,String>a,String k,double v){return Double.parseDouble(a.getOrDefault(k,String.valueOf(v)));}
    private static boolean bool(Map<String,String>a,String k,boolean v){return Boolean.parseBoolean(a.getOrDefault(k,String.valueOf(v)));}
}
