package nl.uu.maze.main.cli;

public enum JUnitVersion {
    JUnit4, JUnit5;

    public boolean isJUnit4() {
        return this == JUnit4;
    }
}
