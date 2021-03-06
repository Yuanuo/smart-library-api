package org.appxi.smartlib.mindmap;

import org.apache.commons.io.IOUtils;
import org.appxi.smartlib.Item;
import org.appxi.smartlib.MetadataApi;
import org.appxi.smartlib.Searchable;
import org.appxi.smartlib.dao.ItemsDao;
import org.appxi.util.DigestHelper;
import org.appxi.util.StringHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public class MindmapDocument implements MetadataApi {
    static {
        JSONObject.writeSingleLine = false;
    }

    final Item item;
    private JSONObject document, metadata;

    public MindmapDocument(Item item) {
        this.item = item;
    }

    final JSONObject getMetadata() {
        if (null == metadata) getDocument();
        return metadata;
    }

    public final JSONObject getDocument() {
        if (null != this.document) return document;

        try (InputStream stream = ItemsDao.items().getContent(this.item)) {
            this.document = new JSONObject(IOUtils.toString(stream, StandardCharsets.UTF_8));
        } catch (Throwable ignore) {
        }
        if (null == this.document) this.document = new JSONObject();
        //
        if (!document.has("root")) document.put("root", new JSONObject("""
                {
                    "data": {
                        "id": "%s",
                        "created": %d,
                        "text": "Main Topic / 中心主题"
                    }
                }
                """.formatted(DigestHelper.uid(), System.currentTimeMillis())));
        if (!document.has("template")) document.put("template", "default");
        if (!document.has("theme")) document.put("theme", "fresh-blue");
        if (!document.has("version")) document.put("version", "21.8.18");

        if (document.has("metadata") && document.remove("metadata") instanceof JSONObject meta) this.metadata = meta;
        else this.metadata = new JSONObject();

        if (StringHelper.isBlank(metadata.optString("id"))) metadata.put("id", DigestHelper.uid62s());

        //
        item.attr(Searchable.class, Searchable.of(getMetadata("searchable", "all")));
        //
        return document;
    }

    public void setDocumentBody(String json) {
        try {
            this.document.clear();
            this.document = new JSONObject(json);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void save() throws Exception {
        this.save(true);
    }

    void save(boolean reindex) throws Exception {
        this.document.put("metadata", this.metadata);
        // fill id
        walkJson(root(), jsonData -> {
            if (!jsonData.has("id")) jsonData.put("id", DigestHelper.uid());
            if (!jsonData.has("created")) jsonData.put("created", System.currentTimeMillis());
        });
        //
        ItemsDao.setContent(this.item, new ByteArrayInputStream(this.document.toString(1).getBytes(StandardCharsets.UTF_8)), reindex);
    }

    JSONObject root() {
        return getDocument().getJSONObject("root");
    }

    String id() {
        return getMetadata().getString("id");
    }

    @Override
    public Item item() {
        return this.item;
    }

    @Override
    public List<String> getMetadata(String key) {
        JSONArray array = getMetadata().optJSONArray(key);
        if (null == array) array = new JSONArray();
        return new ArrayList<>(array.toList().stream().map(v -> v.toString().strip()).filter(v -> !v.isBlank()).distinct().sorted().toList());
    }

    @Override
    public String getMetadata(String key, String defaultValue) {
        JSONArray array = getMetadata().optJSONArray(key);
        return (null == array || array.isEmpty()) ? defaultValue : array.optString(0, defaultValue);
    }

    @Override
    public void setMetadata(String key, String value) {
        JSONArray array = getMetadata().optJSONArray(key);
        if (null == array) getMetadata().put(key, array = new JSONArray());
        array.clear();
        array.put(value);
    }

    @Override
    public void addMetadata(String key, String value) {
        JSONArray array = getMetadata().optJSONArray(key);
        if (null == array) getMetadata().put(key, array = new JSONArray());
        array.put(value);
    }

    @Override
    public void removeMetadata(String key) {
        getMetadata().remove(key);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    List<MindmapDocument> toSearchableDocuments() {
        return List.of(this);
    }

    String getDocumentText() {
        try {
            StringBuilder buff = new StringBuilder();
            walkJson(root(), json -> buff.append(json.optString("text")).append("\n"));
            return buff.toString();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    LinkedHashMap<String, String> getTagged(String... tags) {
        final LinkedHashMap<String, String> result = new LinkedHashMap<>();
        walkJson(root(), jsonData -> {
            JSONArray arr = jsonData.optJSONArray("resource");
            if (null == arr || arr.isEmpty()) return;
            for (int i = 0; i < arr.length(); i++) {
                if (arr.opt(i) instanceof String str && StringHelper.indexOf(str, tags)) {
                    result.put(jsonData.optString("id"), jsonData.optString("text"));
                    break;
                }
            }
        });
        return result;
    }

    LinkedHashSet<String> getTaggedUnique(String... tags) {
        return new LinkedHashSet<>(getTagged(tags).values());
    }

    void walkJson(JSONObject json, Consumer<JSONObject> consumer) {
        JSONObject data = json.optJSONObject("data");
        JSONArray resArr = null == data ? null : data.optJSONArray("resource");
        if (null != data && (null == resArr || !resArr.has("exclude") && !resArr.has("excludes")))
            consumer.accept(data);

        if (null != resArr && resArr.has("excludes")) return;

        JSONArray array = json.optJSONArray("children");
        if (null != array && !array.isEmpty()) {
            for (int i = 0; i < array.length(); i++) {
                walkJson(array.optJSONObject(i), consumer);
            }
        }
    }
}
