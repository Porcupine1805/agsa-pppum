package org.ppum.agsa.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class EnvironmentInfo {
    private EnvironmentInfo(){}
    static void write(Path out)throws IOException{
        Path parent=out.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        Properties p=System.getProperties();String root=System.getenv("PPUM_ROOT");
        try(BufferedWriter w=Files.newBufferedWriter(out,StandardCharsets.UTF_8)){
            line(w,"timestamp_utc",Instant.now().toString());line(w,"os_name",p.getProperty("os.name"));line(w,"os_version",p.getProperty("os.version"));line(w,"os_arch",p.getProperty("os.arch"));
            line(w,"java_version",p.getProperty("java.version"));line(w,"java_vendor",p.getProperty("java.vendor"));line(w,"java_vm",p.getProperty("java.vm.name"));line(w,"available_processors",Runtime.getRuntime().availableProcessors());line(w,"jvm_max_heap_mib",Runtime.getRuntime().maxMemory()/1048576.0);
            java.lang.management.OperatingSystemMXBean osBean=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if(osBean instanceof com.sun.management.OperatingSystemMXBean extended){
                line(w,"physical_memory_mib",extended.getTotalMemorySize()/1048576.0);
            }
            line(w,"project_git_commit",gitCommit("."));
            line(w,"ppum_root",root==null?"":root);line(w,"upstream_git_commit",root==null?"":gitCommit(root));
            line(w,"jvm_input_arguments",String.join(" ",java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        }
        System.out.println("Wrote environment manifest: "+out.toAbsolutePath());
    }
    private static void line(BufferedWriter w,String k,Object v)throws IOException{w.write(k+"="+String.valueOf(v));w.newLine();}
    private static String gitCommit(String root){try{Process p=new ProcessBuilder("git","-C",root,"rev-parse","HEAD").redirectErrorStream(true).start();String s=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();return p.waitFor()==0?s:"UNAVAILABLE";}catch(Exception e){return "UNAVAILABLE";}}
}
