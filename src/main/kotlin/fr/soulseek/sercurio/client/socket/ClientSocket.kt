package fr.sercurio.soulseekapi.socket

import fr.sercurio.soulseekapi.entities.ByteMessage
import fr.sercurio.soulseekapi.entities.PeerApiModel
import fr.sercurio.soulseekapi.entities.SoulFile
import fr.sercurio.soulseekapi.repositories.PeerRepository
import fr.sercurio.soulseekapi.toInt
import kotlinx.coroutines.runBlocking
import fr.sercurio.soulseekapi.utils.SoulStack
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream


class ClientSocket(private val peer: PeerApiModel) : SoulSocket(peer.host, peer.port) {
    private val askedFiles = mutableMapOf<String, SoulFile>()

    init {
        Thread(this).start()
    }

    override fun onSocketConnected() {
        println("Connected to $peer")

        pierceFirewall(peer.token)
        //TODO changer ca pour recuperer l'username utilisé dans l'appli
        peerInit("DebugApp", "P", peer.token)
        //getShareFileList()
        //userInfoRequest()
        val actualSearch = SoulStack.searches[SoulStack.actualSearchToken] ?: ""
        fileSearchRequest(peer.token, actualSearch)
    }

    override fun onSocketDisconnected() {
        println("Disconnected of $peer")
        this.peer.clientSocket = null
        this.stop()
    }

    override fun onMessageReceived() {
        runBlocking {
            try {
                soulInput.readAndSetMessageLength()
                val code = soulInput.readInt()
                println("PeerClient received: Message code:" + code + " Packet Size:" + (soulInput.packLeft + 4))

                when (code) {
                    4 -> receiveSharesRequest()
                    5 -> receiveSharesReply()
                    8 -> receiveSearchRequest()
                    9 -> receiveSearchReply()
                    15 -> receiveInfoRequest()
                    16 -> receiveInfoReply()
                    36 -> receiveFolderContentsRequest()
                    37 -> receiveFolderContentsReply()
                    40 -> receiveTransferRequest()
                    41 -> receiveTransferReply()
                    43 -> receiveQueueDownload()
                    44 -> receivePlaceInQueueReply()
                    46 -> receiveUploadFailed()
                    50 -> receiveQueueFailed()
                    51 -> receivePlaceInQueueRequest()
                    52 -> receiveUploadQueueNotification()
                }
            } catch (e: EOFException) {
                println("EOFException ! $peer")
                throw e
            }
        }
    }
    /*
    {
                        override fun onAbstractGetSharedList() {
                            //peerSocketManagerInterface.onGetSharedList()
                        }

                        override fun onAbstractFileSearchResult(peer: Peer) {
                            peer.searchResults = true
                            println("actual token: ${SoulStack.actualSearchToken} peer token : ${peer.token}")
                            //if (SoulStack.actualSearchToken == peer.token)
                            //peerSocketManagerInterface.onFileSearchResult(peer)
                        }

                        override fun onAbstractFolderContentsRequest(numberOfFiles: Int) {
                            //peerSocketManagerInterface.onFolderContentsRequest(numberOfFiles)
                        }

                        override fun onAbstractTransferDownloadRequest(
                            token: Long,
                            allowed: Int,
                            reason: String?
                        ) {
                            //peerSocketManagerInterface.onTransferDownloadRequest(token, allowed, reason)
                        }

                        override fun onAbstractUploadFailed(filename: String) {
                            //peerSocketManagerInterface.onUploadFailed(filename)
                        }

                        override fun onAbstractQueueFailed(filename: String?, reason: String) {
                            //peerSocketManagerInterface.onQueueFailed(filename, reason)
                        }
                    }.also { PeerManager.executorService.submit(it) }
                }
     */


    private fun receiveSharesRequest() {
        println("received share request")
        //sendSharesReply()
    }


