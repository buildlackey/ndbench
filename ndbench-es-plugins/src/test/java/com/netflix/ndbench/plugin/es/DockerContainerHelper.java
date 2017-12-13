package com.netflix.ndbench.plugin.es;


import com.netflix.governator.guice.test.ModulesForTesting;
import com.netflix.governator.guice.test.junit4.GovernatorJunit4ClassRunner;
import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;


import com.netflix.ndbench.api.plugin.DataGenerator;
import java.io.IOException;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.ImmutableDockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supports integration testing by bringing up docker containers according to docker-compose specification.
 *
 * Allows integration tests to run such  that Docker container initialization can be short circuited in favor
 * of running a full Elasticsearch distribution locally, where such distribution is listening on standard ports
 * (9200 for REST and 9300 for transport.)
 * <p>
 * To suppress start up of Elasticsearch in Docker, set the environment variable  ES_NDBENCH_NO_DOCKER.
 * The main reason you would want to do this is because the current Docker configuration has some issues with
 * being run so that requests can be routed through an HTTP traffic proxy -- which is useful for debugging.
 */
public class DockerContainerHelper {
    static final String ELASTICSEARCH = "elasticsearch";
    private static final Logger logger = LoggerFactory.getLogger(DockerContainerHelper.class);

    private static volatile Boolean isCanceled = false;

    /**
     * Temporarily shut off mechanism to  detect if docker and docker-compose are not available or not. If these
     * are not available, then we will disable running integration / smoke tests. Docker, and (even more likely)
     * docker-compose may be unavailable in some  Jenkins and Travis CI environments.
     */
    protected static boolean disableDueToDockerExecutableUnavailability = false;

    static {
        verifyAvailabilityOfExecutable("docker");
        verifyAvailabilityOfExecutable("docker-compose --help");
    }

    private static void verifyAvailabilityOfExecutable(String command) {
        try {
            Process proc = Runtime.getRuntime().exec(command);
            int exitCode = proc.waitFor();
            String processOutput = IOUtils.toString(proc.getInputStream());
            if (exitCode != 0 || ! processOutput.contains("docker") ) {
                String errOutput = IOUtils.toString(proc.getErrorStream());
                logger.error(errOutput);
                disableDueToDockerExecutableUnavailability = true;
            }
        } catch (Exception e) {
            disableDueToDockerExecutableUnavailability = true;
        }
    }

    static ImmutableDockerComposeRule getDockerComposeRule() {
        if (disableDueToDockerExecutableUnavailability) {
            return null;
        }
        if (StringUtils.isNotEmpty(System.getenv("ES_NDBENCH_NO_DOCKER"))) {
            return null;
        }

        return DockerComposeRule.builder()
                .file("src/test/resources/docker-compose-elasticsearch.yml")
                .projectName(ProjectName.random())
                .waitingForService(ELASTICSEARCH, HealthChecks.toHaveAllPortsOpen())
                .build();
    }


    /**
     * This check is required because DockerComposeRule .waitingForService() checks don't seem to wait for
     * connections to be establishable on port 9200. There is probably some way
     * to configure this, but this quick and dirty test does the job for now.
     * <p>
     * Note: This is similar to check code in test for transport protocol: could be refactored.
     */
    private static FutureTask<Boolean> getCheckServerUpTask(String restEndpoint) {
        return new FutureTask<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                while (! isCanceled ) {
                    logger.info("Checking if we can connect to elasticsearch (IGNORE EXCEPTIONS, PLEASE)");
                    try {
                        if (StringUtils.isNotEmpty(EsUtils.httpGet("http://localhost:9200"))) {
                            logger.info("connection to elasticsearch succeeded !");
                            return true;           // success -- break out of loop
                        }
                    } catch (Exception ignored) {
                    }
                    Thread.sleep(100);  // yes. it is an extra 1/10th of a second in happy path. but less code this way.
                }
                return false;
            }
        });
    }


    public static void initialize() throws Exception {
        if (disableDueToDockerExecutableUnavailability) {
            return;
        }
        if (StringUtils.isNotEmpty(System.getenv("ES_NDBENCH_NO_DOCKER"))) {
            return;
        }

        ExecutorService execSvc = Executors.newSingleThreadExecutor();
        FutureTask<Boolean> checkServerUpTask = getCheckServerUpTask("http://localhost:9200");
        execSvc.submit(checkServerUpTask);
        checkServerUpTask.get(40, TimeUnit.SECONDS);
        isCanceled = true;
        checkServerUpTask.cancel(true);
        execSvc.shutdownNow();
    }

    public static void tearDown(DockerComposeRule docker) throws Exception {
        if (disableDueToDockerExecutableUnavailability) {
            return;
        }
        if (StringUtils.isNotEmpty(System.getenv("ES_NDBENCH_NO_DOCKER"))) {
            return;
        }
        docker.containers().container(ELASTICSEARCH).stop();
    }


}
