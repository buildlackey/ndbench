package com.netflix.ndbench.test;




import com.google.common.collect.ImmutableList;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;


import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.concurrent.*;


import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.ImmutableDockerComposeRule;
import org.apache.commons.io.IOUtils;

/**
 * Supports integration testing by bringing up Docker containers according to a docker-compose specification.
 *
 * Since Docker and Docker-compose are not guaranteed to be available in all environments,  any test method
 * that use this class should verify that {@link #disableDueToDockerExecutableUnavailability} is
 * false before proceeding with its test logic.
 *
 * This class enables integration tests to run such  that Docker container initialization can be short circuited
 * in favor of running a locally installed distributions of whatever datastore Docker image is being benchmarked.
 * <p>
 * To suppress start up of your datastore container in Docker, set the environment variable  ES_NDBENCH_NO_DOCKER.
 * The main reason you would want to do this is because the current Docker configuration has some issues with
 * being run so that requests can be routed through an HTTP traffic proxy -- which is useful for debugging.
 * For example, if your tests targeted Elasticsearch you might want to launch Elasticsearch listening
 * on its standard port, 9200, then set up an Http traffic sniffer listening on port 5555 and proxying
 * through to 9200. Then you would set up ndbench.config.es.restClientPort to 5555.
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


    private static  List<String> startAndWaitForExternalCommand(String command)
            throws InterruptedException, IOException {
        Process proc = Runtime.getRuntime().exec(command);
        int exitCode = proc.waitFor();
        return ImmutableList.of(
                Integer.toString(exitCode),                 // process return code
                IOUtils.toString(proc.getInputStream()),    // process stdout
                IOUtils.toString(proc.getErrorStream()));   // process stderr
    }

    private static void verifyAvailabilityOfExecutable(String command) {
        try {

            List<String> retCodeStdoutStderr = startAndWaitForExternalCommand(command);
            if (! "0".equals(retCodeStdoutStderr.get(0)) || ! retCodeStdoutStderr.get(1).contains("docker") ) {
                logger.error(retCodeStdoutStderr.get(2));
                disableDueToDockerExecutableUnavailability = true;
            }
        } catch (Exception e) {
            disableDueToDockerExecutableUnavailability = true;
        }
    }

    public static void prepareTomcatWebappsDirectory(String path) throws IOException, InterruptedException {
        List<String> result = startAndWaitForExternalCommand("sudo rm -rf  " + path);
        assert result.get(0).equals("0");
        File webappsDir = new File(path);
        webappsDir.mkdirs();
        assert webappsDir.exists();
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
     * connections to be establishable on port of services referenced by 'waitingForService'.
     * There is probably some way to configure this, but this quick and dirty test does the job for now.
     */
    private static FutureTask<Boolean> getCheckServerUpTask(List<String> restEndpoints) {
        return new FutureTask<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                while (! isCanceled ) {
                    int goodConnectionCount  = 0;
                    logger.info("Checking if we can connect to endpoints: " + restEndpoints);
                    try {
                        for (String endpoint : restEndpoints) {
                            if (StringUtils.isNotEmpty(httpGet(endpoint))) {
                                logger.info("good connection to " + endpoint);
                                goodConnectionCount  += 1;
                            }
                        }

                    } catch (Exception ignored) {
                    }
                    assert goodConnectionCount < restEndpoints.size() + 1;
                    if (goodConnectionCount == restEndpoints.size() ) {
                        return true;           // success -- break out of loop
                    }
                    Thread.sleep(100);  // yes. it is an extra 1/10th of a second in happy path. but less code this way.
                }
                return false;
            }
        });
    }


    private static FutureTask<Boolean> launchDockerComposeTask() throws IOException {
        File dir = new File("/home/chris/dev/ndbench/ndbench-integ-tests/src/integtest/resources");

        Process proc = Runtime.getRuntime().exec("docker-compose up", null, dir);

        return new FutureTask<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                while (! isCanceled ) {
                    if (! proc.isAlive()) {
                        logger.info("Process is not alive: " + IOUtils.toString(proc.getErrorStream()));
                        return false;
                    }

                    InputStream stdOutAsOurInput = proc.getInputStream();
                    BufferedReader stdout = new BufferedReader(new InputStreamReader(stdOutAsOurInput));
                    String line = stdout.readLine();
                    System.out.println("docker-compose: " + line);
                    Thread.sleep(100);  // yes. it is an extra 1/10th of a second in happy path. but less code this way.
                }
                return false;
            }
        });
    }


    public static void initialize(List<String> restEndpointsToCheck) throws Exception {
        if (disableDueToDockerExecutableUnavailability) {
            return;
        }
        if (StringUtils.isNotEmpty(System.getenv("ES_NDBENCH_NO_DOCKER"))) {
            return;
        }

        ExecutorService execSvc2 = Executors.newSingleThreadExecutor();
        FutureTask<Boolean> task2 = launchDockerComposeTask();
        execSvc2.submit(task2);
        Thread.sleep(500);  // yes. it is an extra 1/10th of a second in happy path. but less code this way.



        ExecutorService execSvc = Executors.newSingleThreadExecutor();
        FutureTask<Boolean> checkServerUpTask = getCheckServerUpTask(restEndpointsToCheck);
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


}
