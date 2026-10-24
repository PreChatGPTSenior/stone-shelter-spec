package lab.stoneshelter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class StoneDocumentHttpTestSupport implements AutoCloseable {
    private final HttpServer httpServer;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();

    StoneDocumentHttpTestSupport(Handler handler) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.setExecutor(executorService);
        httpServer.createContext("/api/tags", httpExchange -> {
            try (httpExchange) {
                respond(httpExchange, 200, "{\"models\":[{\"name\":\"qwen3-embedding:0.6b\",\"digest\":\"qualified-digest\"}]}");
            }
        });
        httpServer.createContext("/api/embed", httpExchange -> {
            requests.incrementAndGet();
            try (httpExchange) {
                request.set(jsonMapper.readTree(httpExchange.getRequestBody().readAllBytes()));
                handler.handle(httpExchange);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        httpServer.start();
    }

    void source(Handler handler) {
        httpServer.createContext("/stone-encyclopedia/stone-encyclopedia_en.md", httpExchange -> {
            try (httpExchange) { handler.handle(httpExchange); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        });
    }

    String baseUrl() {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    JsonNode request() { return request.get(); }
    int requests() { return requests.get(); }

    void modelMetadata(Handler handler) {
        httpServer.removeContext("/api/tags");
        httpServer.createContext("/api/tags", httpExchange -> {
            try (httpExchange) {
                handler.handle(httpExchange);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    static void respond(HttpExchange httpExchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        httpExchange.getResponseHeaders().set("Content-Type", body.startsWith("<Error>") ? "application/xml" : "application/json");
        httpExchange.sendResponseHeaders(status, bytes.length);
        httpExchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        httpServer.stop(0);
        executorService.shutdownNow();
    }

    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange httpExchange) throws IOException, InterruptedException;
    }
}
