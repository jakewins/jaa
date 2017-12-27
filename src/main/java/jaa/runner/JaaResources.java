package jaa.runner;

import java.io.File;

public class JaaResources {
    public String javaHome() {
        // TODO, obviously
        return "/home/jake/Code/third/jdk8u/build/linux-x86_64-normal-server-fastdebug/jdk";
    }

    public File javaExecutable(String javaHome)
    {
        if(javaHome.endsWith("/")) {
            javaHome = javaHome.substring(0, javaHome.length() - 1);
        }
        // TODO Windows exe
        return new File(String.format("%s/bin/java", javaHome));
    }
}
