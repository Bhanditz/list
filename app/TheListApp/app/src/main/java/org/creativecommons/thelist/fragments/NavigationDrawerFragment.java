/* The List powered by Creative Commons

   Copyright (C) 2014, 2015 Creative Commons

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package org.creativecommons.thelist.fragments;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.creativecommons.thelist.R;
import org.creativecommons.thelist.adapters.DrawerAdapter;
import org.creativecommons.thelist.adapters.DrawerItem;
import org.creativecommons.thelist.utils.RecyclerItemClickListener;
import org.creativecommons.thelist.utils.SharedPreferencesMethods;

import java.util.ArrayList;
import java.util.List;

public class NavigationDrawerFragment extends android.support.v4.app.Fragment {

    private Context mContext;
    private SharedPreferencesMethods mSharedPref;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private DrawerAdapter mAdapter;

    private View containerView;
    private RecyclerView mDrawerRecyclerView;

    private boolean mUserLearnedDrawer;
    private boolean mFromSavedInstanceState;

    //Interface with Activity
    public NavigationDrawerListener mCallback;

    // --------------------------------------------------------

    public interface NavigationDrawerListener {
        public void onDrawerClicked (int position);
    }

    public NavigationDrawerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (NavigationDrawerListener) activity;
        } catch(ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + activity.getString(R.string.R_string_drawer_callback_exception_message));
        }
    } //onAttach

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_navigation_drawer, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        mFromSavedInstanceState = savedInstanceState != null ? true : false;

    } //onCreate

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        mSharedPref = new SharedPreferencesMethods(mContext);

        //RecyclerView
        mAdapter = new DrawerAdapter(mContext, getData());
        mDrawerRecyclerView = (RecyclerView)getActivity().findViewById(R.id.drawer_recyclerView);
        mDrawerRecyclerView.setAdapter(mAdapter);
        mDrawerRecyclerView.setHasFixedSize(true);
        mDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mDrawerRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getActivity(),
                new RecyclerItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        //TODO: on item click load fragment
                        mCallback.onDrawerClicked(position);
                    }
                }));

        mUserLearnedDrawer = mSharedPref.getUserLearnedDrawer();

    } //onActivityCreated

    public void setUp(int drawerId, DrawerLayout drawerLayout, Toolbar toolbar) {
        containerView = getActivity().findViewById(drawerId);
        mDrawerLayout = drawerLayout;
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close){

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                if(!mUserLearnedDrawer){
                    mUserLearnedDrawer = true;
                    mSharedPref.savedSharedPreference(SharedPreferencesMethods.DRAWER_USER_LEARNED, true);
                }

                getActivity().invalidateOptionsMenu();
            }
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                getActivity().invalidateOptionsMenu();
            }
        };

        if(!mUserLearnedDrawer && !mFromSavedInstanceState){
            mDrawerLayout.openDrawer(containerView);
        }

        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });
    } //setUp

    public List<DrawerItem> getData() {
        //load only static data inside a drawer
        List<DrawerItem> data = new ArrayList<>();
        String[] titles = getResources().getStringArray(R.array.drawer_navigation_labels);
        for (int i = 0; i < titles.length; i++) {
            DrawerItem drawerItem = new DrawerItem();
            drawerItem.setItemName(titles[i]);
            data.add(drawerItem);
        }
        return data;
    }



} //NavigationDrawerFragment
