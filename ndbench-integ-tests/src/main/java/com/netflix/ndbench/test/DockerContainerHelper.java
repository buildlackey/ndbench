package com.netflix.ndbench.test;




import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


import org.apache.commons.lang.StringUtils;

import java.util.concurrent.*;


import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.ImmutableDockerComposeRule;
import org.apache.commons.io.IOUtils;

/**
 * Supports integration testing by bringing up docker containers according to docker-compose specification.
 *
 * Since Docker and Docker-compose are not guaranteed to be available all test methods that use this class should
 * verify that {@link #disableDueToDockerExecutableUnavailability} is false before proceeding with their test
 * logic.
 *
 * This class enables integration tests to run such  that Docker container initialization can be short circuited
 * in favor of running a full Elasticsearch distribution locally, where such distribution is listening on standard ports
 * (9200 for REST and 9300 for transport.)
 * <p>
 * To suppress start up of Elasticsearch in Docker, set the environment variable  ES_NDBENCH_NO_DOCKER.
 * The main reason you would want to do this is because the current Docker configuration has some issues with
 * being run so that requests can be routed through an HTTP traffic proxy -- which is useful for debugging.
 */
public class DockerContainerHelper {
    private static final Logger logger = LoggerFactory.getLogger(DockerContainerHelper.class);

    private static volatile Boolean isCanceled = false;

    /**
     * Temporarily shut off mechanism to  detect if docker and docker-compose are not available or not. If these
     * are not available, then we will disable running integration / smoke tests. Docker, and (even more likely)
     * docker-compose may be unavailable in some  Jenkins and Travis CI environments.
     */
    public static volatile boolean disableDueToDockerExecutableUnavailability = false;

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

    public static ImmutableDockerComposeRule getDockerComposeRule(String serviceName) {
        if (disableDueToDockerExecutableUnavailability) {
            return null;
        }
        if (StringUtils.isNotEmpty(System.getenv("ES_NDBENCH_NO_DOCKER"))) {
            return null;
        }

        return DockerComposeRule.builder()
                .file("src/test/resources/docker-compose.yml")
                .projectName(ProjectName.random())
                .waitingForService(serviceName, HealthChecks.toHaveAllPortsOpen())
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
                        if (StringUtils.isNotEmpty(httpGet("http://localhost:9200"))) {
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

    // TODO - this is copied from EsUtils. would be nice to factor out to avoid repetition.
    private static String httpGet(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        OkHttpClient httpClient = new OkHttpClient();
        Response response = httpClient.newCall(request).execute();

        if (response.code() != 200 && response.code() != 201) {
            String message = "Unable to read data from " + url + " response code was: " + response.code();
            logger.error(message);
            throw new IOException(message);
        }

        return response.body().string();
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

    public static void tearDown(DockerComposeRule docker, String serviceName) throws Exception {
        if (disableDueToDockerExecutableUnavailability) {
            return;
        }
        if (StringUtils.isNotEmpty(System.getenv("ES_NDBENCH_NO_DOCKER"))) {
            return;
        }
        docker.containers().container(serviceName).stop();
    }


}
