package org.ppum.agsa.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class BatchRunner {
    private BatchRunner() {}

    static void run(Map<String,String> args) throws Exception {
        Path config=Path.of(require(args,"config"));
        Path out=Path.of(args.getOrDefault("out","results/batch_runs.csv"));
        boolean continueOnError=Boolean.parseBoolean(args.getOrDefault("continueOnError","true"));
        List<Map<String,String>> rows=readCsv(config);
        if(rows.isEmpty())throw new IllegalArgumentException("No experiment rows in "+config);
        Files.deleteIfExists(out);
        Path errors=out.resolveSibling(out.getFileName().toString().replace(".csv","")+"_errors.csv");
        Files.deleteIfExists(errors);
        int ok=0,failed=0;
        for(int idx=0;idx<rows.size();idx++){
            Map<String,String> m=new LinkedHashMap<>(rows.get(idx));
            m.putIfAbsent("configId","row"+(idx+1));
            m.putIfAbsent("saveArtifacts",args.getOrDefault("saveArtifacts","false"));
            System.out.printf(Locale.ROOT,"[%d/%d] %s dataset=%s%n",idx+1,rows.size(),m.get("configId"),m.getOrDefault("dataset",""));
            try{
                ExperimentSpec spec=ExperimentSpec.from(m);
                Path artifactBase=Path.of(args.getOrDefault("artifactDir","results/artifacts"),spec.configId());
                ExperimentRunner.Output rr=ExperimentRunner.run(spec,artifactBase,out);
                String sensitiveDir=args.getOrDefault("sensitiveDir","").trim();
                if(!sensitiveDir.isEmpty()){
                    Path dir=Path.of(sensitiveDir);Files.createDirectories(dir);
                    ExternalInterop.writeSensitiveFiles(rr.sensitive(),rr.originalHuis(),
                            dir.resolve(spec.configId()+".simple.txt"),dir.resolve(spec.configId()+".upstream.txt"));
                }
                ok++;
            }catch(Throwable e){
                failed++;
                writeError(errors,idx+1,m,e);
                System.err.println("  ERROR: "+e);
                if(!continueOnError)throw e;
            }
        }
        System.out.printf(Locale.ROOT,"Batch complete: rows=%d ok=%d failed=%d output=%s errors=%s%n",
                rows.size(),ok,failed,out.toAbsolutePath(),errors.toAbsolutePath());
    }

    private static List<Map<String,String>> readCsv(Path path)throws IOException{
        List<Map<String,String>> out=new ArrayList<>();
        try(BufferedReader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String line;String[] header=null;
            while((line=r.readLine())!=null){
                if(line.isBlank()||line.stripLeading().startsWith("#"))continue;
                String[] p=split(line);
                if(header==null){header=p;continue;}
                Map<String,String> m=new LinkedHashMap<>();
                for(int i=0;i<header.length;i++)m.put(header[i].trim(),i<p.length?unquote(p[i].trim()):"");
                if(!m.getOrDefault("enabled","true").equalsIgnoreCase("false"))out.add(m);
            }
        }
        return out;
    }

    /** Minimal CSV splitter supporting quoted cells; sufficient for experiment matrices. */
    private static String[] split(String line){
        List<String> cells=new ArrayList<>();StringBuilder sb=new StringBuilder();boolean quote=false;
        for(int i=0;i<line.length();i++){
            char c=line.charAt(i);
            if(c=='\"'){
                if(quote&&i+1<line.length()&&line.charAt(i+1)=='\"'){sb.append('\"');i++;}
                else quote=!quote;
            }else if(c==','&&!quote){cells.add(sb.toString());sb.setLength(0);}else sb.append(c);
        }
        cells.add(sb.toString());return cells.toArray(String[]::new);
    }
    private static String unquote(String s){return s;}
    private static String require(Map<String,String>a,String k){String v=a.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing --"+k);return v;}
    private static void writeError(Path path,int row,Map<String,String>m,Throwable e)throws IOException{
        boolean header=!Files.exists(path)||Files.size(path)==0;
        try(BufferedWriter w=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){
            if(header)w.write("time,row,config_id,dataset,error\n");
            w.write(Instant.now()+","+row+","+safe(m.get("configId"))+","+safe(m.get("dataset"))+",\""+safe(e.toString())+"\"\n");
        }
    }
    private static String safe(String s){return s==null?"":s.replace("\"","'").replace("\n"," ").replace("\r"," ");}
}
