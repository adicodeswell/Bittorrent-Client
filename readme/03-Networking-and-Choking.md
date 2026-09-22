# Networking, Pipelining & Tit-for-Tat

The networking layer is where the actual BitTorrent protocol is negotiated. 

## 1. Peer Connection Lifecycle (`PeerConnection.java`)
Every IP address received from the tracker is handed to a brand new `PeerConnection` object running in a Virtual Thread.
1. **TCP Handshake:** We send a specific 68-byte handshake (Protocol Name, Info Hash, Peer ID).
2. **Bitfield Exchange:** The peer sends us a `BITFIELD` message (a bit array representing exactly which pieces of the file they currently possess).
3. **Interest:** If they have pieces we need, we send an `INTERESTED` message.
4. **Unchoke:** We wait for the peer to send us an `UNCHOKE` message, signaling we are allowed to request data.

## 2. Aggressive Pipelining (`MAX_PIPELINE = 20`)
A naive BitTorrent client requests a 16 KB block, waits for it to arrive, and then requests the next one. If the peer has a 100ms ping latency, your maximum speed is capped at a dismal 160 KB/s regardless of your actual internet speed.
Our client implements **Network Pipelining**. As soon as we are unchoked, we fire up to `20` concurrent `REQUEST` messages into the socket buffer without waiting for a response. As each block arrives, the pipeline immediately refills, ensuring the peer's upload pipe is constantly saturated. This allows speeds to scale into the tens of Megabytes per second.

## 3. The Tit-for-Tat Algorithm
BitTorrent survives because it forces users to share. If you don't upload to others, they will "choke" you and refuse to send you data.
In `Main.java`, a loop runs every 10 active seconds:
1. It looks at all active `PeerConnection` objects.
2. It calculates exactly how many bytes each peer successfully transferred to us in the last 10 seconds (`getAndResetDownloadedBytes()`).
3. It sorts the peers by speed.
4. The top 3 fastest peers are explicitly sent an `UNCHOKE` message (allowing them to download from us). 
5. All other peers are sent a `CHOKE` message (cutting them off).
*(Note: A 4th "Optimistic Unchoke" slot should be randomized to discover potentially faster peers, but the core foundation is built).*

## 4. Swarm Replenishment
A BitTorrent swarm is highly volatile. Peers constantly disconnect, finish downloading, or crash. 
The same 10-second loop in `Main.java` constantly monitors the `aliveCount` of our connections. If the number of active sockets drops below `40`, the client automatically fires a new HTTP request to the tracker to ask for a fresh batch of 50 IPs, keeping the swarm fully populated until the download reaches 100%.
