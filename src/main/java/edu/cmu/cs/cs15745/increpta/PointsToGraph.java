package edu.cmu.cs.cs15745.increpta;

import java.util.Set;

public interface PointsToGraph<Node, HeapItem> {
  /** Returns nodes affected by edge addition. */
  Set<Node> addEdge(Node from, Node to);

  /** Returns nodes affected by edge deletion. */
  Set<Node> deleteEdge(Node from, Node to);

  Set<Node> nodes();

  Set<Node> edges(Node from);

  Set<HeapItem> pointsTo(Node key);

  // For testing we require these guys to be cloneable
  PointsToGraph<Node, HeapItem> clone();
}
