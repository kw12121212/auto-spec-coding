package org.specdriven.agent.tool;

import org.specdriven.agent.permission.Permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Tool providing Language Server Protocol operations (diagnostics, hover,
 * go-to-definition, references, document symbols) via an external language server.
 */
public class LspTool implements Tool {

    private static final String NAME = "lsp";
    private static final List<String> VALID_OPERATIONS = List.of(
            "diagnostics", "hover", "goToDefinition", "references", "documentSymbols"
    );

    private static final List<ToolParameter> PARAMETERS = List.of(
            new ToolParameter("operation", "string",
                    "LSP operation: diagnostics, hover, goToDefinition, references, documentSymbols", true),
            new ToolParameter("file", "string",
                    "File path (absolute or relative to workDir)", true),
            new ToolParameter("line", "integer",
                    "Line number (0-based) — required for hover, goToDefinition, references", false),
            new ToolParameter("character", "integer",
                    "Character offset (0-based) — required for hover, goToDefinition, references", false),
            new ToolParameter("serverCommand", "string",
                    "Language server command (space-separated). Uses default if omitted.", false),
            new ToolParameter("timeout", "integer",
                    "Per-request timeout in seconds (default: 30)", false)
    );

    private static final Map<String, String> LANGUAGE_MAP = Map.of(
            "java", "java",
            "py", "python",
            "js", "javascript",
            "ts", "typescript",
            "go", "go",
            "rs", "rust",
            "c", "c",
            "cpp", "cpp",
            "h", "c",
            "hpp", "cpp"
    );

    private final String defaultServerCommand;
    private LspClient client;

    public LspTool() {
        this(null);
    }

    public LspTool(String defaultServerCommand) {
        this.defaultServerCommand = defaultServerCommand;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Interacts with a Language Server Protocol server for code intelligence "
                + "(diagnostics, hover, definitions, references, symbols)";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return PARAMETERS;
    }

    @Override
    public Permission permissionFor(ToolInput input, ToolContext context) {
        String operation = getString(input, "operation", "");
        String file = getString(input, "file", "");
        return new Permission("execute", "lsp", Map.of(
                "operation", operation,
                "file", file
        ));
    }

