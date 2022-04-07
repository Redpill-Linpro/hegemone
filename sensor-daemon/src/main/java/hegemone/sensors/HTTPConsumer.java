package hegemone.sensors;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HTTPConsumer implements DataConsumer {
    String targetUrl;

    public HTTPConsumer(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    @Override
    public void accept(String data) {
        try {
            var req = createPostRequest(data);
            var resp = HttpClient.newBuilder()
                    .build()
                    .sendAsync(req, HttpResponse.BodyHandlers.ofString());
            /* TODO: handle errors(!) */
        } catch (Exception e) {
            System.err.println("Couldn't send POST data \n" + e);
        }
    }

    private HttpRequest createPostRequest(String data) throws URISyntaxException {
        return HttpRequest.newBuilder()
                .uri(new URI(targetUrl))
                .headers("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .build();
    }
}
