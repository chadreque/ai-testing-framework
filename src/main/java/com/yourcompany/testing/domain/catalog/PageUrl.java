package com.yourcompany.testing.domain.catalog;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public record PageUrl(String value) {
    public PageUrl {
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException("Page URL is required");

        URI uri = URI.create(value.trim());

        if (!StringUtils.equalsIgnoreCase("http", uri.getScheme()) && !StringUtils.equalsIgnoreCase("https", uri.getScheme())) throw new IllegalArgumentException("Page URL must use HTTP or HTTPS");
        if (StringUtils.isBlank(uri.getHost())) throw new IllegalArgumentException("Page URL must contain a host");
        if (StringUtils.isNotBlank(uri.getUserInfo())) throw new IllegalArgumentException("Page URL must not contain embedded credentials");

        value = value.trim();
    }

    public String normalizedIdentity() {
        URI uri = URI.create(value);

        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath().replaceAll("/{2,}", "/");

        int port = uri.getPort();

        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        return scheme + "://" + host + (port == -1 ? "" : ":" + port) + path;
    }
}