    private fun receiveSharesReply() {
        val inflatedInputStream = DataInputStream(InflaterInputStream(soulInput.dis))
        println("Loading " + this.peer.username + " shares.")
        val nDirs = soulInput.readInt(inflatedInputStream)
        //val hashMap: HashMap<String?, ShareDirectory?> = HashMap<Any?, Any?>(nDirs)
        println("Loading $nDirs folders.")
        for (i in 0 until nDirs) {
            val dirName = soulInput.readString(inflatedInputStream)
            /*val parent: ShareDirectory? = hashMap[Util.getFolderPath(dirName)] as ShareDirectory?
            val currentDir = ShareDirectory(dirName, parent)
            hashMap[dirName] = currentDir
            if (parent == null) {
                this.service.rootShare.put(this.peerName, currentDir)
            }*/
            val nFiles = soulInput.readInt(inflatedInputStream)
            for (j in 0 until nFiles) {
                soulInput.readByte(inflatedInputStream)
                val filename = soulInput.readString(inflatedInputStream)
                val fileSize = soulInput.readLong(inflatedInputStream)
                soulInput.readString(inflatedInputStream)
                val nAttributes = soulInput.readInt(inflatedInputStream)
                var bitrate = 0
                var length = 0
                var vbr = 0
                for (k in 0 until nAttributes) {
                    when (soulInput.readInt(inflatedInputStream)) {
                        0 -> bitrate = soulInput.readInt(inflatedInputStream)
                        1 -> length = soulInput.readInt(inflatedInputStream)
                        2 -> vbr = soulInput.readInt(inflatedInputStream)
                        else -> soulInput.readInt(inflatedInputStream)
                    }
                }
                //val shareFile = ShareFile(currentDir, filename, filesize, bitrate, length, vbr)
                println("decodeFiles: $dirName, $filename")
            }
        }
        println("Finished Loading " + this.peer.username + " shares.")
    }


    private fun receiveSearchRequest() {
        val ticket = soulInput.readInt()
        val query = soulInput.readString()
        /*val cursor: Cursor = GoSeekData.searchShares(query)
        if (cursor != null) {
            sendSearchReply(ticket, query, cursor)
        }*/
    }


    private suspend fun receiveSearchReply() {
        val soulFiles = arrayListOf<SoulFile>()

        val inflatedInputStream = DataInputStream(InflaterInputStream(soulInput.dis))

        val user = soulInput.readString(inflatedInputStream)
        val ticket = soulInput.readInt(inflatedInputStream)
        var path = ""
        var size: Long
        var extension = ""
        var bitrate = 0
        var duration = 0
        var vbr = 0
        val slotsFree: Boolean
        val avgSpeed: Int
        val queueLength: Long
        if (true /*TODO search the ticket*/) {
            val nResults = soulInput.readInt(inflatedInputStream)
            for (i in 0 until nResults) {
                soulInput.readBoolean(inflatedInputStream) //unused
                path = soulInput.readString(inflatedInputStream).replace("\\", "/")
                size = soulInput.readLong(inflatedInputStream)
                extension = soulInput.readString(inflatedInputStream)
                val nAttr = soulInput.readInt(inflatedInputStream)
                for (j in 0 until nAttr) {
                    when (val posAttr = soulInput.readInt(inflatedInputStream)) {
                        0 -> bitrate = soulInput.readInt(inflatedInputStream)
                        1 -> duration = soulInput.readInt(inflatedInputStream)
                        2 -> vbr = soulInput.readInt(inflatedInputStream)
                        else -> soulInput.readInt(inflatedInputStream)
                    }
                }
                var filename = ""
                var folder = ""
                var folderPath = ""
                val a = path.lastIndexOf("/")
                if (a > 0 && a < path.length) {
                    filename = path.substring(a + 1)
                    folderPath = path.substring(0, a)
                    val s = folderPath.lastIndexOf("/")
                    folder = if (s < 0) "/" else folderPath.substring(s)
                }
                soulFiles.add(
                    SoulFile(
                        path,
                        filename,
                        folderPath,
                        folder,
                        size,
                        extension,
                        bitrate,
                        vbr,
                        duration
                    )
                )
            }
            slotsFree = soulInput.readBoolean(inflatedInputStream)
            avgSpeed = soulInput.readInt(inflatedInputStream)
            queueLength = soulInput.readLong(inflatedInputStream)

            peer.soulFiles = soulFiles
            peer.slotsFree = slotsFree
            peer.avgSpeed = avgSpeed
            peer.queueLength = queueLength
            println("Received " + nResults + " search results from ${this.peer.username}\n soulfiles : ${peer.soulFiles}")

            soulInput.packLeft = 0
            if (!peer.soulFiles.isNullOrEmpty())
                PeerRepository.addOrUpdatePeer(peer)
        }
    }


