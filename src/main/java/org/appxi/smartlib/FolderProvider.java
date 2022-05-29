package org.appxi.smartlib;

import org.appxi.smartlib.dao.ItemsDao;

import java.util.List;

public final class FolderProvider extends AbstractProvider {
    public static final FolderProvider ONE = new FolderProvider();

    private FolderProvider() {
    }

    @Override
    public boolean isFolder() {
        return true;
    }

    @Override
    public String providerId() {
        return null;
    }

    @Override
    public String providerName() {
        return "目录";
    }

    public List<Item> getChildren(Item item) {
        return ItemsDao.items().list(item);
    }
}
