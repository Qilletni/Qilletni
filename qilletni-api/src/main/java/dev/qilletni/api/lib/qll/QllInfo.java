package dev.qilletni.api.lib.qll;

import java.util.List;

/**
 * A record of a JSON file in a .qll that holds metadata about it.
 * 
 * @param name
 * @param version
 * @param author
 * @param dependencies
 * @param providerClass
 */
public record QllInfo(String scope, String name, Version version, String author, String description, String sourceUrl, List<QilletniInfoData.Dependency> dependencies, String providerClass, String nativeBindFactoryClass, List<String> nativeClasses, List<String> autoImportFiles) {
    public QllInfo(QilletniInfoData qilletniInfoData) {
        this(qilletniInfoData.scope(), qilletniInfoData.name(), qilletniInfoData.version(), qilletniInfoData.author(), qilletniInfoData.description(), qilletniInfoData.sourceUrl(), qilletniInfoData.dependencies(), qilletniInfoData.providerClass(), qilletniInfoData.nativeBindFactoryClass(), qilletniInfoData.nativeClasses(), qilletniInfoData.autoImportFiles());
    }
}
