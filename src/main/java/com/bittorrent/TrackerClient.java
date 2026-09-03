package com.bittorrent;

import java.util.List;
import java.net.http.HttpClient;

public class TrackerClient {

    private final HttpClient httpClient;

    public TrackerClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<PeerAddress> getPeers(TorrentInfo torrent, byte[] myPeerId, int port, long uploaded, long downloaded, long left) {
        // TODO: Implement HTTP GET request and parse compact peer response (Milestone 3)
        // Remember to check for "failure reason" before parsing peers!
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // Nested record for Peer representation
    public record PeerAddress(String ip, int port) {}
}
