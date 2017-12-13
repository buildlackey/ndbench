package com.netflix.ndbench.plugin.es;


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

public class DockerContainerHelper {
    static final String ELASTICSEARCH = "elasticsearch";
    private static final Logger logger = LoggerFactory.getLogger(DockerContainerHelper.class);
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

}
