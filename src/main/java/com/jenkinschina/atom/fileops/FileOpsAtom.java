package com.jenkinschina.atom.fileops;

import com.jenkinschina.atom.sdk.AtomContext;
import com.jenkinschina.atom.sdk.AtomResult;
import com.jenkinschina.atom.sdk.AtomTask;
import com.jenkinschina.atom.sdk.ErrorType;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * file-operations as an atom: copy, move, mkdir, delete — with every path pinned inside the workspace.
 *
 * <p>The plugin's whole value is doing this without a shell snippet; the guard is the part worth keeping strict, because
 * a mistyped {@code target} in a job would otherwise delete something on the agent.
 */
public class FileOpsAtom implements AtomTask {

    @Override
    public AtomResult run(AtomContext context) throws Exception {
        Path workspace = context.getWorkspace();
        String operation = context.getParam("operation", "copy").trim().toLowerCase(Locale.ROOT);
        String pattern = context.getParam("pattern", "");
        String target = context.getParam("target", "");
        boolean flatten = context.getBooleanParam("flatten", false);

        return switch (operation) {
            case "copy", "move" -> transfer(workspace, operation, pattern, target, flatten);
            case "mkdir" -> mkdir(workspace, target);
            case "delete" -> delete(workspace, pattern);
            default -> AtomResult.error(ErrorType.USER, 40001,
                    "unknown operation '" + operation + "'; use copy, move, mkdir or delete");
        };
    }

    private AtomResult transfer(Path workspace, String operation, String pattern, String target, boolean flatten)
            throws Exception {
        Path destination = inside(workspace, target);
        if (destination == null || target.isBlank()) {
            return AtomResult.error(ErrorType.USER, 40002, "target must be inside the workspace: " + target);
        }
        List<Path> files = match(workspace, pattern);
        if (files.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40003, "no file matched " + pattern);
        }
        int done = 0;
        for (Path file : files) {
            Path name = flatten ? destination.resolve(file.getFileName())
                    : destination.resolve(workspace.relativize(file).toString());
            if (name.getParent() != null) {
                Files.createDirectories(name.getParent());
            }
            if ("move".equals(operation)) {
                Files.move(file, name, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(file, name, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            done++;
        }
        return AtomResult.success()
                .data("operation", operation)
                .data("files", String.valueOf(done))
                .data("target", target);
    }

    private AtomResult mkdir(Path workspace, String target) throws Exception {
        Path directory = inside(workspace, target);
        if (directory == null || target.isBlank()) {
            return AtomResult.error(ErrorType.USER, 40004, "target must be inside the workspace: " + target);
        }
        Files.createDirectories(directory);
        return AtomResult.success().data("created", target);
    }

    private AtomResult delete(Path workspace, String pattern) throws Exception {
        List<Path> files = match(workspace, pattern);
        if (files.isEmpty()) {
            return AtomResult.error(ErrorType.USER, 40005, "no file matched " + pattern);
        }
        int done = 0;
        for (Path file : files) {
            if (Files.isDirectory(file)) {
                try (var walk = Files.walk(file)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            } else {
                Files.deleteIfExists(file);
            }
            done++;
        }
        return AtomResult.success().data("deleted", String.valueOf(done));
    }

    private static Path inside(Path workspace, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Path path = workspace.resolve(name).normalize();
        return path.startsWith(workspace) ? path : null;
    }

    private static List<Path> match(Path workspace, String pattern) throws Exception {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String part : pattern.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + trimmed));
            }
        }
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(workspace, 20)) {
            walk.filter(path -> !path.equals(workspace))
                    .filter(path -> matchers.stream().anyMatch(m -> m.matches(workspace.relativize(path))
                            || m.matches(java.nio.file.Path.of("_").resolve(workspace.relativize(path)))
                            || m.matches(path.getFileName())))
                    .sorted()
                    .limit(20_000)
                    .forEach(files::add);
        }
        // deleting a directory and then its children would fail; keep the outermost match only
        List<Path> filtered = new ArrayList<>();
        for (Path path : files) {
            boolean nested = filtered.stream().anyMatch(parent -> path.startsWith(parent) && !path.equals(parent));
            if (!nested) {
                filtered.add(path);
            }
        }
        return filtered;
    }
}
