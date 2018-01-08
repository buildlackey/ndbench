package com.netflix.ndbench.test;

import com.google.common.collect.ImmutableList;
import org.junit.ClassRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import com.palantir.docker.compose.DockerComposeRule;
import org.junit.Test;

import java.io.File;


public class ElasticsearchIntegrationTest {
    private static final String dockerTomcatDeploymentDirOnHost = "/tmp/docker/webapps";


    @BeforeClass
    public static void initialize() throws Exception {
        if (DockerContainerHelper.disableDueToDockerExecutableUnavailability) {
            return;
        }
        DockerContainerHelper.initialize(ImmutableList.of("http://localhost:9200"));

        WarFileDeployer deployer = new WarFileDeployer(dockerTomcatDeploymentDirOnHost);
        String warFile = deployer.getWarFile("../../../../../ndbench-web/build/libs/");
        deployer.deployWarFile(new File(warFile));

        Thread.sleep(500 * 1000);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        //DockerContainerHelper.tearDown(docker, "elasticsearch");
    }

    @Test
    public void testDeployment() throws Exception {
        String warFile =
                new WarFileDeployer(dockerTomcatDeploymentDirOnHost ).
                        getWarFile("../../../../../ndbench-web/build/libs/");
        System.out.println("warFile:" + warFile);
    }
}


