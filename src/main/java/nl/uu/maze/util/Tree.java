package nl.uu.maze.util;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Represents a generic tree where each node can hold a value of any
 * type.
 *
 * @param <T> The type of value held in each node.
 */
public class Tree<T> {

    /**
     * Represents a node in the tree.
     *
     * @param <T> The type of value held in each node.
     */
    public static class TreeNode<T> {
        private T value;
        private final TreeNode<T> parent;
        private final List<TreeNode<T>> children;

        public TreeNode(T value, TreeNode<T> parent) {
            this.value = value;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public boolean hasValue() {
            return value != null;
        }

        public TreeNode<T> getParent() {
            return parent;
        }

        public List<TreeNode<T>> getChildren() {
            return children;
        }

        public int getChildCount() {
            return children.size();
        }

        public TreeNode<T> getChild(int index) {
            return children.get(index);
        }

        public void addChild(TreeNode<T> child) {
            this.children.add(child);
        }

        public void addChild(T value) {
            this.children.add(new TreeNode<>(value, this));
        }

        public void addChildren(List<TreeNode<T>> children) {
            this.children.addAll(children);
        }

        public boolean isLeaf() {
            return children.isEmpty();
        }

        public void removeChild(TreeNode<T> child) {
            this.children.remove(child);
        }
    }

    private TreeNode<T> root;

    public Tree(T rootValue) {
        this.root = new TreeNode<>(rootValue, null);
    }

    public TreeNode<T> getRoot() {
        return root;
    }

    public Optional<TreeNode<T>> findNode(T value) {
        return findNode(root, value);
    }

    /**
     * Finds the node with the given value in the tree.
     * 
     * @param current The current node to search from
     * @param value   The value to search for
     * @return The node with the given value or null if not found
     */
    public Optional<TreeNode<T>> findNode(TreeNode<T> current, T value) {
        if (current == null) {
            return Optional.empty();
        }

        if (current.hasValue() && current.getValue().equals(value)) {
            return Optional.of(current);
        }

        for (TreeNode<T> child : current.getChildren()) {
            Optional<TreeNode<T>> result = findNode(child, value);
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    /**
     * Removes the given start node and the path towards it from the tree.
     * 
     * @param startNode The node to remove the path for
     */
    public void removePath(TreeNode<T> startNode) {
        if (startNode == null) {
            return;
        }

        TreeNode<T> parent = startNode.getParent();
        if (parent != null) {
            parent.removeChild(startNode);
            // Recursively remove the parent if it becomes a leaf
            if (parent.isLeaf()) {
                removePath(parent);
            }
        } else if (startNode == root) {
            // If the startNode is the root, set the root to null
            root = null;
        }
    }

    /**
     * Get all leaf nodes in the tree.
     */
    public List<TreeNode<T>> getLeafNodes() {
        List<TreeNode<T>> leafNodes = new ArrayList<>();
        collectLeafNodes(root, leafNodes);
        return leafNodes;
    }

    private void collectLeafNodes(TreeNode<T> node, List<TreeNode<T>> leafNodes) {
        if (node == null) {
            return;
        }

        Deque<TreeNode<T>> stack = new java.util.ArrayDeque<>();
        stack.push(node);

        while (!stack.isEmpty()) {
            TreeNode<T> current = stack.pop();

            if (current.isLeaf()) {
                leafNodes.add(current);
            } else {
                // Add children to the stack in reverse order
                // to maintain the original traversal order
                List<TreeNode<T>> children = current.getChildren();
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
    }
}