    private fun receiveInfoRequest() {
    }


    private fun receiveInfoReply() {
        val description = soulInput.readString()
        if (soulInput.readBoolean()) {
            val picture = soulInput.readString()
        }
        val totalupl = soulInput.readInt()
        val queuesize = soulInput.readInt()
        val slotsfree = soulInput.readInt()
        println("Received User Info Reply.")
        /*val activity: Activity = Util.uiActivity
        if (activity.getClass() === ProfileActivity::class.java && (activity as ProfileActivity).peerName.equals(this.peerName)) {
            (activity as ProfileActivity).updateProfile(description)
        }*/
    }


    private fun receiveFolderContentsRequest() {
        val nFiles = soulInput.readInt()
        val file = arrayOfNulls<String>(nFiles)
        for (i in 0 until nFiles) {
            file[i] = soulInput.readString()
        }
        println("Received Folder Contents Request.")
        //sendFolderContentsReply(file)
    }


    private fun receiveFolderContentsReply() {
        /*var i: Int
        val dataInputStream = DataInputStream(InflaterInputStream(dis))
        var nFolders = soulInput.readInt(dataInputStream)
        i = 0
        while (i < nFolders) {
            soulInput.readString(dataInputStream)
            i++
        }
        nFolders = soulInput.readInt(dataInputStream)
        i = 0
        while (i < nFolders) {
            val dir = soulInput.readString(dataInputStream)
            val nFiles = soulInput.readInt(dataInputStream)
            println( "Parsing " + dir + " from:" + this.peerName + ".")
            var currentDir: ShareDirectory? = this.service.rootShare.get(this.peerName) as ShareDirectory
            if (currentDir == null) {
                currentDir = ShareDirectory(dir, null)
                this.service.rootShare.put(this.peerName, currentDir)
            } else {
                currentDir = currentDir.open(dir)
            }
            for (j in 0 until nFiles) {
                soulInput.readByte(dataInputStream)
                val file = soulInput.readString(dataInputStream)
                val size = soulInput.readLong(dataInputStream)
                val ext = soulInput.readString(dataInputStream)
                val nAttrs = soulInput.readInt(dataInputStream)
                var bitrate = 0
                var length = 0
                var vbr = 0
                for (k in 0 until nAttrs) {
                    when (soulInput.readInt(dataInputStream)) {
                        0 -> bitrate = soulInput.readInt(dataInputStream)
                        1 -> length = soulInput.readInt(dataInputStream)
                        2 -> vbr = soulInput.readInt(dataInputStream)
                        else -> soulInput.readInt(dataInputStream)
                    }
                }
                val shareFile = ShareFile(currentDir, file, size, bitrate, length, vbr)
            }
            i++
        }
        println( "Finished Loading " + this.peerName.toString() + " folder reply.")
        val a: Activity = Util.uiActivity
        if (a != null && a.getClass() === BrowsePeerActivity::class.java) {
            (a as BrowsePeerActivity).update(this.peerName)
        }*/
    }


