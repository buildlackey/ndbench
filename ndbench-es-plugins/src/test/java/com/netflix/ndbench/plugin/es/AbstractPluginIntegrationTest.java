package com.netflix.ndbench.plugin.es;

import com.netflix.ndbench.api.plugin.DataGenerator;
import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.ImmutableDockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Enables integration tests to run such  that Docker container initialization can be short circuited in favor
 * of running a full Elasticsearch distribution locally, where such distribution is listening on standard ports
 * (9200 for REST and 9300 for transport.)
 * <p>
 * To suppress start up of Elasticsearch in Docker, set the environment variable  ES_NDBENCH_NO_DOCKER.
 * The main reason you would want to do this is because the current Docker configuration has some issues with
 * being run so that requests can be routed through an HTTP traffic proxy -- which is useful for debugging.
 */
public class AbstractPluginIntegrationTest extends AbstractPluginTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractPluginIntegrationTest.class);

    static DockerContainerHelper dockerContainerHelper = new DockerContainerHelper();

    @ClassRule
    public static DockerComposeRule docker = dockerContainerHelper.getDockerComposeRule();

    protected static DataGenerator alwaysSameValueGenerator = new DataGenerator() {
        @Override
        public String getRandomString() {
            return "hello";
        }

        @Override
        public String getRandomValue() {
            return "hello";
        }

        @Override
        public Integer getRandomInteger() {
            return 1;
        }

        @Override
        public Integer getRandomIntegerValue() {
            return 1;
        }
    };


    static EsRestPlugin getPlugin(String forcedHostName,
                                  String indexName,
                                  boolean isBulkWrite,
                                  int indexRollsPerDay,
                                  int portNum) throws Exception {
        IEsConfig config =
                getConfig(portNum, forcedHostName, indexName, isBulkWrite, 0f, indexRollsPerDay);
        EsRestPlugin plugin =
                new EsRestPlugin(
                        getCoreConfig(0, false, 60, 10, 10, 0.01f),
                        config,
                        new MockServiceDiscoverer(9200),
                        false);
        plugin.init(alwaysSameValueGenerator);
        return plugin;
    }
}

