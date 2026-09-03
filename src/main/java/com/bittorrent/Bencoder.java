package com.bittorrent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Bencoder {

    public static Object decode(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        Cursor cursor = new Cursor();
        Object result = parseValue(data, cursor);

        if (cursor.position != data.length) {
            throw new IllegalArgumentException("Extra data after Bencoded value");
        }

        return result;
    }

    public static byte[] encode(Object data) {
        if (data == null) {
            throw new IllegalArgumentException("Cannot encode null");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        encodeValue(data, output);
        return output.toByteArray();
    }

    // ---------- DECODING ----------

    private static Object parseValue(byte[] data, Cursor cursor) {
        ensureAvailable(data, cursor);

        byte current = data[cursor.position];

        if (current == 'i') return parseInteger(data, cursor);
        if (current == 'l') return parseList(data, cursor);
        if (current == 'd') return parseDictionary(data, cursor);
        if (current >= '0' && current <= '9') {
            return parseByteString(data, cursor);
        }

        throw new IllegalArgumentException(
                "Invalid Bencode token at position " + cursor.position
        );
    }

    private static Long parseInteger(byte[] data, Cursor cursor) {
        cursor.position++; // Skip 'i'
        int start = cursor.position;

        while (true) {
            ensureAvailable(data, cursor);

            if (data[cursor.position] == 'e') break;
            cursor.position++;
        }

        String value = new String(
                data,
                start,
                cursor.position - start,
                StandardCharsets.US_ASCII
        );

        if (value.isEmpty() ||
                value.equals("-0") ||
                value.equals("-") ||
                (value.charAt(0) == '0' && value.length() > 1) ||
                (value.startsWith("-0") && value.length() > 2)) {
            throw new IllegalArgumentException("Invalid integer: " + value);
        }

        try {
            cursor.position++; // Skip 'e'
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer: " + value, e);
        }
    }

    private static byte[] parseByteString(byte[] data, Cursor cursor) {
        int start = cursor.position;

        while (true) {
            ensureAvailable(data, cursor);

            if (data[cursor.position] == ':') break;

            byte current = data[cursor.position];

            if (current < '0' || current > '9') {
                throw new IllegalArgumentException("Invalid byte string length");
            }

            cursor.position++;
        }

        String lengthText = new String(
                data,
                start,
                cursor.position - start,
                StandardCharsets.US_ASCII
        );

        if (lengthText.isEmpty()) {
            throw new IllegalArgumentException("Empty byte string length");
        }

        long length;

        try {
            length = Long.parseLong(lengthText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid byte string length", e);
        }

        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Byte string too large");
        }

        cursor.position++; // Skip ':'

        if (cursor.position + length > data.length) {
            throw new IllegalArgumentException("Unexpected end of byte string");
        }

        byte[] result = new byte[(int) length];

        System.arraycopy(
                data,
                cursor.position,
                result,
                0,
                (int) length
        );

        cursor.position += (int) length;

        return result;
    }

    private static List<Object> parseList(byte[] data, Cursor cursor) {
        cursor.position++; // Skip 'l'

        List<Object> list = new ArrayList<>();

        while (true) {
            ensureAvailable(data, cursor);

            if (data[cursor.position] == 'e') {
                cursor.position++;
                return list;
            }

            list.add(parseValue(data, cursor));
        }
    }

    private static Map<String, Object> parseDictionary(
            byte[] data,
            Cursor cursor
    ) {
        cursor.position++; // Skip 'd'

        Map<String, Object> map = new LinkedHashMap<>();

        while (true) {
            ensureAvailable(data, cursor);

            if (data[cursor.position] == 'e') {
                cursor.position++;
                return map;
            }

            byte current = data[cursor.position];

            if (current < '0' || current > '9') {
                throw new IllegalArgumentException(
                        "Dictionary key must be a byte string"
                );
            }

            byte[] keyBytes = parseByteString(data, cursor);

            String key = new String(
                    keyBytes,
                    StandardCharsets.UTF_8
            );

            Object value = parseValue(data, cursor);

            map.put(key, value);
        }
    }

    // ---------- ENCODING ----------

    private static void encodeValue(
            Object value,
            ByteArrayOutputStream output
    ) {
        if (value instanceof byte[] bytes) {
            encodeByteString(bytes, output);

        } else if (value instanceof String string) {
            encodeByteString(
                    string.getBytes(StandardCharsets.UTF_8),
                    output
            );

        } else if (value instanceof Byte ||
                value instanceof Short ||
                value instanceof Integer ||
                value instanceof Long) {

            output.write('i');
            writeAscii(String.valueOf(value), output);
            output.write('e');

        } else if (value instanceof List<?> list) {
            output.write('l');

            for (Object item : list) {
                encodeValue(item, output);
            }

            output.write('e');

        } else if (value instanceof Map<?, ?> map) {
            encodeDictionary(map, output);

        } else {
            throw new IllegalArgumentException(
                    "Unsupported type: " + value.getClass().getName()
            );
        }
    }

    private static void encodeByteString(
            byte[] bytes,
            ByteArrayOutputStream output
    ) {
        writeAscii(String.valueOf(bytes.length), output);
        output.write(':');
        output.writeBytes(bytes);
    }

    private static void encodeDictionary(
            Map<?, ?> map,
            ByteArrayOutputStream output
    ) {
        output.write('d');

        List<Map.Entry<byte[], Object>> entries = new ArrayList<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            byte[] key;

            if (entry.getKey() instanceof String string) {
                key = string.getBytes(StandardCharsets.UTF_8);

            } else if (entry.getKey() instanceof byte[] bytes) {
                key = bytes;

            } else {
                throw new IllegalArgumentException(
                        "Dictionary key must be String or byte[]"
                );
            }

            entries.add(Map.entry(key, entry.getValue()));
        }

        entries.sort(
                Comparator.comparing(
                        Map.Entry::getKey,
                        Bencoder::compareBytes
                )
        );

        for (Map.Entry<byte[], Object> entry : entries) {
            encodeByteString(entry.getKey(), output);
            encodeValue(entry.getValue(), output);
        }

        output.write('e');
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int length = Math.min(a.length, b.length);

        for (int i = 0; i < length; i++) {
            int first = Byte.toUnsignedInt(a[i]);
            int second = Byte.toUnsignedInt(b[i]);

            if (first != second) {
                return Integer.compare(first, second);
            }
        }

        return Integer.compare(a.length, b.length);
    }

    private static void writeAscii(
            String text,
            ByteArrayOutputStream output
    ) {
        output.writeBytes(
                text.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static void ensureAvailable(
            byte[] data,
            Cursor cursor
    ) {
        if (cursor.position >= data.length) {
            throw new IllegalArgumentException(
                    "Unexpected end of Bencoded data"
            );
        }
    }

    private static class Cursor {
        int position;
    }
}