    private fun receiveTransferRequest() {
        val direction = soulInput.readInt()
        val ticket = soulInput.readInt()
        val path = soulInput.readString()
        var size: Long
        if (direction == 1) {
            size = soulInput.readLong()
            println("Peer:  ${this.peer} wants to send us a file: $path")
            when {
                askedFiles[path] != null -> {
                    println("The file is recognized as a download we requested.")
//                    askedFiles[ticket]?.path = path
//                    askedFiles[ticket]?.size = size
                    println("Sending a confirmation to start the transfer.")
                    downloadReply(ticket, true, null)
                }
                /* TODO Trusted USers
                GoSeekData.isUserTrusted(this.peerName) -> {
                    println( "A Trusted user is uploading a file to us.")
                    GoSeekData.newDownload(ticket, Util.getFilename(path), this.peerName, path, 5, 0, size, 0, 0, 0)
                    println( "Sending a confirmation to start the transfer.")
                    sendDownloadReply(ticket, true, null)
                    return
                }*/
                else -> {
                    downloadReply(ticket, false, "Forbidden.")
                    println("Unsolicited upload attempted at us.")
                }
            }
        } else
            println("Peer: ${this.peer} wants to download a file: $path")
    }


    private fun receiveTransferReply() {
        println("Received transfer reply.")
        val ticket = soulInput.readInt()
        val allowed = soulInput.readBoolean()
        if (allowed && soulInput.packLeft >= 8) {
            println("Allowed!")
            val filesize = soulInput.readLong()
        } else if (!allowed) {
            println("Not Allowed:" + soulInput.readString())
            /*val goSeekService: GoSeekService = this.service
                goSeekService.pendingUploads--*/
        }
    }


    private fun receiveQueueDownload() {
        val filename = soulInput.readString()
        /* println( "Received a queue download request.")
         this.service.queueUpload(this.peerName, filename)*/
    }


    private fun receivePlaceInQueueReply() {
        /* GoSeekData.updateDownloadPlace(this.peerName, soulInput.readString(), soulInput.readInt())
         val a: Activity = Util.uiActivity
         if (a.getClass() === TransfersActivity::class.java) {
             (a as TransfersActivity).update()
         }*/
    }


    private fun receiveUploadFailed() {
        val reason = soulInput.readString()
        println(reason)
    }


    private fun receiveQueueFailed() {
        val path = soulInput.readString()
        println("Queue Failed. Reason: " + soulInput.readString())
    }


    private fun receivePlaceInQueueRequest() {
        /*val filename = soulInput.readString()
        val c: Cursor = GoSeekData.getUpload(this.peerName, filename)
        if (c != null && c.getCount() > 0) {
            c.moveToFirst()
            sendPlaceInQueueReply(filename, c.getInt(c.getColumnIndex("place")))
        }*/
    }


    private fun receiveUploadQueueNotification() {
    }


