package org.appxi.smartlib.dao;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.core.NodeConfig;
import org.appxi.search.solr.Piece;
import org.appxi.util.FileHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;

import java.nio.file.Path;
import java.util.Set;

@Configuration
@EnableSolrRepositories("org.appxi.smartlib.dao")
class BeansConfig {
    static Path solrHome, confHome;

    @Bean
    SolrClient solrClient() throws Exception {
        System.setProperty("solr.dns.prevent.reverse.lookup", "true");
        System.setProperty("solr.install.dir", solrHome.toString());

        FileHelper.makeDirs(solrHome);
        final NodeConfig config = new NodeConfig.NodeConfigBuilder(Piece.REPO, solrHome)
                .setConfigSetBaseDirectory(confHome.toString())
                .setAllowPaths(Set.of(Path.of("_ALL_"), solrHome))
                .build();

        final EmbeddedSolrServer solrClient = new EmbeddedSolrServer(config, Piece.REPO);
        if (null != solrClient.getCoreContainer() && solrClient.getCoreContainer().getCoreDescriptor(Piece.REPO) == null) {
            final CoreAdminRequest.Create createRequest = new CoreAdminRequest.Create();
            createRequest.setCoreName(Piece.REPO);
            createRequest.setConfigSet(Piece.REPO);
            solrClient.request(createRequest);
        }

        return solrClient;
    }

    @Bean
    SolrTemplate solrTemplate(SolrClient client) throws Exception {
        return new SolrTemplate(client);
    }
}
