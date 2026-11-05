package com.recipeagent.flyer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Gets a store's flyer from the web (F10). The address comes from FLYER_URL_TEMPLATE in .env,
 * with {store} replaced by the store name, e.g. https://example.com/flyers/{store}.txt.
 * Stores don't offer a common free flyer API, so this is optional: when no address is set,
 * or the page can't be reached, the user is asked to upload the flyer file instead (UC10 1a).
 */
public class OnlineFlyerSource implements FlyerSource {

    static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String urlTemplate;
    private final HttpClient http;

    /** @param urlTemplate the address with {store}, or null when online flyers are not set up */
    public OnlineFlyerSource(String urlTemplate) {
        this(urlTemplate, HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    OnlineFlyerSource(String urlTemplate, HttpClient http) {
        this.urlTemplate = urlTemplate == null || urlTemplate.isBlank() ? null : urlTemplate.trim();
        this.http = http;
    }

    /** True if an address is set in .env. */
    public boolean isConfigured() {
        return urlTemplate != null;
    }

    /** The address for a store, e.g. ".../No%20Frills.txt", or null if not set up. */
    String urlFor(String store) {
        if (urlTemplate == null) {
            return null;
        }
        String encoded = URLEncoder.encode(store.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        return urlTemplate.replace("{store}", encoded);
    }

    @Override
    public String fetch(String store) throws FlyerException {
        String url = urlFor(store);
        if (url == null) {
            throw new FlyerException("Online flyers are not set up, so please upload the " + store
                    + " flyer as a file.", true);
        }
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .header("Accept", "text/plain, text/html").GET().build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new FlyerException("The online flyer address for " + store + " is not valid. "
                    + "Check FLYER_URL_TEMPLATE in .env, or upload the flyer file.", true);
        } catch (IOException e) {
            throw new FlyerException("The online flyer for " + store + " could not be reached. "
                    + "Upload the flyer file instead.", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlyerException("Getting the online flyer for " + store + " was stopped.", true);
        }
        if (response.statusCode() != 200) {
            throw new FlyerException("The online flyer for " + store + " is not available (error "
                    + response.statusCode() + "). Upload the flyer file instead.", true);
        }
        String text = FlyerText.clean(response.body() == null ? "" : response.body());
        if (text.isBlank()) {
            throw new FlyerException("The online flyer for " + store + " was empty. Upload the flyer file instead.", true);
        }
        return text;
    }

    @Override
    public String describe() {
        return isConfigured() ? "the online flyer" : "online flyers (not set up)";
    }
}