    private fun deflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        return outputStream.toByteArray()
    }

    fun pierceFirewall(token: Int) {
        sendMessage(
            ByteMessage()
                .writeInt8(0)
                .writeInt32(token)
                .getBuff()
        )
    }

    fun peerInit(username: String, connectionType: String, token: Int) {
        sendMessage(
            ByteMessage().writeInt8(1)
                .writeStr(username)
                .writeStr(connectionType) //.writeInt(300)
                .writeInt32(token)
                .getBuff()
        )
    }


    private fun getShareFileList() {
        sendMessage(
            ByteMessage()
                .writeInt8(4)
                .getBuff()
        )
    }

    fun fileSearchRequest(token: Int, query: String) {
        sendMessage(
            ByteMessage()
                .writeInt8(8)
                .writeInt32(token)
                .writeStr(query)
                .getBuff()
        )
    }


    private fun userInfoRequest() {
        sendMessage(ByteMessage().writeInt8(15).getBuff())
    }

    /*getShareFileList: () => new Message().int32(4),
    sharedFileList: shareList => {
        let msg = new Message().int32(5);
        encodeList.shares(msg, shareList);
        return msg;
    },
    fileSearchResult: args => {
        let msg = new Message().str(args.username).int32(args.token);
        encodeList.files(msg, args.fileList);
        msg.int8(args.slotsFree).int32(args.speed).int64(args.queueSize);
        return new Message().int32(9).writegetBuff()er(zlib.deflateSync(msg.data));
    },

    userInfoReply: args => {
        let msg = new Message().int32(16).str(args.description);

        if (args.picture) {
            msg.int8(true).file(args.picture);
        } else {
            msg.int8(false);
        }

        msg.int32(args.uploadSlots).int32(args.queueSize).int8(args.slotsFree);
        // who we accept uploads from
        msg.int32(args.uploadsFrom);

        return msg;
    },
    messageAcked: () => new Message().int32(23),
    folderContentsRequest: folders => {
        folders = Array.isArray(folders) ? folders : [ folders ];
        let msg = new Message().int32(36).int32(folders.length);
        folders.forEach(folder => msg.str(folder));
        return msg;
    },
    folderContentsResponse: shareLists => {
        let zipped = new Message();

        zipped.int32(Object.keys(fileLists).length);

        Object.keys(fileLists).forEach(dir => {
                encodeList.shares(zipped, shareLists[dir], false);
        });

        zipped = zlib.deflateSync(zipped.data);

        let msg = new Message().int32(37)
        msg.writegetBuff()er(zlib.deflateSync(zipped.data));
        return msg;
    },
    */


    fun transferRequest(direction: Int, token: Int, soulFile: SoulFile, fileSize: Long?) {
        println("sending transfer request for ${soulFile.filename}, direction $direction, token $token")
        val actualToken = SoulStack.actualSearchToken
        askedFiles[soulFile.path] = soulFile

        val msgInit = ByteMessage()
            .writeInt32(1)
            .writeStr(this.peer.username)
            .writeStr("P")
            .writeInt32(token)

        val msgTransfer = ByteMessage()
            .writeInt32(40)
            .writeInt32(direction)
            .writeInt32(actualToken)
            .writeStr(soulFile.filename)
        if (direction == 1)
            if (fileSize != null)
                msgTransfer.writeLong(fileSize)
            else {
                println("size shouldn't be null when responding transferRequest")
                return
            }
        sendMessage(msgTransfer.getBuff())
    }

    fun downloadReply(ticket: Int, allowed: Boolean, reason: String?) {
        val msg = ByteMessage()
            .writeInt32(41)
            .writeInt32(ticket)
            .writeBool(allowed.toInt())

        if (!allowed)
            msg.writeStr(reason ?: "no reason")

        sendMessage(msg.getBuff())
    }


    fun queueDownload(soulFile: SoulFile) {
        sendMessage(
            ByteMessage()
                .writeInt32(43)
                .writeStr(soulFile.path)
                .getBuff()
        )
    }

    fun placeInQueueRequest(filename: String) {
        sendMessage(
            ByteMessage()
                .writeInt32(51)
                .writeStr(filename)
                .getBuff()
        )
    }


/*: (isUpload, token, file, size) => {
let msg = new Message().int32(40);
msg.int32(+isUpload); // direction 1 for upload
msg.int32(token).str(file);

if (isUpload) {
    msg.int64(size);
}

return msg;
},*/
/*
transferResponse: (token, allowed, size) => {
let msg = new Message().int32(41).int32(token);

if (allowed) {
    msg.int8(true).int64(size);
} else {
    msg.int8(false).str(size); // reason
}

return msg;
},
queueUpload: file => new Message().int32(43).str(file),
placeInQueue: (file, place) => {
return new Message().int32(44).str(file).int32(place);
},
uploadFailed: file => new Message().int32(46).str(file),
queueFailed: (file, reason) => {
return new Message().int32(50).str(file).str(reason);
},
// TODO may not need this
placeInQueueRequest: file => new Message().int32(51).str(file)*/

/* private fun setPeerSocketInterface(callback: PeerSocketInterface) {
    this.peerSocketInterface = callback
}*/


    private fun fromUnsignedInt(value: Long): ByteArray {
        val bytes = ByteArray(8)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(value)

        return bytes.copyOfRange(4, 8)
    }

}