package org.appxi.smartlib.dao;

import org.appxi.smartlib.Item;
import org.springframework.data.solr.core.SolrTemplate;

import java.io.InputStream;
import java.nio.file.Path;

public final class ItemsDao {
    private static ItemRepository itemRepository;

    public static void setupInitialize(Path root) {
        itemRepository = null != itemRepository ? itemRepository : new ItemRepositoryImpl(root);
    }

    public static ItemRepository items() {
        return itemRepository;
    }

    public static PiecesRepository solrIndexes() {
        return BeansContext.getBean(PiecesRepository.class);
    }

    public static SolrTemplate solrTemplate() {
        return BeansContext.getBean(SolrTemplate.class);
    }

    public static void setContent(Item item, InputStream content, boolean reindex) throws Exception {
        final String msg = ItemsDao.items().setContent(item, content);
        if (msg != null) {
            throw new Exception(msg);
        }
        // update indexes
        if (reindex) {
            ItemsDao.items().reindex(item, (d, s) -> {
            });
        }
    }

}
