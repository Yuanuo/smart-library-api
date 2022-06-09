package org.appxi.smartlib.mindmap;

import org.appxi.smartlib.Item;

public class MindmapOldProvider extends MindmapProvider {
    public static final MindmapOldProvider ONE = new MindmapOldProvider();

    private MindmapOldProvider() {
    }

    @Override
    public String providerId() {
        return "km.json";
    }

    @Override
    public String providerName() {
        return "脑图（旧版）";
    }

    @Override
    public void touching(Item item) {
    }
}
