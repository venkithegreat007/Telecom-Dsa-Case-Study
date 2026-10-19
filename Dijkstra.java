import java.util.*;

public class Dijkstra {
    public static class NodeDist {
        public String node;
        public double distance;
        public NodeDist(String node, double distance) {
            this.node = node; this.distance = distance;
        }
    }

    public static Map<String, Double> computeShortestPaths(Graph network, String start) {
        Map<String, Double> distances = new HashMap<>();
        PriorityQueue<NodeDist> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.distance));

        Set<String> nodes = network.getNodes();
        for (String node : nodes) {
            distances.put(node, Double.MAX_VALUE);
        }
        if (!distances.containsKey(start)) return distances;

        distances.put(start, 0.0);
        pq.add(new NodeDist(start, 0.0));

        while (!pq.isEmpty()) {
            NodeDist curr = pq.poll();
            if (curr.distance > distances.get(curr.node)) continue;

            for (Graph.Edge e : network.getNeighbors(curr.node)) {
                double newDist = distances.get(curr.node) + e.weight;
                if (newDist < distances.get(e.target)) {
                    distances.put(e.target, newDist);
                    pq.add(new NodeDist(e.target, newDist));
                }
            }
        }
        return distances;
    }
}
