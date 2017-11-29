package com.netflix.foo.ba.a;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URL;


public class ConfigurationPropertiesTest {
    static {
        System.setProperty("ndbench.config.numKeys", "777");
    }

    @Test
    public void testInvokingProcessMethodOnWriteOperationSetsNewRateLimit() throws Exception {
        String warFile = getWarFile();
        System.out.println("warFile:" + warFile);
    }

    private String getWarFile() {
        File warDir = new File(getCompiledClassesDirLocation(), "../../../../../ndbench-web/build/libs/");
        for(final File fileEntry : warDir.listFiles()) {
            if (fileEntry.isFile() && fileEntry.getAbsolutePath().endsWith(".war")) {
                return fileEntry.getAbsolutePath();
            }
        }

        throw new RuntimeException("no .war file found under " + warDir.getAbsolutePath());
    }


    private String getCompiledClassesDirLocation() {
        URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
        return location.getFile();
    }
}


