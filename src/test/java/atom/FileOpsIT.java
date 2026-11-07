package atom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenkinschina.atom.sdk.testing.Atom;
import com.jenkinschina.atom.sdk.testing.AtomResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The file-ops atom, started the way the runner starts it.
 *
 * <p>These are the checks the old {@code tools/smoke.sh} made — copy with flatten, delete, mkdir, and the guard that
 * keeps a target inside the workspace — written as JUnit so a failure is a test result with the workspace attached.
 */
class FileOpsIT {

    private Atom atom;

    @BeforeEach
    void setUp() throws Exception {
        atom = Atom.of();
    }

    @Test
    @DisplayName("copy flattens into the target directory")
    void copiesWithFlatten() throws Exception {
        AtomResult run = atom.file("dist/a/app.jar", "x")
                .input("operation", "copy")
                .input("pattern", "dist/**/*.jar")
                .input("target", "release")
                .input("flatten", "true")
                .run();

        run.assertOk();
        assertTrue(run.exists("release/app.jar"), "the flattened copy is missing:\n" + run.console());
    }

    @Test
    @DisplayName("delete removes what the pattern names and leaves the rest alone")
    void deletes() throws Exception {
        AtomResult run = atom.file("keep.txt", "x")
                .file("drop.txt", "y")
                .input("operation", "delete")
                .input("pattern", "drop.txt")
                .run();

        run.assertOk();
        assertFalse(run.exists("drop.txt"), "drop.txt should be gone");
        assertTrue(run.exists("keep.txt"), "delete touched the wrong file");
    }

    @Test
    @DisplayName("a target outside the workspace is refused")
    void refusesToEscapeTheWorkspace() throws Exception {
        AtomResult run = atom.file("a.txt", "x")
                .input("operation", "copy")
                .input("pattern", "a.txt")
                .input("target", "../escape")
                .run();

        run.assertRefused();
        assertFalse(run.exists("../escape"), "nothing may be written outside the workspace");
    }

    @Test
    @DisplayName("mkdir creates nested directories")
    void makesDirectories() throws Exception {
        AtomResult run = atom.input("operation", "mkdir")
                .input("target", "out/nested")
                .run();

        run.assertOk();
        assertTrue(run.exists("out/nested"), "mkdir did not create out/nested:\n" + run.console());
    }
}
