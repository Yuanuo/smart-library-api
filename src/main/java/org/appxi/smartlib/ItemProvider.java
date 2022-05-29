package org.appxi.smartlib;

import org.appxi.search.solr.Piece;
import org.appxi.smartcn.pinyin.PinyinHelper;
import org.appxi.util.DigestHelper;

import java.util.ArrayList;
import java.util.List;

public interface ItemProvider {
    String providerId();

    String providerName();

    boolean isFolder();

    default String toCleanName(String itemName) {
        return itemName;
    }
//
//    Consumer<Item> getCreator();
//
//    Function<Item, Object> getEditor();
//
//    Function<Item, Object> getViewer();

    default void touching(Item item) {
    }

    default List<Piece> indexing(Item item) {
        final Piece mainPiece = Piece.of();
        mainPiece.provider = providerId();
        mainPiece.path = item.getPath();
        mainPiece.type = "location";
        mainPiece.title = item.getName();
        //
        Piece piece = mainPiece.clone();
        piece.id = DigestHelper.uid62s();
        piece.title = mainPiece.title;
        piece.field("title_txt_aio", piece.title);
        piece.field("title_txt_en", PinyinHelper.pinyin(piece.title));
        //
        return new ArrayList<>(List.of(piece));
    }
}
