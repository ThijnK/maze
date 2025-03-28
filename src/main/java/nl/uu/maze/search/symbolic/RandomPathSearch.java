package nl.uu.maze.search.symbolic;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.SymbolicSearchStrategy;
import nl.uu.maze.util.Tree;
import nl.uu.maze.util.Tree.TreeNode;

/**
 * Symbolic-driven search strategy that keeps track of the execution tree and
 * selects the next
 * state to explore by walking the tree from the root to one of the leaves,
 * randomly selecting the branch to follow at each node.
 * <p>
 * Whereas the {@link RandomSearch} selects the next state to explore
 * uniformly at random from the set of all states, this search strategy gives
 * preference to states that are closer to the root of the execution tree, and
 * thus have a lower path length. This helps to keep the path conditions shorter
 * and more manageable.
 */
public class RandomPathSearch extends SymbolicSearchStrategy {
    private Tree<SymbolicState> tree;
    private TreeNode<SymbolicState> current;
    private final Random random = new Random();

    public String getName() {
        return "RandomPathSearch";
    }

    @Override
    public void add(SymbolicState state) {
        if (tree == null) {
            tree = new Tree<>(state);
        } else {
            current.addChild(state);
        }
    }

    @Override
    public SymbolicState next() {
        // Walk the tree from the root to a leaf, randomly selecting the branch to
        // follow at each node
        current = tree.getRoot();
        while (current != null && !current.isLeaf()) {
            List<TreeNode<SymbolicState>> children = current.getChildren();
            current = children.get(random.nextInt(children.size()));
        }
        return current != null ? current.getValue() : null;
    }

    @Override
    public void remove(SymbolicState state) {
        // Remove the state from the tree by removing the path it's part of
        // Most likely the state to remove is the current tree node
        // If not, we need to find the node first
        if (current.getValue().equals(state)) {
            tree.removePath(current);
        } else {
            Optional<TreeNode<SymbolicState>> node = tree.findNode(state);
            node.ifPresent(symbolicStateTreeNode -> tree.removePath(symbolicStateTreeNode));
        }
    }

    @Override
    public void reset() {
        tree = null;
        current = null;
    }
}
