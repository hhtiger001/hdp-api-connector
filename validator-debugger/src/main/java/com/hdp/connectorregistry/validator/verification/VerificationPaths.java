package com.hdp.connectorregistry.validator.verification;

import java.nio.file.Files;
import java.nio.file.Path;

final class VerificationPaths {
    private VerificationPaths() {}

    static Path connectorFile(Path connectorPath) {
        return Files.isDirectory(connectorPath) ? connectorPath.resolve("connector.json") : connectorPath;
    }

    static Path connectorDirectory(Path connectorPath) {
        Path connectorFile = connectorFile(connectorPath).toAbsolutePath().normalize();
        Path parent = connectorFile.getParent();
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }
}
