package nl.uu.maze.search.strategy;

import java.util.List;
import java.util.Random;

import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.util.Tree;
import nl.uu.maze.util.Tree.TreeNode;

/**
 * Random Path Search (RPS) strategy.
 * <p>
 * Maintains an execution tree and selects paths by randomly walking from
 * root to leaf. Designed specifically for symbolic-driven execution, it
 * naturally favors states closer to the root, keeping path conditions shorter
 * and easier for constraint solvers to handle compared to pure random search.
 */
public class RandomPathSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private Tree<T> tree;
    private TreeNode<T> current;
    private final Random random = new Random();

    public String getName() {
        return "RandomPathSearch";
    }

    @Override
    public void add(T target) {
        if (tree == null) {
            tree = new Tree<>(target);
            current = tree.getRoot();
        } else {
            current.addChild(target);
            // Remove state from current node (don't need it anymore)
            current.setValue(null);
        }
    }

    @Override
    public void remove(T target) {
        // Remove the state from the tree by removing the path it's part of
        // Most likely the state to remove is the current tree node
        // If not, we need to find the node first
        if (current != null && current.hasValue() && current.getValue().equals(target)) {
            tree.removePath(current);
        } else {
            tree.findNode(target).ifPresent(node -> tree.removePath(node));
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
    public void select(T target) {
        // When a specific target is selected, we need to set the current node to that
        // node, so that adding new targets will be added as children of that node
        tree.findNode(target).ifPresent(node -> current = node);
    }

    @Override
    public void reset() {
        tree = null;
        current = null;
    }

    @Override
    public List<T> getAll() {
        return tree.getLeafNodes().stream().map(TreeNode::getValue).toList();
    }
}
