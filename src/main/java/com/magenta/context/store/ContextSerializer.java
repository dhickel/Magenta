package com.magenta.context.store;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class ContextSerializer {
    private static final String SEPARATOR = "|||";
    private static final String LINE_BREAK = "\n###CHUNK###\n";

    public static String serialize(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID:").append(context.getId()).append(LINE_BREAK);
        sb.append(serializeElements(context.getElements()));
        return sb.toString();
    }

    public static Context deserialize(String data) {
        if (data == null || data.isEmpty()) return new Context("unknown");

        String[] parts = data.split(LINE_BREAK);
        String id = "unknown";
        List<ContextElement> elements = new ArrayList<>();

        for (String part : parts) {
            if (part.startsWith("ID:")) {
                id = part.substring(3);
            } else if (!part.isBlank()) {
                // Determine if this part belongs to the list of elements
                // The serializeElements method joins with LINE_BREAK
                // So splitting by LINE_BREAK works for the top level list
                ContextElement e = deserializeElement(part);
                if (e != null) {
                    elements.add(e);
                }
            }
        }
        return new Context(id, elements);
    }

    // Helper to serialize a list of elements into a string compatible with the top-level format
    // or for nesting.
    private static String serializeElements(List<ContextElement> elements) {
        StringBuilder sb = new StringBuilder();
        for (ContextElement element : elements) {
            sb.append(serializeElement(element)).append(LINE_BREAK);
        }
        return sb.toString();
    }

    private static String serializeElement(ContextElement element) {
        if (element instanceof ContextElement.System s) {
            return "SYSTEM" + SEPARATOR + encode(s.content());
        } else if (element instanceof ContextElement.User u) {
            return "USER" + SEPARATOR + encode(u.content());
        } else if (element instanceof ContextElement.Assistant a) {
            return "ASSISTANT" + SEPARATOR + encode(a.content());
        } else if (element instanceof ContextElement.Tool t) {
            return "TOOL" + SEPARATOR + encode(t.toolName()) + SEPARATOR + encode(t.content());
        } else if (element instanceof ContextElement.Summary s) {
            // Serialize the nested list, then encode it to keep it safe
            String serializedOriginals = serializeElements(s.originalElements());
            return "SUMMARY" + SEPARATOR + encode(s.summary()) + SEPARATOR + encode(s.originalContextKey()) + SEPARATOR + encode(serializedOriginals);
        }
        return "UNKNOWN" + SEPARATOR;
    }

    private static ContextElement deserializeElement(String data) {
        if (data == null || data.isBlank()) return null;
        String[] parts = data.split("\\|\\|\\");
        if (parts.length == 0) return null;
        
        String type = parts[0];

        try {
            switch (type) {
                case "SYSTEM":
                    return new ContextElement.System(decode(parts[1]));
                case "USER":
                    return new ContextElement.User(decode(parts[1]));
                case "ASSISTANT":
                    return new ContextElement.Assistant(decode(parts[1]));
                case "TOOL":
                    return new ContextElement.Tool(decode(parts[1]), decode(parts[2]));
                case "SUMMARY":
                    String summary = decode(parts[1]);
                    String key = decode(parts[2]);
                    List<ContextElement> originals = Collections.emptyList();
                    if (parts.length > 3) {
                        String serializedOriginals = decode(parts[3]);
                        originals = deserializeElementsList(serializedOriginals);
                    }
                    return new ContextElement.Summary(summary, key, originals);
                default:
                    // Unknown type, ignore or return system error
                    return null;
            }
        } catch (Exception e) {
            return new ContextElement.System("Error deserializing element: " + e.getMessage());
        }
    }

    private static List<ContextElement> deserializeElementsList(String data) {
        if (data == null || data.isEmpty()) return Collections.emptyList();
        List<ContextElement> elements = new ArrayList<>();
        String[] parts = data.split(LINE_BREAK);
        for (String part : parts) {
            if (!part.isBlank()) {
                ContextElement e = deserializeElement(part);
                if (e != null) {
                    elements.add(e);
                }
            }
        }
        return elements;
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    private static String decode(String s) {
        return new String(Base64.getDecoder().decode(s));
    }
}