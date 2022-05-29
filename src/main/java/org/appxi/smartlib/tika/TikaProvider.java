package org.appxi.smartlib.tika;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.appxi.prefs.UserPrefs;
import org.appxi.search.solr.Piece;
import org.appxi.smartlib.AbstractProvider;
import org.appxi.smartlib.Item;
import org.appxi.smartlib.dao.ItemsDao;
import org.appxi.smartlib.html.HtmlRepairer;
import org.appxi.util.DigestHelper;
import org.appxi.util.FileHelper;
import org.appxi.util.StringHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class TikaProvider extends AbstractProvider {
    @Override
    public final boolean isFolder() {
        return false;
    }

    @Override
    public List<Piece> indexing(Item item) {
        List<Piece> result = super.indexing(item);
        //
        Piece piece = result.get(0);
        piece.type = "article";
        piece.text("text_txt_aio", extractText(item));
        //
        return result;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public final String extractText(Item item) {
        try (InputStream stream = ItemsDao.items().getContent(item)) {
            Metadata metadata = new Metadata();
            return new Tika().parseToString(stream, metadata, -1);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return "无内容";
    }

    public String toViewableHtmlDocument(Item item, Supplier<Element> bodySupplier, Function<Element, Object> bodyWrapper, String... includes) {
        Element body = null != bodySupplier ? bodySupplier.get() : null;
        if (null == body) {
            Document document = Jsoup.parse("");
            body = document.body();
            String extractedText = this.extractText(item);
            extractedText = extractedText.lines().map(s -> "<p>".concat(s).concat("</p>")).collect(Collectors.joining());
            body.html(extractedText);
        }
        // fill id for elements
        body.traverse(new HtmlRepairer(1).withMost());
        //
        final StringBuilder buff = new StringBuilder();
        buff.append("<!DOCTYPE html><html lang=\"");
//        buff.append(body == body() ? getTextLocale().lang : "en");
        buff.append(Locale.getDefault().getLanguage());
        buff.append("\"><head><meta charset=\"UTF-8\">");
        //
        final List<String> scripts = new ArrayList<>(), styles = new ArrayList<>();
        for (String include : includes) {
            if (include.endsWith(".js")) {
                buff.append("\r\n<script type=\"text/javascript\" src=\"").append(include).append("\"></script>");
            } else if (include.endsWith(".css")) {
                buff.append("\r\n<link rel=\"stylesheet\" href=\"").append(include).append("\"/>");
            } else if (include.startsWith("<script") || include.startsWith("<style")
                    || include.startsWith("<link") || include.startsWith("<meta")) {
                buff.append("\r\n").append(include);
            } else if (include.startsWith("var ") || include.startsWith("function")) {
                scripts.add(include);
            } else {
                styles.add(include);
            }
        }
        if (!scripts.isEmpty()) {
            buff.append("\r\n<script type=\"text/javascript\">").append(StringHelper.joinLines(scripts)).append("</script>");
        }
        if (!styles.isEmpty()) {
            buff.append("\r\n<style type=\"text/css\">").append(StringHelper.joinLines(styles)).append("</style>");
        }
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

    private static final String VERSION = "22.01.10";

    public String toViewableHtmlFile(Item item, Supplier<Element> bodySupplier, Function<Element, Object> bodyWrapper, String... includes) {
        final StringBuilder cacheInfo = new StringBuilder();
        cacheInfo.append(ItemsDao.items().getIdentificationInfo(item));
        cacheInfo.append(VERSION);
//        cacheInfo.append(hanLang.lang);
        cacheInfo.append(StringHelper.join("|", includes));
        final String cachePath = StringHelper.concat(".tmp.", DigestHelper.md5(cacheInfo.toString()), ".html");
        final Path cacheFile = UserPrefs.dataDir().resolve(".temp").resolve(cachePath);
        if (Files.notExists(cacheFile)) {
            final String stdHtmlDoc = this.toViewableHtmlDocument(item, bodySupplier, bodyWrapper, includes);
            final boolean success = FileHelper.writeString(stdHtmlDoc, cacheFile);
            if (success) {
//                DevtoolHelper.LOG.info("Cached : " + cacheFile.toAbsolutePath());
            } else throw new RuntimeException("cannot cache stdHtmlDoc");// for debug only
        }
        return cacheFile.toAbsolutePath().toString();
    }
}
