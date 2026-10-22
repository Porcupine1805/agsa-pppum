package org.ppum.agsa.cli;

import org.ppum.agsa.io.SyntheticGenerator;
import org.ppum.agsa.metrics.*;
import org.ppum.agsa.mining.VerticalHuiMiner;
import org.ppum.agsa.model.*;
import org.ppum.agsa.sanitize.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class SyntheticStudy {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length>0?args[0]:"results/synthetic_study.csv");
        Files.createDirectories(out.toAbsolutePath().getParent());
        record Cfg(int tx,int items,double density){}
        List<Cfg> cfgs=List.of(new Cfg(60,12,.18),new Cfg(80,14,.25),new Cfg(100,16,.32));
        try(BufferedWriter w=Files.newBufferedWriter(out)){
            w.write("tx,items,density,seed,minUtil,phuis,algorithm,feasible,HF,MC,AC,IUS_pct,DUS_pct,modifiedTx_pct,editOps,runtime_ms\n");
            for(Cfg cfg:cfgs) for(long seed=1;seed<=3;seed++){
                UtilityDatabase db=SyntheticGenerator.generate(cfg.tx,cfg.items,cfg.density,seed);
                PeriodicityConstraints c=null;Map<ItemsetKey,PatternInfo> orig=null;
                for(double frac:new double[]{.06,.04,.03,.02,.01}){
                    long min=Math.max(20,Math.round(db.totalUtility()*frac));
                    PeriodicityConstraints cc=new PeriodicityConstraints(min,1,Math.max(5,cfg.tx/3),1.0,Math.max(2.0,cfg.tx/3.0));
                    Map<ItemsetKey,PatternInfo> mm=new VerticalHuiMiner().mine(db,cc,4);
                    long pc=mm.values().stream().filter(PatternInfo::periodicHighUtility).count();
                    if(pc>=5){c=cc;orig=mm;break;}
                }
                if(c==null)continue;
                List<ItemsetKey> p=new ArrayList<>(orig.values().stream().filter(PatternInfo::periodicHighUtility)
                        .filter(x->x.key().size()>=2).map(PatternInfo::key).toList());
                if(p.size()<3)p=new ArrayList<>(orig.values().stream().filter(PatternInfo::periodicHighUtility).map(PatternInfo::key).toList());
                Collections.shuffle(p,new Random(seed*991));Set<ItemsetKey>s=new LinkedHashSet<>(p.subList(0,Math.min(3,p.size())));
                List<Sanitizer> algs=List.of(new MuMapSanitizer(),new MuMipSanitizer(),new AgsaSanitizer(),new FastAgsaSanitizer(4));
                final PeriodicityConstraints cc=c; final Map<ItemsetKey,PatternInfo> oo=orig;
                long phuis=orig.values().stream().filter(PatternInfo::periodicHighUtility).count();
                for(Sanitizer alg:algs){
                    SanitizationResult r=alg.sanitize(db,cc,s,oo);
                    Map<ItemsetKey,PatternInfo> after=new VerticalHuiMiner().mine(r.sanitizedDatabase(),cc,4);
                    Metrics m=Evaluator.evaluate(r.algorithm(),db,r.sanitizedDatabase(),s,oo,after,r);
                    w.write(String.format(Locale.ROOT,"%d,%d,%.3f,%d,%d,%d,%s,%s,%d,%d,%d,%.6f,%.6f,%.6f,%d,%d%n",
                            cfg.tx,cfg.items,cfg.density,seed,cc.minUtil(),phuis,m.algorithm(),m.feasible(),m.hidingFailure(),m.missingCost(),m.artificialCost(),
                            m.itemsetUtilitySimilarityPct(),m.databaseUtilitySimilarityPct(),m.modifiedTransactionPct(),m.editOperations(),m.runtimeMillis()));
                    w.flush();
                }
            }
        }
        System.out.println("Wrote "+out.toAbsolutePath());
    }
}
