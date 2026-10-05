package com.yourcompany.testing.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yourcompany.testing.application.exception.CatalogException;
import com.yourcompany.testing.application.port.ComponentCatalogRepository;
import com.yourcompany.testing.domain.catalog.ComponentCatalog;
import com.yourcompany.testing.domain.catalog.PageUrl;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

public final class JsonComponentCatalogRepository implements ComponentCatalogRepository {
    private final Path catalogRoot;
    private final ObjectMapper mapper;

    public JsonComponentCatalogRepository(Path projectRoot, String catalogDirectory) {
        if (projectRoot == null) throw new IllegalArgumentException("Project root is required");
        if (StringUtils.isBlank(catalogDirectory)) throw new IllegalArgumentException("Catalog directory is required");

        this.catalogRoot = projectRoot.toAbsolutePath().normalize().resolve(catalogDirectory).normalize();

        if (!catalogRoot.startsWith(projectRoot.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Catalog directory must remain inside the project root");

        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Optional<ComponentCatalog> find(PageUrl pageUrl) {
        Path catalogPath = pathFor(pageUrl);

        if (!Files.isRegularFile(catalogPath)) return Optional.empty();

        try {
            JsonNode catalogJson = mapper.readTree(catalogPath.toFile());

            ComponentCatalog catalog = mapper.treeToValue(catalogJson, ComponentCatalog.class);

            if (!pageUrl.normalizedIdentity().equals(catalog.pageUrl()))
                throw new CatalogException("Catalog URL identity does not match requested page: " + catalogPath);

            return Optional.of(catalog);
        } catch (IOException exception) {
            throw new CatalogException("Unable to load component catalog: " + catalogPath, exception);
        } catch (RuntimeException exception) {
            if (exception instanceof CatalogException catalogException) throw catalogException;

            throw new CatalogException("Unable to load current-format component catalog: " + catalogPath, exception);
        }
    }

    @Override
    public void save(PageUrl pageUrl, ComponentCatalog catalog) {
        if (!pageUrl.normalizedIdentity().equals(catalog.pageUrl()))
            throw new CatalogException("Refusing to save a catalog under a different page identity");

        Path catalogPath = pathFor(pageUrl);

        try {
            Files.createDirectories(catalogPath.getParent());
            Path temporaryPath = catalogPath.resolveSibling(catalogPath.getFileName() + ".tmp");

            mapper.writeValue(temporaryPath.toFile(), catalog);

            try {
                Files.move(temporaryPath, catalogPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, catalogPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new CatalogException("Unable to save component catalog: " + catalogPath, exception);
        }
    }

    public Path pathFor(PageUrl pageUrl) {
        URI uri = URI.create(pageUrl.normalizedIdentity());

        String host = slug(uri.getHost());
        String page = pageSlug(uri.getPath());
        String identityHash = shortHash(pageUrl.normalizedIdentity());

        return catalogRoot.resolve(host).resolve(page + "-" + identityHash + ".json");
    }

    private String pageSlug(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) return "home";
        String slug = slug(path);
        return slug.isBlank() ? "page" : slug;
    }

    private String slug(String value) {
        String normalizedValue = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalizedValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create deterministic catalog identity", exception);
        }
    }
}
