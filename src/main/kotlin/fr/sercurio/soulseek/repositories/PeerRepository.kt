package fr.sercurio.soulseek.repositories

import fr.sercurio.soulseek.entities.PeerApiModel
import fr.sercurio.soulseek.client.ClientSocket
import fr.sercurio.soulseek.client.TransferSocket
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PeerRepository {
    private val peersMutex = Mutex()
    var peers: MutableMap<String, PeerApiModel> = mutableMapOf()

    suspend fun addOrUpdatePeer(peer: PeerApiModel) {
        peersMutex.withLock {
            peers[peer.username] = peer
        }
    }

    suspend fun initiateClientSocket(peer: PeerApiModel) {
        peer.clientSocket = ClientSocket(peer)
        peersMutex.withLock {
            peers[peer.username] = peer
        }
    }

    suspend fun initiateTransferSocket(peer: PeerApiModel) {
        peer.transferSocket = TransferSocket(peer)
        peersMutex.withLock {
            peers[peer.username] = peer
        }
    }
}