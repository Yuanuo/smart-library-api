package org.appxi.smartlib;

import org.appxi.util.ext.Attributes;

import java.util.Objects;

public abstract class Item extends Attributes {
    public final ItemProvider provider;

    public Item(ItemProvider provider) {
        this.provider = provider;
    }

    public abstract Item setName(String name);

    public abstract String getName();

    public abstract Item setPath(String path);

    public abstract String getPath();

    @Override
    public String toString() {
        return this.getName();
    }

    public String toName() {
        return this.provider.toCleanName(this.getName());
    }

    public String toDetail() {
        return "【" + this.provider.providerName() + "】: /" + this.getPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Objects.equals(getName(), item.getName()) && Objects.equals(getPath(), item.getPath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getPath());
    }

    @Override
    public Item clone() {
        return clone(this.getName(), this.getPath(), this.provider);
    }

    public abstract Item clone(String name, String path, ItemProvider provider);

    public abstract Item root();

    /* ============================================================================================= */

    public final boolean isRoot() {
        return this == root() || root().getPath().equals(this.getPath());
    }

    public final boolean isFolder() {
        return this.provider.isFolder();
    }

    public final Item parentItem() {
        String parentPath = this.parentPath();
        if (parentPath.isBlank())
            return root();

        int idx = parentPath.lastIndexOf('/');
        String parentName = parentPath.substring(idx + 1);
        return clone(parentName, parentPath, FolderProvider.ONE);
    }

    public final String parentPath() {
        String path = this.getPath();
        int idx = path.lastIndexOf('/');
        return idx > 0 ? path.substring(0, idx) : "";
    }
}
