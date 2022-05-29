module appxi.smartLibrary.api {
    requires transitive appxi.shared;
    requires transitive appxi.search.solr;
    requires transitive appxi.smartcn.pinyin;
    requires static appxi.search.tika.aio;
    requires transitive org.jsoup;
    requires transitive org.json;

    opens org.appxi.smartlib.dao; // open for spring

    exports org.appxi.smartlib;
    exports org.appxi.smartlib.dao;
    exports org.appxi.smartlib.article;
    exports org.appxi.smartlib.html;
    exports org.appxi.smartlib.mindmap;
    exports org.appxi.smartlib.tika;
}