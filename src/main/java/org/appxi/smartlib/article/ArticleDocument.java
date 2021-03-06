package org.appxi.smartlib.article;

import org.appxi.prefs.UserPrefs;
import org.appxi.smartlib.Item;
import org.appxi.smartlib.MetadataApi;
import org.appxi.smartlib.Searchable;
import org.appxi.smartlib.dao.ItemsDao;
import org.appxi.smartlib.html.HtmlHelper;
import org.appxi.smartlib.html.HtmlRepairer;
import org.appxi.util.DigestHelper;
import org.appxi.util.FileHelper;
import org.appxi.util.NumberHelper;
import org.appxi.util.StringHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeFilter;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

public class ArticleDocument implements MetadataApi {
    private static final String VERSION = "21.08.30";

    final Item item;
    private Document document;
    private long edition = 1;

    public ArticleDocument(Item item) {
        this.item = item;
    }

    private ArticleDocument(Item item, Document document) {
        this.item = item;
        this.document = document;
    }

    public Document getDocument() {
        if (null != this.document) return this.document;

        try (InputStream stream = ItemsDao.items().getContent(this.item)) {
            InputStream stream1 = stream;
            if (null == stream1 || stream1.available() < 160)
                stream1 = new ByteArrayInputStream("<!doctype html><html><head><meta charset=\"utf-8\"></head><body><p></p></body></html>".getBytes());
            document = Jsoup.parse(stream1, StandardCharsets.UTF_8.name(), "/", Parser.htmlParser());
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (null == this.document) this.document = Jsoup.parse("");
        //
        Element html = html();
        if (html.id().isBlank()) html.id(DigestHelper.uid62s());
        edition = NumberHelper.toLong(html.attr("data-ver"), 1);

        //
        item.attr(Searchable.class, Searchable.of(getMetadata("searchable", "all")));
        //
        return document;
    }

    public ArticleDocument setDocumentBody(String html) {
        this.body().replaceWith(Jsoup.parse(html).body());

        return this;
    }

    public ArticleDocument save() throws Exception {
        return this.save(true);
    }

    public ArticleDocument save(boolean reindex) throws Exception {
        // fill id for elements
        final HtmlRepairer htmlRepairer = new HtmlRepairer(edition).withMost();
        this.body().traverse(htmlRepairer);
        //
        this.html().attr("data-ver", String.valueOf(htmlRepairer.edition()));

        ItemsDao.setContent(this.item, new ByteArrayInputStream(this.document.outerHtml().getBytes(StandardCharsets.UTF_8)), reindex);

        return this;
    }

    public Element root() {
        return this.getDocument();
    }

    public Element html() {
        return this.getDocument().selectFirst("> html");
    }

    public Element head() {
        return this.getDocument().head();
    }

    public Element body() {
        return this.getDocument().body();
    }

    @Override
    public Item item() {
        return this.item;
    }

    @Override
    public List<String> getMetadata(String key) {
        final Elements metas = this.head().select("> meta[".concat(key).concat("]"));
        return metas.stream().map(v -> v.attr(key).strip()).filter(v -> !v.isBlank()).distinct().sorted().toList();
    }

    @Override
    public String getMetadata(String key, String defaultValue) {
        final Element ele = this.head().selectFirst("> meta[".concat(key).concat("]"));
        return null == ele ? defaultValue : ele.attrOr(key, defaultValue);
    }

    @Override
    public void setMetadata(String key, String value) {
        Element ele = this.head().selectFirst("> meta[".concat(key).concat("]"));
        if (null == ele) ele = this.head().appendElement("meta");
        ele.attr(key, value);
    }

    @Override
    public void addMetadata(String key, String value) {
        this.head().appendElement("meta").attr(key, value);
    }

    @Override
    public void removeMetadata(String key) {
        this.head().select("> meta[".concat(key).concat("]")).remove();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public String toViewableHtmlDocument(Supplier<Element> bodySupplier, Function<Element, Object> bodyWrapper, String... includes) {
        final Element body = null != bodySupplier ? bodySupplier.get() : body();
        //
        final StringBuilder buff = new StringBuilder();
        buff.append("<!DOCTYPE html><html lang=\"");
//        buff.append(body == body() ? getTextLocale().lang : "en");
        buff.append(Locale.getDefault().getLanguage());
        buff.append("\"><head><meta charset=\"UTF-8\">");
        //
        StringHelper.buildWebIncludes(buff, List.of(includes));
        //
        buff.append("</head>");
        if (null == bodyWrapper) {
            buff.append(body.outerHtml());
        } else {
            final Object bodyWrapped = bodyWrapper.apply(body);
            if (bodyWrapped instanceof Node node) {
                buff.append(node.outerHtml());
            } else {
                final String bodyHtml = bodyWrapped.toString();
                if (bodyHtml.startsWith("<body"))
                    buff.append(bodyHtml);
                else buff.append("<body>").append(bodyHtml).append("</body>");
            }
        }
        buff.append("</html>");
        return buff.toString();
    }

    public String toViewableHtmlFile(Supplier<Element> bodySupplier, Function<Element, Object> bodyWrapper, String... includes) {
        final StringBuilder cacheInfo = new StringBuilder();
        cacheInfo.append(ItemsDao.items().getIdentificationInfo(this.item));
        cacheInfo.append(VERSION);
//        cacheInfo.append(hanLang.lang);
        cacheInfo.append(StringHelper.join("|", includes));
        final String cachePath = StringHelper.concat(".tmp.", DigestHelper.md5(cacheInfo.toString()), ".html");
        final Path cacheFile = UserPrefs.dataDir().resolve(".temp").resolve(cachePath);
        if (Files.notExists(cacheFile)) {
            final String stdHtmlDoc = this.toViewableHtmlDocument(bodySupplier, bodyWrapper, includes);
            final boolean success = FileHelper.writeString(stdHtmlDoc, cacheFile);
            if (success) {
//                DevtoolHelper.LOG.info("Cached : " + cacheFile.toAbsolutePath());
            } else throw new RuntimeException("cannot cache stdHtmlDoc");// for debug only
        }
        return cacheFile.toAbsolutePath().toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public List<ArticleDocument> toSearchableDocuments() {
        final List<ArticleDocument> result = new ArrayList<>();
        final List<List<Element>> lists = new ArrayList<>();

        final Document tmpDocument = getDocument().clone();
        final Element tmpBody = tmpDocument.body();

        Elements children = tmpBody.children();
        int fromIndex = 0;
        for (int toIndex = 0; toIndex < children.size(); toIndex++) {
            Element element = children.get(toIndex);
            if ("p".equals(element.tagName()) && element.hasAttr("data-pb")) {
                if (fromIndex == toIndex) continue;
                lists.add(new ArrayList<>(children.subList(fromIndex, toIndex)));
                fromIndex = toIndex + 1;
            }
        }
        if (fromIndex < children.size())
            lists.add(new ArrayList<>(children.subList(fromIndex, children.size())));

        for (int i = 1; i < lists.size(); i++) {
            List<Element> list = lists.get(i);
            if (list.stream().takeWhile(ele -> HtmlHelper.headingTags.contains(ele.tagName())).findAny().isEmpty()) {
                lists.get(i - 1).addAll(list);
                lists.remove(i--);
            }
        }

        if (lists.size() <= 1) return List.of(this);

        lists.forEach(list -> list.forEach(Node::remove));
        tmpBody.text("");// clear body for clone
        lists.forEach(list -> {
            Document doc = tmpDocument.clone();
            doc.body().appendChildren(list);
            result.add(new ArticleDocument(item, doc));
        });
        return result;
    }

    String getDocumentText() {
        this.body().filter(((node, depth) -> {
            if (node instanceof Element ele) {
                if (ele.attrIs("data-searchable", "exclude"))
                    return NodeFilter.FilterResult.SKIP_ENTIRELY;
            }
            return NodeFilter.FilterResult.CONTINUE;
        }));
        return this.body().text();
    }
}