    @Override
    public ToolResult execute(ToolInput input, ToolContext context) {
        // Validate operation
        String operation = getString(input, "operation", null);
        if (operation == null || operation.isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: operation");
        }
        if (!VALID_OPERATIONS.contains(operation)) {
            return new ToolResult.Error("Invalid operation: " + operation + ". Valid: " + VALID_OPERATIONS);
        }

        // Validate file
        String filePath = getString(input, "file", null);
        if (filePath == null || filePath.isBlank()) {
            return new ToolResult.Error("Missing or empty required parameter: file");
        }

        // Resolve file path
        Path resolvedPath = Path.of(filePath);
        if (!resolvedPath.isAbsolute()) {
            resolvedPath = Path.of(context.workDir()).resolve(resolvedPath);
        }
        String fileUri = resolvedPath.toUri().toString();

        // Read file content for didOpen
        String fileContent;
        try {
            fileContent = Files.readString(resolvedPath);
        } catch (IOException e) {
            return new ToolResult.Error("Failed to read file: " + resolvedPath + ": " + e.getMessage());
        }

        String languageId = detectLanguageId(resolvedPath);
        int timeout = getInt(input, "timeout", 30);

        try {
            ensureClient(input, context, timeout);

            client.textDocumentDidOpen(fileUri, languageId, fileContent);
            try {
                ToolResult result = switch (operation) {
                    case "diagnostics" -> executeDiagnostics(fileUri, timeout);
                    case "hover" -> executeHover(fileUri, input);
                    case "goToDefinition" -> executeDefinition(fileUri, input);
                    case "references" -> executeReferences(fileUri, input);
                    case "documentSymbols" -> executeDocumentSymbol(fileUri);
                    default -> new ToolResult.Error("Unsupported operation: " + operation);
                };
                return result;
            } finally {
                client.textDocumentDidClose(fileUri);
            }
        } catch (Exception e) {
            return new ToolResult.Error("LSP operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Close the underlying LSP client connection.
     */
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    // --- Operation implementations ---

    @SuppressWarnings("unchecked")
    private ToolResult executeDiagnostics(String fileUri, int timeout) throws Exception {
        List<Map<String, Object>> diags = client.waitForDiagnostics(fileUri, timeout * 1000L);
        if (diags.isEmpty()) {
            return new ToolResult.Error("No diagnostics received within timeout");
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> diag : diags) {
            if (!sb.isEmpty()) sb.append('\n');
            String severity = formatSeverity(diag.get("severity"));
            String message = String.valueOf(diag.get("message"));
            Map<String, Object> range = (Map<String, Object>) diag.get("range");
            String location = formatRange(range);
            sb.append(severity).append(": ").append(message);
            if (location != null) sb.append(" at ").append(location);
        }
        return new ToolResult.Success(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeHover(String fileUri, ToolInput input) throws Exception {
        Integer line = getOptionalInt(input, "line");
        Integer character = getOptionalInt(input, "character");
        if (line == null || character == null) {
            return new ToolResult.Error("hover requires 'line' and 'character' parameters");
        }
        Map<String, Object> response = client.hover(fileUri, line, character);
        checkResponseError(response);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null) {
            return new ToolResult.Error("No hover information available");
        }
        String content = formatHoverContent(result);
        if (content == null || content.isBlank()) {
            return new ToolResult.Error("No hover information available");
        }
        return new ToolResult.Success(content);
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeDefinition(String fileUri, ToolInput input) throws Exception {
        Integer line = getOptionalInt(input, "line");
        Integer character = getOptionalInt(input, "character");
        if (line == null || character == null) {
            return new ToolResult.Error("goToDefinition requires 'line' and 'character' parameters");
        }
        Map<String, Object> response = client.definition(fileUri, line, character);
        checkResponseError(response);
        Object result = response.get("result");
        String formatted = formatLocations(result);
        if (formatted == null || formatted.isBlank()) {
            return new ToolResult.Error("No definitions found");
        }
        return new ToolResult.Success(formatted);
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeReferences(String fileUri, ToolInput input) throws Exception {
        Integer line = getOptionalInt(input, "line");
        Integer character = getOptionalInt(input, "character");
        if (line == null || character == null) {
            return new ToolResult.Error("references requires 'line' and 'character' parameters");
        }
        Map<String, Object> response = client.references(fileUri, line, character);
        checkResponseError(response);
        Object result = response.get("result");
        String formatted = formatLocations(result);
        if (formatted == null || formatted.isBlank()) {
            return new ToolResult.Error("No references found");
        }
        return new ToolResult.Success(formatted);
    }

    @SuppressWarnings("unchecked")
    private ToolResult executeDocumentSymbol(String fileUri) throws Exception {
        Map<String, Object> response = client.documentSymbol(fileUri);
        checkResponseError(response);
        Object result = response.get("result");
        String formatted = formatDocumentSymbols(result);
        if (formatted == null || formatted.isBlank()) {
            return new ToolResult.Error("No document symbols found");
        }
        return new ToolResult.Success(formatted);
    }

    // --- Client lifecycle ---

    private void ensureClient(ToolInput input, ToolContext context, int timeout) throws Exception {
        if (client != null && client.isInitialized()) return;

        String serverCommand = getString(input, "serverCommand", defaultServerCommand);
        if (serverCommand == null || serverCommand.isBlank()) {
            throw new IllegalStateException(
                    "No language server command configured. Provide 'serverCommand' parameter.");
        }

        List<String> command = List.of(serverCommand.split("\\s+"));
        client = new LspClient(command, timeout);
        String rootUri = Path.of(context.workDir()).toUri().toString();
        client.initialize(rootUri);
    }

    // --- Formatting helpers ---

    private static String formatSeverity(Object severity) {
        if (severity instanceof Number n) {
            return switch (n.intValue()) {
                case 1 -> "Error";
                case 2 -> "Warning";
                case 3 -> "Information";
                case 4 -> "Hint";
                default -> "Diagnostic(" + n + ")";
            };
        }
        return "Diagnostic";
    }

    @SuppressWarnings("unchecked")
    private static String formatRange(Map<String, Object> range) {
        if (range == null) return null;
        Map<String, Object> start = (Map<String, Object>) range.get("start");
        if (start == null) return null;
        Object line = start.get("line");
        Object character = start.get("character");
        if (line == null || character == null) return null;
        return "line " + line + ", char " + character;
    }

    @SuppressWarnings("unchecked")
    private static String formatHoverContent(Map<String, Object> result) {
        Object contents = result.get("contents");
        if (contents == null) return null;

        // MarkupContent: { kind: "markdown"|"plaintext", value: "..." }
        if (contents instanceof Map<?, ?> map) {
            Object value = map.get("value");
            if (value != null) return value.toString();
        }

        // MarkedString (string)
        if (contents instanceof String s) return s;

        // MarkedString[] or MarkedString with language
        if (contents instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (!sb.isEmpty()) sb.append("\n---\n");
                if (item instanceof String s) {
                    sb.append(s);
                } else if (item instanceof Map<?, ?> m) {
                    Object value = m.get("value");
                    if (value != null) sb.append(value);
                }
            }
            return sb.toString();
        }

        return contents.toString();
    }

    @SuppressWarnings("unchecked")
    private static String formatLocations(Object result) {
        if (result == null) return null;

        // Location
        if (result instanceof Map<?, ?> map) {
            return formatLocation((Map<String, Object>) map);
        }

        // Location[]
        if (result instanceof List<?> list) {
            if (list.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    if (!sb.isEmpty()) sb.append('\n');
                    sb.append(formatLocation((Map<String, Object>) m));
                }
            }
            return sb.toString();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static String formatLocation(Map<String, Object> loc) {
        String uri = (String) loc.get("uri");
        Map<String, Object> range = (Map<String, Object>) loc.get("range");
        String path = uri != null ? uriToPath(uri) : "?";
        if (range != null) {
            Map<String, Object> start = (Map<String, Object>) range.get("start");
            if (start != null) {
                return path + ":" + start.get("line") + ":" + start.get("character");
            }
        }
        return path;
    }

    private static String uriToPath(String uri) {
        if (uri.startsWith("file://")) return uri.substring(7);
        return uri;
    }

    @SuppressWarnings("unchecked")
    private static String formatDocumentSymbols(Object result) {
        if (result == null) return null;
        if (!(result instanceof List<?> list) || list.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> sym = (Map<String, Object>) m;
                if (!sb.isEmpty()) sb.append('\n');
                String name = String.valueOf(sym.get("name"));
                String kind = formatSymbolKind(sym.get("kind"));
                Map<String, Object> range = (Map<String, Object>) sym.get("range");
                String location = formatRange(range);
                sb.append(kind).append(" ").append(name);
                if (location != null) sb.append(" at ").append(location);
            }
        }
        return sb.toString();
    }

    private static String formatSymbolKind(Object kind) {
        if (kind instanceof Number n) {
            return switch (n.intValue()) {
                case 1 -> "File";
                case 2 -> "Module";
                case 3 -> "Namespace";
                case 4 -> "Package";
                case 5 -> "Class";
                case 6 -> "Method";
                case 7 -> "Property";
                case 8 -> "Field";
                case 9 -> "Constructor";
                case 10 -> "Enum";
                case 11 -> "Interface";
                case 12 -> "Function";
                case 13 -> "Variable";
                case 14 -> "Constant";
                case 15 -> "String";
                case 16 -> "Number";
                case 17 -> "Boolean";
                case 18 -> "Array";
                case 19 -> "Object";
                case 20 -> "Key";
                case 21 -> "Null";
                case 22 -> "EnumMember";
                case 23 -> "Struct";
                case 24 -> "Event";
                case 25 -> "Operator";
                case 26 -> "TypeParameter";
                default -> "Symbol(" + n + ")";
            };
        }
        return "Symbol";
    }

    // --- Parameter helpers ---

    private static String getString(ToolInput input, String key, String defaultValue) {
        Object value = input.parameters().get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static int getInt(ToolInput input, String key, int defaultValue) {
        Object value = input.parameters().get(key);
        return value instanceof Number n ? n.intValue() : defaultValue;
    }

    private static Integer getOptionalInt(ToolInput input, String key) {
        Object value = input.parameters().get(key);
        return value instanceof Number n ? n.intValue() : null;
    }

    private static String detectLanguageId(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "";
        String ext = fileName.substring(dot + 1);
        return LANGUAGE_MAP.getOrDefault(ext, ext);
    }

    private static void checkResponseError(Map<String, Object> response) throws Exception {
        Object error = response.get("error");
        if (error instanceof Map<?, ?> err) {
            throw new RuntimeException("LSP error " + err.get("code") + ": " + err.get("message"));
        }
    }
}
