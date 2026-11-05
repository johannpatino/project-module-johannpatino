package io.everyonecodes.deliciousnessness.security;

import java.net.URI;
import java.net.URISyntaxException;

// Recipe links end up in href and src attributes. Only http and https are allowed, so a
// "javascript:" or "data:" link can never run in a visitor's browser.
public final class WebLinks {

    private WebLinks() {
    }

    // Blank is fine: the link is optional.
    public static boolean isSafe(String link) {
        if (link == null || link.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(link.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
