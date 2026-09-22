# Networking, Pipelining & Tit-for-Tat

The networking layer (`PeerConnection.java`) is where the actual BitTorrent protocol is negotiated. 

## 1. Peer Connection Lifecycle
Every IP address received from the tracker is handed to a brand new `PeerConnection` object running in a Virtual Thread. Here is the strict sequence of messages they must exchange before any file data can be downloaded:


## 2. Aggressive Pipelining (`MAX_PIPELINE = 20`)
A naive, beginner BitTorrent client requests a 16 KB block, waits for it to arrive, and then requests the next one. If the peer is located across the world and has a 100ms ping latency, your maximum speed is capped at a dismal 160 KB/s regardless of how fast your internet is.

Our client implements **Network Pipelining**. As soon as we are unchoked, we fire up to `20` concurrent `REQUEST` messages into the socket buffer without waiting for a response. As each block arrives, the pipeline immediately refills, ensuring the peer's upload pipe is constantly saturated. This allows speeds to scale into the tens of Megabytes per second per peer.

## 3. The Tit-for-Tat Algorithm
BitTorrent survives because it forces users to share. If you don't upload to others, they will "choke" you and refuse to send you data.

In `Main.java`, a loop runs every 10 active seconds to evaluate our relationships:
1. It looks at all active `PeerConnection` objects.
2. It asks each connection: *"How many bytes did you send me in the last 10 seconds?"*
3. It sorts the peers from fastest to slowest.
4. The top 3 fastest peers are explicitly sent an `UNCHOKE` message (allowing them to download from us as a reward). 
5. All other peers are sent a `CHOKE` message (cutting off their download access).

## 4. Swarm Replenishment
A BitTorrent swarm is highly volatile. Peers constantly disconnect, finish downloading, or crash. 
The same 10-second loop constantly monitors the `aliveCount` of our socket connections. If the number of active sockets drops below `40`, the client automatically fires a new HTTP request to the tracker to ask for a fresh batch of 50 IPs. This guarantees the swarm stays fully populated until the download reaches 100%.
