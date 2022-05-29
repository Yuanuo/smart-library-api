package org.appxi.smartlib.article;

import org.appxi.search.solr.Piece;
import org.appxi.smartcn.pinyin.PinyinHelper;
import org.appxi.smartlib.AbstractProvider;
import org.appxi.smartlib.Item;
import org.appxi.smartlib.ItemHelper;
import org.appxi.smartlib.Searchable;
import org.appxi.smartlib.html.HtmlHelper;
import org.appxi.util.DigestHelper;
import org.appxi.util.NumberHelper;
import org.appxi.util.StringHelper;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public final class ArticleProvider extends AbstractProvider {
    public static final ArticleProvider ONE = new ArticleProvider();

    private ArticleProvider() {
    }

    @Override
    public String providerId() {
        return "html";
    }

    @Override
    public String providerName() {
        return "图文";
    }

    @Override
    public boolean isFolder() {
        return false;
    }

    @Override
    public String toCleanName(String itemName) {
        return ItemHelper.getNameWithoutProvider(itemName, providerId());
    }

    @Override
    public void touching(Item item) {
        ArticleDocument document = new ArticleDocument(item);
        //
        if (document.getMetadata("library").isEmpty()) document.setMetadata("library", "unknown");
        //
        if (document.getMetadata("catalog").isEmpty()) document.setMetadata("catalog", "unknown");
        //
        if (document.getMetadata("period").isEmpty()) document.setMetadata("period", "unknown");
        //
        if (document.getMetadata("author").isEmpty()) document.setMetadata("author", "unknown");

        HtmlHelper.inlineFootnotes(document.body());

        try {
            document.save(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Piece> indexing(Item item) {
        final ArticleDocument articleDocument = new ArticleDocument(item);
        final Searchable searchable = articleDocument.getSearchable();
        if (searchable == Searchable.exclude) return null;
        // prepare common parts
        final Piece mainPiece = Piece.of();
        mainPiece.provider = providerId();
        mainPiece.path = item.getPath();
        mainPiece.type = "article";
        //
        String sequence = null;
        Matcher matcher = ItemHelper.P_INDEXED_NAME.matcher(item.getName());
        if (matcher.matches()) {
            sequence = matcher.group(1);
            mainPiece.title = matcher.group(3);
        } else mainPiece.title = item.getName();
        //
        List<String> metaList = articleDocument.getMetadata("library");
        if (metaList.isEmpty()) addCategories(mainPiece, "library", "unknown");
        else metaList.forEach(v -> addCategories(mainPiece, "library", v));
        //
        metaList = articleDocument.getMetadata("catalog");
        if (metaList.isEmpty()) addCategories(mainPiece, "catalog", "unknown");
        else metaList.forEach(v -> addCategories(mainPiece, "catalog", v));
        //
        metaList = articleDocument.getMetadata("period");
        if (metaList.isEmpty()) addCategories(mainPiece, "period", "unknown");
        else metaList.forEach(v -> addCategories(mainPiece, "period", v));
        //
        metaList = articleDocument.getMetadata("author");
        if (metaList.isEmpty()) {
            addCategories(mainPiece, "author", "unknown");
            mainPiece.field("authors_s", "unknown");
        } else {
            metaList.forEach(v -> addCategories(mainPiece, "author", v));
            mainPiece.field("authors_s", metaList.stream().map(v -> v.split("[\s　]")[0])
                    .collect(Collectors.joining("; ")));
        }
        //
        Optional.ofNullable(articleDocument.getMetadata("priority", null))
                .ifPresent(v -> mainPiece.priority = NumberHelper.toDouble(v, 5));
        Optional.ofNullable(articleDocument.getMetadata("sequence", sequence))
                .ifPresent(v -> mainPiece.field("sequence_s", v));
        //
        final List<Piece> result = new ArrayList<>();
        final List<ArticleDocument> documents = articleDocument.toSearchableDocuments();
        final boolean articleDocumentOnly = documents.size() == 1 && documents.get(0) == articleDocument;
        //
        for (int j = 0; j < documents.size(); j++) {
            ArticleDocument document = documents.get(j);
            Piece piece = mainPiece.clone();
            // detect topic title
            final Elements headings = document.body().select(HtmlHelper.headings);
            if (articleDocumentOnly) {
                piece.id = articleDocument.html().id();
                piece.title = mainPiece.title;
            } else {
                piece.id = DigestHelper.uid62s();
                piece.type = "topic";
                piece.title = "<HR but no HEADING>";
                Optional.ofNullable(headings.first())
                        .ifPresent(h -> piece.setTitle(h.text().strip()).field("anchor_s", h.id()));
                if (j == 0) {
                    if (mainPiece.title.startsWith(piece.title) || mainPiece.title.endsWith(piece.title))
                        piece.setTitle(mainPiece.title).setType("article");
                    else if (piece.title.startsWith(mainPiece.title) || piece.title.endsWith(mainPiece.title))
                        piece.setType("article");
                    else result.add(createPiece(mainPiece.path, null, mainPiece.title, "topic"));
                }
            }
            //
            piece.field("title_txt_aio", piece.title);
            piece.field("title_txt_en", PinyinHelper.pinyin(piece.title));

            if (searchable == Searchable.all) {
                piece.text("text_txt_aio", document.getDocumentText());
            }
            //
            //
            result.add(piece);

            for (int i = 0; i < headings.size(); i++) {
                Element head = headings.get(i);
                String headText = head.text().strip();
                if (headText.isBlank()) continue;
                if (i == 0 && (piece.title.endsWith(headText) || headText.endsWith(piece.title))) continue;
                result.add(createPiece(piece.path, head.id(), headText, "label"));
            }
        }

        return result;
    }

    private Piece createPiece(String path, String anchor, String title, String type) {
        Piece piece = Piece.of();
        piece.provider = providerId();
        piece.id = DigestHelper.uid62s();
        piece.type = type;
        piece.path = path;
        if (null != anchor) {
            piece.field("anchor_s", anchor);
        }
        piece.title = title;
        piece.field("title_txt_aio", title);
        piece.field("title_txt_en", PinyinHelper.pinyin(title));
        return piece;
    }

    private static void addCategories(Piece piece, String group, String path) {
        if (StringHelper.isBlank(path))
            return;
        final String[] names = path.replace("//", "/").split("/");
        final List<String> paths = StringHelper.getFlatPaths(StringHelper.join("/", names));
        final String grouped = StringHelper.concat(".categories/", group, "/");
        paths.forEach(s -> piece.categories.add(grouped.concat(s)));
    }
}
