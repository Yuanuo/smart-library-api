package org.appxi.smartlib;

import org.appxi.smartcn.pinyin.PinyinHelper;
import org.appxi.util.StringHelper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ItemHelper {

    public static final Pattern P_INDEXED_NAME = Pattern.compile("(\\d+)(\\.\s+)(.*)");
    private static final Pattern P_INDEX_PART = Pattern.compile("^(\\d+)(.*)");
    private static final Pattern P_ASCII_PART = Pattern.compile("^([ .a-zA-Z]+)(.*)");

    private ItemHelper() {
    }

    public static boolean isNameWithProvider(String name, String providerId) {
        return null != providerId && !providerId.isBlank() && name.toLowerCase().endsWith(".".concat(providerId));
    }

    public static String getNameWithProvider(String name, String providerId) {
        if (null == providerId || providerId.isBlank()) return name;
        final String ext = ".".concat(providerId);
        return name.endsWith(ext) ? name : name.concat(ext);
    }

    public static String getNameWithoutProvider(String name, String providerId) {
        return null == providerId || providerId.isBlank()
                ? name : name.substring(0, name.length() - providerId.length() - 1);
    }

    public static String toComparableString(Item item) {
        String name = item.getName();

        final StringBuilder buff = new StringBuilder();
//        buff.append(item.provider.isDirectory() ? '0' : '1').append('!');

        Matcher matcher = P_INDEX_PART.matcher(name);
        if (matcher.matches()) {
            buff.append(StringHelper.padLeft(matcher.group(1), 10, '0'));
            name = matcher.group(2);
        }
        matcher = P_ASCII_PART.matcher(name);
        if (matcher.matches()) {
            buff.append(matcher.group(1).toLowerCase(Locale.ROOT));
            name = matcher.group(2);
        }
        if (!name.isBlank()) {
            buff.append(PinyinHelper.pinyin(name).toLowerCase(Locale.ROOT));
        }

        return buff.toString();
    }
}
