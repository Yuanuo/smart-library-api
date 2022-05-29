package org.appxi.smartlib;

public final class FileProvider extends AbstractProvider {
    public static final FileProvider ONE = new FileProvider();

    private FileProvider() {
    }

    @Override
    public final String providerId() {
        return null;
    }

    @Override
    public final String providerName() {
        return "文件";
    }

    @Override
    public final boolean isFolder() {
        return false;
    }
}
