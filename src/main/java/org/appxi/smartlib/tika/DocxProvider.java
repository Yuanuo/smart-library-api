package org.appxi.smartlib.tika;

public class DocxProvider extends TikaProvider {
    public static final DocxProvider ONE = new DocxProvider();

    private DocxProvider() {
    }

    @Override
    public String providerId() {
        return "docx";
    }

    @Override
    public String providerName() {
        return "DOCX";
    }
}
