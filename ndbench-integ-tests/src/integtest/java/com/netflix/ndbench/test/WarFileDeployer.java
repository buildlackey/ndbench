package com.netflix.ndbench.test;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class WarFileDeployer {
    private static final Logger logger = LoggerFactory.getLogger(WarFileDeployer.class);

    static DockerContainerHelper dockerContainerHelper = new DockerContainerHelper();
    private final String deploymentDirPath;

    private WarFileDeployer() {
        deploymentDirPath = null;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public WarFileDeployer(String deploymentDirPath) throws IOException, InterruptedException {
        DockerContainerHelper.prepareTomcatWebappsDirectory(deploymentDirPath);
        this.deploymentDirPath = deploymentDirPath;
    }


    public void deployWarFile(File warFile) {
        File destFile = new File(deploymentDirPath + "/ROOT.war");
        try {
            FileUtils.copyFile(warFile, destFile);
        } catch (IOException e) {
            throw new RuntimeException(
                    "copy from " + warFile.getAbsolutePath() + " to " + destFile.getAbsolutePath() + " failed", e);
        }
    }



    public String getWarFile(String relativePath) {
        File warDir = new File(getCompiledClassesDirLocation(), relativePath);
        for(final File fileEntry : warDir.listFiles()) {
            if (fileEntry.isFile() && fileEntry.getAbsolutePath().endsWith(".war")) {
                String retval = fileEntry.getAbsolutePath();
                logger.info("war file is: " + retval);
                return retval;
            }
        }

        throw new RuntimeException("no .war file found under " + warDir.getAbsolutePath());
    }


    private String getCompiledClassesDirLocation() {
        URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
        return location.getFile();
    }
}
