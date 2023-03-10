package fr.sercurio.soulseek

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.AndroidEntryPoint
import fr.sercurio.soulseek.databinding.ActivitySoulBinding
import fr.sercurio.soulseek.entities.PeerApiModel
import fr.sercurio.soulseek.entities.RoomApiModel
import fr.sercurio.soulseek.entities.RoomMessageApiModel
import fr.sercurio.soulseek.entities.SoulFile
import fr.sercurio.soulseek.ui.fragments.PreferencesFragment
import fr.sercurio.soulseek.ui.fragments.RoomFragment
import fr.sercurio.soulseek.ui.fragments.SearchFragment
import fr.sercurio.soulseek.viewmodel.LoginViewModel
import kotlinx.coroutines.*
import soulseek.ui.fragments.child.SearchChildFragment
import soulseek.ui.fragments.child.SearchChildFragment.SearchChildInterface
import soulseek.utils.SoulStack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random


/**
 * Créé et codé par Louis Penalva tout droits réservés.
 */
@AndroidEntryPoint
class SoulActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    SearchChildInterface,
    RoomFragment.RoomFragmentInterface {

    private lateinit var binding: ActivitySoulBinding

    /* Fragments */
    private val roomFragment = RoomFragment()
    private val searchFragment = SearchFragment()
    private val preferencesFragment = PreferencesFragment()

    /* Logger */
    private val tag = SoulActivity::class.java.simpleName

    /* Managers */
    private lateinit var soulseekApi: SoulseekApi

    /* BottomNavigationListener */
    private val onNavigationItemSelectedListener = NavigationBarView.OnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.destination_search -> setCurrentFragment(searchFragment)
            R.id.destination_room -> setCurrentFragment(roomFragment)
            R.id.destination_preferences -> setCurrentFragment(preferencesFragment)
        }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySoulBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setCurrentFragment(roomFragment)

        val sharedPreference = PreferenceManager.getDefaultSharedPreferences(this)

        binding.bottomNavigationView.setOnItemSelectedListener(onNavigationItemSelectedListener)

        val viewModel: LoginViewModel by viewModels()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    if (it.connected) println("Connected") else println("Not connected")
                }
            }
        }
        try {

            soulseekApi = object : SoulseekApi(
                sharedPreference.getString("key_login", "")!!,
                sharedPreference.getString("key_password", "")!!,
                5001,
                sharedPreference.getString("key_host", "")!!,
                sharedPreference.getString("key_port", "0")!!.toInt()
            ) {
                override fun onLogin(isConnected: Boolean, greeting: String?, nothing1: Int?, reason: String?) {
                    viewModel.updateConnected(isConnected)
                }

                override fun onRoomList(rooms: ArrayList<RoomApiModel>) {
                    this@SoulActivity.onRoomList(rooms)
                }

                override fun onUserJoinedRoom(
                    room: String,
                    username: String,
                    status: Int,
                    avgspeed: Int,
                    downloadNum: Long,
                    files: Int,
                    dirs: Int,
                    slotsFree: Int,
                    countryCode: String
                ) {
                    this@SoulActivity.onUserJoinRoom(
                        room,
                        username,
                        status,
                        avgspeed,
                        downloadNum,
                        files,
                        dirs,
                        slotsFree,
                        countryCode
                    )
                }

                override fun onUserLeftRoom(room: String, username: String) {
                    this@SoulActivity.onUserLeftRoom(room, username)
                }

                override fun onSayInChatRoom(room: String, username: String, message: String) {
                    this@SoulActivity.onRoomMessage(room, username, message)
                }

                override fun onSearchReply(peer: PeerApiModel) {
                    this@SoulActivity.onSearchReply(peer)
                }
            }
        } catch (e: Exception) {
            println(e)
        }
    }


    override fun onAttachFragment(fragment: Fragment) {
        when (fragment) {
            is RoomFragment -> {
                fragment.setRoomFragmentInterface(this)
            }

            is SearchChildFragment -> {
                fragment.setSearchChildInterface(this)
            }

            is PreferencesFragment -> {
                //
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soulseekApi.clientSoul.close()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    /************************/
    /* SearchChildInterface */
    /************************/
    override fun onQueryChangeListener(query: String?) {
        val token = ByteBuffer.wrap(Random.nextBytes(4)).order(ByteOrder.LITTLE_ENDIAN).int

        if (query != null) {
            SoulStack.searches[token] = query
            SoulStack.actualSearchToken = token
            Log.d(
                tag,
                "search: ${SoulStack.searches[SoulStack.actualSearchToken]}, token: ${SoulStack.actualSearchToken}"
            )
            CoroutineScope(Dispatchers.IO).launch {
                soulseekApi.clientSoul.fileSearch(query)
            }
        }
    }

    override fun onSoulfileDownloadQuery(peer: PeerApiModel, soulFile: SoulFile) {
        //TODO send to a peer a transfer request
    }

    /****************************/
    /* FRAGMENT MANAGER METHODS */
    /****************************/
    private fun setCurrentFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.flFragment, fragment)
                .commit()
        }
    }
    /**************/
    /* UI METHODS */
    /**************/
    override suspend fun onRoomSpinnerItemSelected(roomName: String) {
        soulseekApi.clientSoul.joinRoom(roomName)
    }

    override suspend fun onRoomMessageSend(roomMessage: RoomMessageApiModel) {
        soulseekApi.clientSoul.sendRoomMessage(roomMessage)
    }

    /*************************/
    /* SERVER SOCKET METHODS */
    /*************************/
    fun onLogin(connected: Int, greeting: String, ipAddress: String) {
        runOnUiThread {
            if (connected == 1) Toast.makeText(this@SoulActivity, "Connected", Toast.LENGTH_SHORT).show()
            else Toast.makeText(this@SoulActivity, "Not Connected", Toast.LENGTH_SHORT).show()
        }
    }

    fun onRoomMessage(roomName: String, username: String, message: String) {
        runOnUiThread {
            roomFragment.addRoomMessage(RoomMessageApiModel(roomName, username, message))
        }
    }

    fun onUserJoinRoom(
        roomName: String,
        username: String,
        status: Int,
        averageSpeed: Int,
        downloadNum: Long,
        nbFiles: Int,
        nbDirectories: Int,
        slotsFree: Int,
        countryCode: String
    ) {
        runOnUiThread {
            roomFragment.addRoomMessage(RoomMessageApiModel(roomName, "~~~", "$username rejoint la room"))
        }
    }

    fun onUserLeftRoom(roomName: String, username: String) {
        runOnUiThread {
            roomFragment.addRoomMessage(RoomMessageApiModel(roomName, "~~~", "$username a quitté la room"))
        }
    }

    fun onRoomList(rooms: ArrayList<RoomApiModel>) {
        runOnUiThread {
            roomFragment.setRoomList(rooms)
        }
    }

    /******************************/
    /* PeerSocketManagerInterface */
    /******************************/
    fun onGetSharedList() {
        TODO("Not yet implemented")
    }

    fun onSearchReply(peer: PeerApiModel) {
        runOnUiThread {
            searchFragment.addSoulFiles(peer)
        }
    }

    fun onFolderContentsRequest(numberOfFiles: Int) {
        TODO("Not yet implemented")
    }

    fun onTransferDownloadRequest(token: Long, allowed: Int, reason: String?) {
        TODO("Not yet implemented")
    }

    fun onUploadFailed(filename: String) {
        TODO("Not yet implemented")
    }

    fun onQueueFailed(filename: String?, reason: String) {
        TODO("Not yet implemented")
    }
}