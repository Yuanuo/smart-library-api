package org.appxi.smartlib.dao;

import org.apache.commons.io.IOUtils;
import org.appxi.search.solr.Piece;
import org.appxi.smartlib.FileProvider;
import org.appxi.smartlib.FolderProvider;
import org.appxi.smartlib.Item;
import org.appxi.smartlib.ItemHelper;
import org.appxi.smartlib.ItemProvider;
import org.appxi.smartlib.ItemProviders;
import org.appxi.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class ItemRepositoryImpl implements ItemRepository {
    private final Logger logger = LoggerFactory.getLogger(ItemRepositoryImpl.class);
    private final Path repo;
    private final int rootLevels;
    private final Item root;

    public ItemRepositoryImpl(Path repo, Item root) {
        this.repo = repo;
        this.rootLevels = repo.getNameCount();
        this.root = root;
    }

    @Override
    public List<Item> list(Item parent) {
        final List<Item> result = new ArrayList<>();
        final Path parentPath = repo.resolve(parent.getPath());

        if (Files.notExists(parentPath) || !Files.isReadable(parentPath) || !Files.isDirectory(parentPath))
            return result;

        try {
            Files.list(parentPath)
                    .filter(path -> path.getFileName().toString().charAt(0) != '.')
                    .sorted((a, b) -> Boolean.compare(Files.isDirectory(b), Files.isDirectory(a)))
                    .forEach(path -> result.add(toItem(path)));
        } catch (Exception e) {
            logger.warn("list", e);
        }
        return result;
    }

    @Override
    public Item resolve(String itemPath) {
        return toItem(repo.resolve(itemPath.replaceAll("\\.\\./", "")));
    }

    private Item toItem(Path path) {
        final String fileName = path.getFileName().toString();
        final String itemPath = FileHelper.subPath(path, rootLevels);
        // set default name
        String itemName = fileName;
        ItemProvider itemProvider;
        if (Files.isDirectory(path)) {
            // detect provider by name extension
            itemProvider = ItemProviders.find(p -> p.isFolder() && ItemHelper.isNameWithProvider(fileName, p.providerId()));
            // set real name by fileName without extension
            if (null != itemProvider) {
                itemName = itemProvider.toCleanName(fileName);
//                itemName = ItemHelper.nameWithoutProvider(fileName, itemProvider.providerId());
            }
            if (null == itemProvider) {
                itemProvider = ItemProviders.find(p -> p.isFolder() && fileName.equals(p.providerId()));
            }
        } else {
            // detect provider by name extension
            itemProvider = ItemProviders.find(p -> !p.isFolder() && ItemHelper.isNameWithProvider(fileName, p.providerId()));
            // set real name by fileName without extension
            if (null != itemProvider) {
                itemName = itemProvider.toCleanName(fileName);
//                itemName = ItemHelper.nameWithoutProvider(fileName, itemProvider.providerId());
            }
            if (null == itemProvider) {
                itemProvider = ItemProviders.find(p -> !p.isFolder() && fileName.equals(p.providerId()));
            }
        }
        // set default provider
        if (null == itemProvider) {
//                    item.setName("Unknown - ".concat(item.getName()));
            itemProvider = Files.isDirectory(path) ? FolderProvider.ONE : FileProvider.ONE;
        }
        return root.clone(itemName, itemPath, itemProvider);
    }

    @Override
    public String walk(Item parent, Consumer<Item> consumer) {
        final Path parentPath = repo.resolve(parent.getPath());

        if (Files.notExists(parentPath))
            return "文件或目录不存在";
        if (!Files.isReadable(parentPath))
            return "文件或目录不能读取";
        if (!Files.isDirectory(parentPath)) {
            consumer.accept(parent);
            return null;
        }

        try {
            Files.walkFileTree(parentPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir == parentPath) return FileVisitResult.CONTINUE;
                    if (dir.getFileName().toString().charAt(0) == '.') return FileVisitResult.SKIP_SUBTREE;
                    consumer.accept(toItem(dir));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().charAt(0) == '.') return FileVisitResult.SKIP_SUBTREE;
                    consumer.accept(toItem(file));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (AccessDeniedException e) {
            logger.warn("walk", e);
            return "本地文件禁止访问！";
        } catch (IOException e) {
            logger.warn("walk", e);
            return "本地文件访问错误！";
        }
        return null;
    }

    @Override
    public boolean exists(String itemPath) {
        final Path path = repo.resolve(itemPath);
        return FileHelper.exists(path) && path.startsWith(repo);
    }

    @Override
    public List<String> exists(String itemPath, String newParent) {
        final Path sourcePath = repo.resolve(itemPath);
        final Path targetPath = repo.resolve(newParent);

        if (Files.isRegularFile(sourcePath)) {
            Path temp = targetPath.resolve(sourcePath.getFileName());
            return Files.notExists(temp) ? List.of() : List.of(FileHelper.subPath(temp, rootLevels));
        }

        if (Files.isDirectory(sourcePath)) {
            final List<String> result = new ArrayList<>();
            try {
                int srcLevels = sourcePath.getNameCount() - 1;
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        final Path tgtPath = targetPath.resolve(FileHelper.subPath(file, srcLevels));
                        if (FileHelper.exists(tgtPath))
                            result.add(FileHelper.subPath(tgtPath, rootLevels));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception e) {
                logger.warn("exists", e);
            }
            return result;
        }
        return List.of();
    }

    @Override
    public File filePath(Item item) {
        return null == item || item.isRoot() ? repo.toFile() : repo.resolve(item.getPath()).toFile();
    }

    @Override
    public String create(Item item) {
        if (!FileHelper.isNameValid(item.getName()))
            return "格式错误！可能包含特殊字符，请修改后重试。";

        Path itemPath = repo.resolve(item.getPath()).getParent();
        itemPath = itemPath.resolve(ItemHelper.getNameWithProvider(item.getName(), item.provider.providerId()));

        if (FileHelper.exists(itemPath)) {
            item.setPath(FileHelper.subPath(itemPath, rootLevels));
            return null;
        }

        try {
            if (item.provider.isFolder()) {
                Files.createDirectories(itemPath);
                item.setPath(FileHelper.subPath(itemPath, rootLevels));
            } else {
                Files.createDirectories(itemPath.getParent());
                Files.createFile(itemPath);
                item.setPath(FileHelper.subPath(itemPath, rootLevels));
            }
            return null;
        } catch (AccessDeniedException e) {
            logger.warn("create", e);
            return "本地文件禁止访问！";
        } catch (Exception e) {
            logger.warn("create", e);
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    @Override
    public String rename(Item item, String newName) {
        if (!FileHelper.isNameValid(newName))
            return "格式错误！可能包含特殊字符，请修改后重试。";

        final Path sourcePath = repo.resolve(item.getPath());
        if (FileHelper.notExists(sourcePath))
            return "源文件或目录不存在！";

        Path targetPath = repo.resolve(item.getPath()).getParent();
        targetPath = targetPath.resolve(ItemHelper.getNameWithProvider(newName, item.provider.providerId()));
        if (FileHelper.exists(targetPath))
            return "目标文件或目录已存在，不能覆盖！";

        try {
            Files.move(sourcePath, targetPath);
            item.setName(newName);
            item.setPath(FileHelper.subPath(targetPath, rootLevels));
            return null;
        } catch (AccessDeniedException e) {
            logger.warn("rename", e);
            return "文件或目录禁止访问！";
        } catch (Exception e) {
            logger.warn("rename", e);
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    @Override
    public String delete(Item item, BiConsumer<Double, String> progressCallback) {
        final Path itemPath = repo.resolve(item.getPath());
        if (FileHelper.notExists(itemPath))
            return null;
        try {
            if (Files.isDirectory(itemPath)) {
                final String ignoredFlag = File.separator + ".";
                Files.walk(itemPath)
                        .sorted(Comparator.reverseOrder())
                        .filter(path -> path.getNameCount() > rootLevels && !path.toString().contains(ignoredFlag))
                        .map(path -> {
                            progressCallback.accept(-1D, FileHelper.subPath(path, rootLevels));
                            return path.toFile();
                        })
                        .forEach(File::delete);
                return null;
            } else {
                Files.delete(itemPath);
            }
        } catch (AccessDeniedException e) {
            logger.warn("delete", e);
            return "文件或目录禁止访问！";
        } catch (Exception e) {
            logger.warn("delete", e);
            return e.getClass().getName() + ": " + e.getMessage();
        }
        return FileHelper.notExists(itemPath) ? null : "无法删除一些文件或目录！";
    }

    @Override
    public String setContent(Item item, InputStream content) {
        final Path itemPath = repo.resolve(item.getPath());
        if (Files.isDirectory(itemPath)) {
            return null;//
        } else {
            try (InputStream swap = content) {
                Files.copy(swap, itemPath, StandardCopyOption.REPLACE_EXISTING);
                return null;
            } catch (AccessDeniedException e) {
                logger.warn("setContent", e);
                return "文件或目录禁止访问！";
            } catch (Exception e) {
                logger.warn("setContent", e);
                return e.getClass().getName() + ": " + e.getMessage();
            }
        }
    }

    @Override
    public InputStream getContent(Item item) {
        final Path itemPath = repo.resolve(item.getPath());
        if (Files.isDirectory(itemPath)) {
            return null;//
        } else {
            try {
                return Files.newInputStream(itemPath);
            } catch (Exception e) {
                logger.warn("getContent", e);
                return null;
            }
        }
    }

    @Override
    public String backup(Item parent, ZipOutputStream zipStream, BiConsumer<Double, String> progressCallback) {
        this.walk(parent, item -> {
            if (item.provider.isFolder()) {
                try {
                    zipStream.putNextEntry(new ZipEntry(item.getPath().concat("/")));
                } catch (IOException e) {
                    logger.warn("backup", e);
                }
            } else {
                progressCallback.accept(-1D, item.toDetail());
                try (InputStream itemContent = getContent(item)) {
                    zipStream.putNextEntry(new ZipEntry(item.getPath()));
                    if (null != itemContent) {
                        IOUtils.copy(itemContent, zipStream);
                    }
                } catch (IOException e) {
                    logger.warn("backup", e);
                }
            }
        });
        return null;
    }

    @Override
    public String restore(Item parent, ZipFile zipFile, BiConsumer<Double, String> progressCallback) {
        final String base = parent.isRoot() ? "" : parent.getPath().concat("/");
        zipFile.stream().forEach(entry -> {
            final String entryPath = base.concat(entry.getName());
            if (entry.isDirectory()) {
                FileHelper.makeDirs(repo.resolve(entryPath));
                return;
            }
            try {
                progressCallback.accept(-1D, entryPath);
                final Path target = repo.resolve(entryPath);
                FileHelper.makeParents(target);
                Files.copy(zipFile.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.warn("restore", e);
            }
        });
        return null;
    }

    @Override
    public String getIdentificationInfo(Item item) {
        return FileHelper.getIdentificationInfo(repo.resolve(item.getPath()));
    }

    @Override
    public String move(Item item, Item newParent) {
        final Path sourcePath = repo.resolve(item.getPath());
        final Path targetPath = repo.resolve(newParent.getPath()).resolve(sourcePath.getFileName());

        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            item.setPath(FileHelper.subPath(targetPath, rootLevels));
            return null;
        } catch (Exception e) {
            logger.warn("move", e);
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    @Override
    public String reindex(Item item, BiConsumer<Double, String> progressCallback) {
        final PiecesRepository repository = ItemsDao.solrIndexes();
        if (null == repository) return null;
        if (item.provider.isFolder()) {
            if (item.isRoot()) repository.deleteAll();
            else repository.deleteAllByPath(item.getPath());

            final Consumer<List<Piece>> batchCommitter = batchList -> {
                try {
                    repository.saveAll(batchList);
                } catch (Throwable t) {
                    logger.warn("batchCommitter.saveAll", t);
                    for (Piece itm : batchList) {
                        try {
                            repository.save(itm);
                        } catch (Throwable ex) {
                            logger.warn("batchCommitter.save", ex);
                        }
                    }
                }
                batchList.clear();
            };

            final List<Piece> batchList = new ArrayList<>(30);
            this.walk(item, itm -> {
                progressCallback.accept(-1D, itm.toDetail());
                final List<Piece> pieces = itm.provider.indexing(itm);
                if (null == pieces || pieces.isEmpty())
                    return;
                batchList.addAll(pieces);
                //
                if (batchList.size() >= 30) batchCommitter.accept(batchList);
            });
            //
            if (batchList.size() > 0) batchCommitter.accept(batchList);
        } else {
            repository.deleteAllByPath(item.getPath());
            try {
                final List<Piece> pieces = item.provider.indexing(item);
                if (null != pieces && !pieces.isEmpty()) repository.saveAll(pieces);
            } catch (Throwable t) {
                logger.warn("batchCommitter.saveAll", t);
            }
        }
        return null;
    }
}
