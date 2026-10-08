import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/** Answers GET /client.json with the document named on the command line, over https on port 443. */
void main(String[] args) throws Exception {

    var keys = KeyStore.getInstance("PKCS12");
    try (var in = new FileInputStream(args[0])) {
        keys.load(in, "e2e-only-key".toCharArray());
    }
    var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(keys, "e2e-only-key".toCharArray());
    var tls = SSLContext.getInstance("TLS");
    tls.init(managers.getKeyManagers(), null, null);
    var document = Files.readAllBytes(Path.of(args[1]));
    var server = HttpsServer.create(new InetSocketAddress(443), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls));
    server.createContext("/client.json", exchange -> {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, document.length);
        try (var out = exchange.getResponseBody()) {
            out.write(document);
        }
    });
    server.start();
}
