package com.bittorrent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TrackerClient {

    private final HttpClient httpClient;
    private final byte[] peerId;

    public TrackerClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.peerId = generatePeerId();
    }

    // ---------- PUBLIC API ----------

    public List<PeerAddress> getPeers(TorrentInfo torrent, int port, long uploaded, long downloaded, long left)
            throws Exception {
        String url = buildAnnounceUrl(torrent, port, uploaded, downloaded, left);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Transmission/3.00")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
        );

        return parseTrackerResponse(response.body());
    }

    public List<PeerAddress> parseTrackerResponse(byte[] responseBytes) {
        Map<String, Object> response = (Map<String, Object>) Bencoder.decode(responseBytes);

        // Check for tracker failure before attempting to parse peers
        if (response.containsKey("failure reason")) {
            byte[] reason = (byte[]) response.get("failure reason");
            throw new IllegalArgumentException(
                    "Tracker error: " + new String(reason, StandardCharsets.UTF_8)
            );
        }

        byte[] compactPeers = (byte[]) response.get("peers");

        if (compactPeers == null) {
            throw new IllegalArgumentException("Tracker response missing 'peers' field");
        }

        List<PeerAddress> peers = new ArrayList<>();

        for (int i = 0; i + 6 <= compactPeers.length; i += 6) {
            // Extract 4-byte IP address (each byte treated as unsigned)
            String ip = (compactPeers[i]     & 0xFF) + "."
                      + (compactPeers[i + 1] & 0xFF) + "."
                      + (compactPeers[i + 2] & 0xFF) + "."
                      + (compactPeers[i + 3] & 0xFF);

            // Extract 2-byte Big-Endian port number
            int port = ((compactPeers[i + 4] & 0xFF) << 8)
                      | (compactPeers[i + 5] & 0xFF);

            peers.add(new PeerAddress(ip, port));
        }

        return peers;
    }

    public byte[] getPeerId() {
        return peerId;
    }


    private String buildAnnounceUrl(TorrentInfo torrent, int port, long uploaded, long downloaded, long left) {
        return torrent.getAnnounceUrl()
                + "?info_hash=" + urlEncode(torrent.getInfoHash())
                + "&peer_id="   + urlEncode(peerId)
                + "&port="      + port
                + "&uploaded="  + uploaded
                + "&downloaded=" + downloaded
                + "&left="      + left
                + "&compact=1"
                + "&event=started";
    }

    private static String urlEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if ((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') ||
                (b >= '0' && b <= '9') || b == '-' || b == '_' || b == '.' || b == '~') {
                sb.append((char) b);
            } else {
                sb.append(String.format("%%%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    private static byte[] generatePeerId() {
        byte[] id = new byte[20];
        byte[] prefix = "-TR3000-".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, id, 0, prefix.length);

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        for (int i = prefix.length; i < 20; i++) {
            id[i] = (byte) chars.charAt(random.nextInt(chars.length()));
        }
        return id;
    }

    // ---------- NESTED RECORD ----------

    public record PeerAddress(String ip, int port) {}
}
