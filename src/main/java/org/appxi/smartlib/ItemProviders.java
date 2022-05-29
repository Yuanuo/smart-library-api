package org.appxi.smartlib;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public abstract class ItemProviders {

    private static final List<ItemProvider> PROVIDERS = new ArrayList<>(16);

    static {
        PROVIDERS.add(FileProvider.ONE);
        PROVIDERS.add(FolderProvider.ONE);
    }

    public static void add(ItemProvider... providers) {
        for (ItemProvider p : providers) {
            if (!PROVIDERS.contains(p)) {
                PROVIDERS.add(PROVIDERS.size() - 2, p);
            }
        }
    }

    public static void remove(ItemProvider... providers) {
        for (ItemProvider provider : providers) {
            if (provider == FileProvider.ONE || provider == FolderProvider.ONE)
                continue;
            PROVIDERS.remove(provider);
        }
    }

    public static List<ItemProvider> list() {
        return List.copyOf(PROVIDERS);
    }

    public static ItemProvider find(String providerId) {
        return find(p -> Objects.equals(p.providerId(), providerId));
    }

    public static ItemProvider find(Predicate<ItemProvider> predicate) {
        for (ItemProvider provider : PROVIDERS) {
            if (predicate.test(provider)) {
                return provider;
            }
        }
        return null;
    }

    private ItemProviders() {
    }
}
