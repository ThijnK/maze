package nl.uu.maze.search.strategy;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.util.Tree;
import nl.uu.maze.util.Tree.TreeNode;

/**
 * Symbolic-driven search strategy that keeps track of the execution tree and
 * selects the next state to explore by walking the tree from the root to one of
 * the leaves, randomly selecting the branch to follow at each node.
 * <p>
 * Where random search selects the next state to explore
 * uniformly at random from the set of all states, this search strategy gives
 * preference to states that are closer to the root of the execution tree, and
 * thus have a lower path length. This helps to keep the path conditions shorter
 * and more manageable.
 */
public class RandomPathSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private Tree<T> tree;
    private TreeNode<T> current;
    private final Random random = new Random();

    public String getName() {
        return "RandomPathSearch";
    }

    @Override
    public void add(T state) {
        if (tree == null) {
            tree = new Tree<>(state);
        } else {
            current.addChild(state);
        }
    }

    @Override
    public T next() {
        // Walk the tree from the root to a leaf, randomly selecting the branch to
        // follow at each node
        current = tree.getRoot();
        while (current != null && !current.isLeaf()) {
            List<TreeNode<T>> children = current.getChildren();
            current = children.get(random.nextInt(children.size()));
        }
        return current != null ? current.getValue() : null;
    }

    @Override
    public void remove(T state) {
        // Remove the state from the tree by removing the path it's part of
        // Most likely the state to remove is the current tree node
        // If not, we need to find the node first
        if (current.getValue().equals(state)) {
            tree.removePath(current);
        } else {
            Optional<TreeNode<T>> node = tree.findNode(state);
            node.ifPresent(TTreeNode -> tree.removePath(TTreeNode));
        }
    }

    @Override
    public void reset() {
        tree = null;
        current = null;
    }
}
