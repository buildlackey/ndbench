package com.netflix.ndbench.plugin.es;

import com.google.common.collect.ImmutableList;
import com.netflix.ndbench.api.plugin.DataGenerator;
import com.netflix.ndbench.test.DockerContainerHelper;
import com.palantir.docker.compose.DockerComposeRule;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AbstractPluginIntegrationTest extends AbstractPluginTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractPluginIntegrationTest.class);

    static DockerContainerHelper dockerContainerHelper = new DockerContainerHelper();


    @ClassRule
    public static DockerComposeRule docker = dockerContainerHelper.getDockerComposeRule("elasticsearch");



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

