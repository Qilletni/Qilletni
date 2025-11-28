package dev.qilletni.api.lib.qll;

import java.util.List;

/**
 * A representation of the qilletni_info.yml file in an unbuilt project/application
 * 
 * @param name
 * @param version
 * @param author
 * @param dependencies
 */
public record QilletniInfoData(String scope, String name, Version version, String author, String description, String sourceUrl, String providerClass, String nativeBindFactoryClass, List<String> nativeClasses, List<String> autoImportFiles, List<Dependency> dependencies) {
    
    public record Dependency(String name, ComparableVersion version) {}
    
}
