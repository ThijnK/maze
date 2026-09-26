package nl.uu.maze.main.cli;

import picocli.CommandLine.IVersionProvider;

/** Reports the version Maven writes into the packaged artifact's manifest. */
public final class MazeVersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        String version = MazeVersionProvider.class.getPackage().getImplementationVersion();
        return new String[] {"maze " + (version == null ? "development" : version)};
    }
}
