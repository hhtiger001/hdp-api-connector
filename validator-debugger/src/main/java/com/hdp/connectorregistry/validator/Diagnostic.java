package com.hdp.connectorregistry.validator;

public record Diagnostic(DiagnosticSeverity severity, String code, String message) {}
