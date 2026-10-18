
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;

public class TelecomServer {
    private static final KDTree kdTree = new KDTree();
    private static final Graph network = new Graph();

    public static void main(String[] args) throws IOException {
        // Initialize Regional Towers
        kdTree.insert(new double[]{10.90, 76.89}, "Tower_Ettimadai");
        kdTree.insert(new double[]{11.02, 76.83}, "Tower_Thondamuthur");
        kdTree.insert(new double[]{10.88, 76.97}, "Tower_Madukkarai");
        kdTree.insert(new double[]{10.98, 76.96}, "Tower_Ukkadam");
        kdTree.insert(new double[]{11.01, 76.95}, "Tower_RS_Puram");
        kdTree.insert(new double[]{11.01, 76.96}, "Tower_Gandhipuram");
        kdTree.insert(new double[]{11.02, 77.03}, "Tower_Peelamedu");
        kdTree.insert(new double[]{11.07, 77.00}, "Tower_Saravanampatti");

        // Initialize Network Edges
        network.addEdge("Tower_Ettimadai", "Tower_Madukkarai", 4.2);
        network.addEdge("Tower_Ettimadai", "Tower_Ukkadam", 9.1);
        network.addEdge("Tower_Madukkarai", "Tower_Ukkadam", 6.5);
        network.addEdge("Tower_Ukkadam", "Tower_RS_Puram", 3.0);
        network.addEdge("Tower_RS_Puram", "Tower_Gandhipuram", 2.1);
        network.addEdge("Tower_Gandhipuram", "Tower_Peelamedu", 7.4);
        network.addEdge("Tower_Peelamedu", "Tower_Saravanampatti", 8.8);
        network.addEdge("Tower_Ettimadai", "Tower_Thondamuthur", 11.2);
        network.addEdge("Tower_Thondamuthur", "Tower_RS_Puram", 10.5);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/nearest", new NearestHandler());
        server.createContext("/api/traffic", new TrafficHandler());
        server.createContext("/api/dijkstra", new DijkstraHandler());
        server.createContext("/api/mst", new MstHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("=== MODULAR JAVA TELECOM DSA SERVER STARTED ON http://localhost:8080 ===");
    }

    private static void setCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static class NearestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            String body = new String(exchange.getRequestBody().readAllBytes());
            double lat = extractParamDouble(body, "lat", 10.90);
            double lon = extractParamDouble(body, "lon", 76.89);
            String nearest = kdTree.findNearest(new double[]{lat, lon});
            String json = String.format("{\"nearestTower\":\"%s\"}", nearest);
            sendResponse(exchange, json);
        }
    }

    static class TrafficHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            String body = new String(exchange.getRequestBody().readAllBytes());
            String devicesStr = extractParamString(body, "devices", "IMEI_1,IMEI_2");
            HyperLogLog hll = new HyperLogLog(4);
            for (String dev : devicesStr.split(",")) {
                if (!dev.trim().isEmpty()) hll.add(dev.trim());
            }
            long estimate = Math.round(hll.estimateCardinality());
            String json = String.format("{\"hllEstimate\":%d}", estimate);
            sendResponse(exchange, json);
        }
    }

    static class DijkstraHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            String body = new String(exchange.getRequestBody().readAllBytes());
            String startNode = extractParamString(body, "start", "Tower_Ettimadai");
            Map<String, Double> distances = Dijkstra.computeShortestPaths(network, startNode);
            StringBuilder jsonDist = new StringBuilder("{");
            int c = 0;
            for (Map.Entry<String, Double> entry : distances.entrySet()) {
                if (entry.getValue() != Double.MAX_VALUE) {
                    if (c > 0) jsonDist.append(",");
                    jsonDist.append(String.format("\"%s\":%.1f", entry.getKey(), entry.getValue()));
                    c++;
                }
            }
            jsonDist.append("}");
            String json = String.format("{\"dijkstra\":%s}", jsonDist.toString());
            sendResponse(exchange, json);
        }
    }

    static class MstHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCors(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) { exchange.sendResponseHeaders(204, -1); return; }
            List<KruskalMST.MSTEdge> mst = KruskalMST.findMST(network);
            double totalCost = 0;
            StringBuilder mstJson = new StringBuilder("[");
            for (int i = 0; i < mst.size(); i++) {
                KruskalMST.MSTEdge e = mst.get(i);
                totalCost += e.weight;
                mstJson.append(String.format("{\"u\":\"%s\",\"v\":\"%s\",\"weight\":%.1f}", e.u, e.v, e.weight));
                if (i < mst.size() - 1) mstJson.append(",");
            }
            mstJson.append("]");
            String json = String.format("{\"mst\":%s,\"totalMstCost\":%.1f}", mstJson.toString(), totalCost);
            sendResponse(exchange, json);
        }
    }

    private static void sendResponse(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(json.getBytes());
        os.close();
    }

    private static double extractParamDouble(String body, String key, double defaultVal) {
        try {
            String marker = "\"" + key + "\":";
            int idx = body.indexOf(marker);
            if (idx == -1) return defaultVal;
            int start = idx + marker.length();
            int end = body.indexOf(",", start);
            if (end == -1) end = body.indexOf("}", start);
            return Double.parseDouble(body.substring(start, end).trim());
        } catch (Exception e) { return defaultVal; }
    }

    private static String extractParamString(String body, String key, String defaultVal) {
        try {
            String marker = "\"" + key + "\":\"";
            int idx = body.indexOf(marker);
            if (idx == -1) return defaultVal;
            int start = idx + marker.length();
            int end = body.indexOf("\"", start);
            return body.substring(start, end).trim();
        } catch (Exception e) { return defaultVal; }
    }
}
