package net.irisshaders.iris.vulkan;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5: pass graph for the Vulkan screen-pass chain. Nodes are
 * {@link IrisVulkanScreenPass}es in shaderpack declaration order; edges are
 * implied by colortex read/write sets (a pass that reads colortex N after a
 * pass that wrote colortex N depends on it), which matches Iris's existing
 * BufferFlipper semantics.
 */
final class IrisVulkanScreenPassGraph {
	static final class Node {
		final IrisVulkanScreenPass pass;
		final IntSet reads = new IntOpenHashSet();
		final IntSet writes = new IntOpenHashSet();
		final List<Node> dependencies = new ArrayList<>();

		Node(IrisVulkanScreenPass pass) {
			this.pass = pass;
		}
	}

	private final List<Node> nodes = new ArrayList<>();

	void add(IrisVulkanScreenPass pass, IntSet reads, IntSet writes) {
		Node node = new Node(pass);
		node.reads.addAll(reads);
		node.writes.addAll(writes);

		for (Node previous : nodes) {
			boolean depends = false;
			for (int read : node.reads) {
				if (previous.writes.contains(read)) {
					depends = true;
					break;
				}
			}

			if (!depends && !node.writes.isEmpty()) {
				for (int write : node.writes) {
					if (previous.writes.contains(write) || previous.reads.contains(write)) {
						depends = true;
						break;
					}
				}
			}

			if (depends) {
				node.dependencies.add(previous);
			}
		}

		nodes.add(node);
	}

	List<Node> nodes() {
		return nodes;
	}

	int size() {
		return nodes.size();
	}
}
