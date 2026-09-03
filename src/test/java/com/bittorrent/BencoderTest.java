package com.bittorrent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class BencoderTest {

    @Test
    public void testDecodeString() {
        byte[] input = "4:spam".getBytes(StandardCharsets.US_ASCII);
        Object decoded = Bencoder.decode(input);
        
        assertInstanceOf(byte[].class, decoded);
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) decoded);
    }

    @Test
    public void testEncodeString() {
        byte[] input = "spam".getBytes(StandardCharsets.US_ASCII);
        byte[] encoded = Bencoder.encode(input);
        
        assertArrayEquals("4:spam".getBytes(StandardCharsets.US_ASCII), encoded);
    }

    @Test
    public void testDecodeInteger() {
        byte[] input = "i3e".getBytes(StandardCharsets.US_ASCII);
        Object decoded = Bencoder.decode(input);
        
        assertInstanceOf(Long.class, decoded);
        assertEquals(3L, decoded);
    }

    @Test
    public void testDecodeNegativeInteger() {
        byte[] input = "i-3e".getBytes(StandardCharsets.US_ASCII);
        Object decoded = Bencoder.decode(input);
        
        assertInstanceOf(Long.class, decoded);
        assertEquals(-3L, decoded);
    }
    
    @Test
    public void testDecodeInvalidInteger_NegativeZero() {
        byte[] input = "i-0e".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> Bencoder.decode(input));
    }

    @Test
    public void testDecodeInvalidInteger_LeadingZeroes() {
        byte[] input = "i03e".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> Bencoder.decode(input));
    }

    @Test
    public void testEncodeInteger() {
        byte[] encoded = Bencoder.encode(42L);
        assertArrayEquals("i42e".getBytes(StandardCharsets.US_ASCII), encoded);
    }

    @Test
    public void testDecodeList() {
        byte[] input = "l4:spam4:eggse".getBytes(StandardCharsets.US_ASCII);
        Object decoded = Bencoder.decode(input);
        
        assertInstanceOf(List.class, decoded);
        List<?> list = (List<?>) decoded;
        assertEquals(2, list.size());
        assertArrayEquals("spam".getBytes(StandardCharsets.US_ASCII), (byte[]) list.get(0));
        assertArrayEquals("eggs".getBytes(StandardCharsets.US_ASCII), (byte[]) list.get(1));
    }

    @Test
    public void testEncodeList() {
        List<Object> list = List.of(
            "spam".getBytes(StandardCharsets.US_ASCII),
            "eggs".getBytes(StandardCharsets.US_ASCII)
        );
        byte[] encoded = Bencoder.encode(list);
        
        assertArrayEquals("l4:spam4:eggse".getBytes(StandardCharsets.US_ASCII), encoded);
    }

    @Test
    public void testDecodeDictionary() {
        byte[] input = "d3:cow3:moo4:spam4:eggse".getBytes(StandardCharsets.US_ASCII);
        Object decoded = Bencoder.decode(input);
        
        assertInstanceOf(Map.class, decoded);
        Map<?, ?> map = (Map<?, ?>) decoded;
        assertEquals(2, map.size());
        assertTrue(map.containsKey("cow"));
        assertTrue(map.containsKey("spam"));
        
        assertArrayEquals("moo".getBytes(StandardCharsets.US_ASCII), (byte[]) map.get("cow"));
        assertArrayEquals("eggs".getBytes(StandardCharsets.US_ASCII), (byte[]) map.get("spam"));
    }

    @Test
    public void testEncodeDictionary() {
        // Use LinkedHashMap to test sorting logic
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("spam", "eggs".getBytes(StandardCharsets.US_ASCII));
        map.put("cow", "moo".getBytes(StandardCharsets.US_ASCII));
        
        byte[] encoded = Bencoder.encode(map);
        
        // Output should be sorted: cow comes before spam
        assertArrayEquals("d3:cow3:moo4:spam4:eggse".getBytes(StandardCharsets.US_ASCII), encoded);
    }

    @Test
    public void testSymmetry_ComplexNested() {
        byte[] input = "d4:dictd5:hello5:worlde4:listli1ei2ei3ee6:numberi42e6:string11:hello worlde".getBytes(StandardCharsets.US_ASCII);
        
        Object decoded = Bencoder.decode(input);
        byte[] encoded = Bencoder.encode(decoded);
        
        assertArrayEquals(input, encoded, "Encoded bytes should exactly match the original input bytes");
    }
}
