package fr.sercurio.soulseek.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import soulseek.custom_view.ViewPagerAdapter
import fr.sercurio.soulseek.R
import fr.sercurio.soulseek.entities.PeerApiModel
import soulseek.ui.fragments.child.SearchChildFragment

class SearchFragment : Fragment() {
    private val childRequestFragment = SearchChildFragment.newInstance(true, 0)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        val viewPager: ViewPager = view.findViewById(R.id.pager)
        val tabLayout: TabLayout = view.findViewById(R.id.tabs)
        tabLayout.setupWithViewPager(viewPager)
        val adapter = ViewPagerAdapter(childFragmentManager)
        adapter.addFrag(childRequestFragment, "REQUEST")
        viewPager.adapter = adapter
        return view
    }

    fun addSoulFiles(peer: PeerApiModel) {
        childRequestFragment.addSearchResults(peer)
    }
}