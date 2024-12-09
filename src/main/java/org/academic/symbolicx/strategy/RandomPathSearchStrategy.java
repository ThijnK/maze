package org.academic.symbolicx.strategy;

import java.util.List;
import java.util.Random;

import org.academic.symbolicx.executor.SymbolicState;

/**
 * A search strategy that keeps track of the execution tree and selects the next
 * state to explore by walking the tree from the root to one of the leaves,
 * randomly selecting the branch to follow at each node.
 * 
 * Whereas the {@link RandomSearchStrategy} selects the next state to explore
 * uniformly at random from the set of all states, this search strategy gives
 * preference to states that are closer to the root of the execution tree, and
 * thus have a lower path length. This helps to keep the path conditions shorter
 * and more manageable.
 */
public class RandomPathSearchStrategy extends SearchStrategy {
    private ExecutionTree<SymbolicState> tree;
    private ExecutionTree.TreeNode<SymbolicState> current;
    private Random random;

    public RandomPathSearchStrategy() {
        random = new Random();
    }

    @Override
    public void init(SymbolicState initialState) {
        tree = new ExecutionTree<>(initialState);
    }

    @Override
    public SymbolicState next() {
        // Walk the tree from the root to a leaf, randomly selecting the branch to
        // follow at each node
        current = tree.getRoot();
        while (current != null && !current.isLeaf()) {
            List<ExecutionTree.TreeNode<SymbolicState>> children = current.getChildren();
            current = children.get(random.nextInt(children.size()));
        }
        return current != null ? current.getValue() : null;
    }

    @Override
    public void add(List<SymbolicState> newStates) {
        current.addChildrenFromValues(newStates);
    }

    @Override
    public void remove(SymbolicState state) {
        // Remove the state from the tree by removing the path it's part of
        // Most likely the state to remove is the current tree node
        // If not, we need to find the node first
        if (current.getValue().equals(state)) {
            tree.removePath(current);
        } else {
            ExecutionTree.TreeNode<SymbolicState> node = tree.findNode(state);
            if (node != null) {
                tree.removePath(node);
            }
        }
    }
}
