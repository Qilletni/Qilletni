package dev.qilletni.impl.lang.runner;

import java.nio.file.Path;
import java.nio.file.Paths;

public record ImportPathState(Path path, String libraryName) {

    public static final ImportPathState VIRTUAL_STATE = new ImportPathState(Path.of(""));

    public ImportPathState(Path path) {
        this(path, null);
    }

    /**
     * Creates a new ImportPathState with a sub path in {@link #path}.
     *
     * @param childFile The name (or relative path) of the next file
     * @return The new {@link ImportPathState} to import from
     */
    public ImportPathState importFrom(String childFile) {
        childFile = childFile.substring(1, childFile.length() - 1); // strip ""

        if (childFile.contains(":")) {
            var colonIndex = childFile.indexOf(":");
            var libName = childFile.substring(0, colonIndex);
            var pathName = childFile.substring(colonIndex + 1);

            return new ImportPathState(Paths.get(pathName), libName);
        }

        var parentPath = path.getParent();
        Path usePath;
        
        if (parentPath == null) {
            usePath = Path.of(childFile);
        } else {
            usePath = parentPath.resolve(childFile);
        }

        return new ImportPathState(usePath.normalize(), libraryName);
    }
}
