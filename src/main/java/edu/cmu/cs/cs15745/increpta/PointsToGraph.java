package edu.cmu.cs.cs15745.increpta;

import java.util.Set;

public interface PointsToGraph<Node, HeapItem> {
	boolean addEdge(Node from, Node to);
	Set<Node> nodes();
	Set<Node> edges(Node from);
	Set<HeapItem> pointsTo(Node key);
	
	// For testing we require these guys to be cloneable
	PointsToGraph<Node, HeapItem> clone();
}
