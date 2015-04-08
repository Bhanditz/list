package org.creativecommons.thelist.fragments;


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.android.volley.VolleyError;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.melnykov.fab.FloatingActionButton;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.nispok.snackbar.listeners.EventListener;

import org.creativecommons.thelist.R;
import org.creativecommons.thelist.activities.RandomActivity;
import org.creativecommons.thelist.adapters.FeedAdapter;
import org.creativecommons.thelist.adapters.MainListItem;
import org.creativecommons.thelist.authentication.AccountGeneral;
import org.creativecommons.thelist.swipedismiss.SwipeDismissRecyclerViewTouchListener;
import org.creativecommons.thelist.utils.ApiConstants;
import org.creativecommons.thelist.utils.DividerItemDecoration;
import org.creativecommons.thelist.utils.ListUser;
import org.creativecommons.thelist.utils.MaterialInterpolator;
import org.creativecommons.thelist.utils.MessageHelper;
import org.creativecommons.thelist.utils.PhotoConstants;
import org.creativecommons.thelist.utils.RequestMethods;
import org.creativecommons.thelist.utils.SharedPreferencesMethods;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyListFragment extends android.support.v4.app.Fragment {
    public static final String TAG = MyListFragment.class.getSimpleName();
    protected Context mContext;

    //Helper Methods
    RequestMethods mRequestMethods;
    SharedPreferencesMethods mSharedPref;
    MessageHelper mMessageHelper;
    ListUser mCurrentUser;

    protected MainListItem mCurrentItem;
    protected int activeItemPosition;

    protected MainListItem mItemToBeUploaded;
    protected int uploadItemPosition;

    protected MainListItem mLastDismissedItem;
    protected int lastDismissedItemPosition;
    protected Uri mMediaUri;

    //RecyclerView
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mFeedAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private List<MainListItem> mItemList = new ArrayList<>();

    //UI Elements
    private Menu menu;
    private FloatingActionButton mFab;
    protected ProgressBar mProgressBar;
    protected RelativeLayout mUploadProgressBar;
    protected FrameLayout mFrameLayout;
    protected TextView mEmptyView;

    // --------------------------------------------------------


    public MyListFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        mMessageHelper = new MessageHelper(mContext);
        mRequestMethods = new RequestMethods(mContext);
        mSharedPref = new SharedPreferencesMethods(mContext);
        mCurrentUser = new ListUser(getActivity());

        Activity activity = getActivity();

        //Load UI Elements
        mProgressBar = (ProgressBar) activity.findViewById(R.id.feedProgressBar);
        mUploadProgressBar = (RelativeLayout) activity.findViewById(R.id.photoProgressBar);
        mEmptyView = (TextView) activity.findViewById(R.id.empty_list_label);
        mFrameLayout = (FrameLayout)activity.findViewById(R.id.overlay_fragment_container);
        mFab = (FloatingActionButton) activity.findViewById(R.id.fab);
        mFab.setEnabled(false);
        mFab.setVisibility(View.GONE);
        mFab.hide();

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: don’t do this
                Intent hitMeIntent = new Intent(getActivity(), RandomActivity.class);
                startActivity(hitMeIntent);
            }
        });

        //RecyclerView
        mSwipeRefreshLayout = (SwipeRefreshLayout)activity.findViewById(R.id.feedSwipeRefresh);
        mRecyclerView = (RecyclerView)activity.findViewById(R.id.feedRecyclerView);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        //TODO: Try dividers in layout instead?
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL_LIST);
        mRecyclerView.addItemDecoration(itemDecoration);
        mLayoutManager = new LinearLayoutManager(mContext);
        mFeedAdapter = new FeedAdapter(mContext, mItemList);
        mRecyclerView.setAdapter(mFeedAdapter);
        mRecyclerView.setLayoutManager(mLayoutManager);
        initRecyclerView();

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                displayUserItems();
            }
        });


    } //onActivityCreated

    //TODO: figure out where this goes in a fragment
//    @Override
//    protected void onRestart(){
//        super.onRestart();
//        //Log.v(TAG, "ON RESTART CALLED");
//        if(!mCurrentUser.isTempUser() && mCurrentUser.getAnalyticsOptOut() != null){
//            mSharedPref.setAnalyticsViewed(true);
//        }
//    }

    //TODO: is this the right place to do this in a fragment?
    @Override
    public void onStart(){
        super.onStart();

        if(!mCurrentUser.isTempUser() && !mSharedPref.getAnalyticsViewed()){
            //TODO: check app version
            //If user is logged in but has not opted into/out of GA
            Log.v(TAG, "logged in without opt out response");
            mMessageHelper.enableFeatureDialog(mContext, getString(R.string.dialog_ga_title),
                    getString(R.string.dialog_ga_message),
                    new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            super.onPositive(dialog);
                            mCurrentUser.setAnalyticsOptOut(false);
                            GoogleAnalytics.getInstance(mContext).setAppOptOut(false);
                            dialog.dismiss();
                        }
                        @Override
                        public void onNegative(MaterialDialog dialog) {
                            super.onNegative(dialog);
                            mCurrentUser.setAnalyticsOptOut(true);
                            GoogleAnalytics.getInstance(mContext).setAppOptOut(true);
                            dialog.dismiss();

                        }
                    });
            mSharedPref.setAnalyticsViewed(true);
            Log.v(TAG, "SET ANALYTICS VIEWED TRUE");
        }
    } //onStart

    @Override
    public void onResume() {
        super.onResume();

        if(!mSharedPref.getSurveyTaken()){
            int surveyCount = mSharedPref.getSurveyCount();

            //Check if should display survey item
            if(surveyCount % 4 == 0 && surveyCount != 0 || !(mCurrentUser.isTempUser()) && surveyCount == 1){
                mMessageHelper.takeSurveyDialog(mContext, getString(R.string.dialog_survey_title),
                        getString(R.string.dialog_survey_message),
                        new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);

                                //Set survey taken
                                mSharedPref.setSurveyTaken(true);

                                //Go to link
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.dialog_survey_link)));
                                startActivity(browserIntent);
                                dialog.dismiss();
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                super.onNegative(dialog);
                                dialog.dismiss();
                            }
                        });
            } //survey check

            //Increase count
            mSharedPref.setSurveyCount(surveyCount + 1);
            Log.v(TAG, "SURVEY COUNT: " + String.valueOf(surveyCount));
        } //surveyTaken

        //Update menu for login/logout options
        //TODO: get login-logout working
        //getActivity().invalidateOptionsMenu();

        if(!mFab.isVisible()){
            mFab.show();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mFab.setEnabled(true);
            }
        }, 500);

        if(mItemToBeUploaded != null){
            return;
        }

        if(!(mCurrentUser.isTempUser())) { //if this is not a temp user
            Log.v(TAG, " > User is logged in");
            displayUserItems();
        } else { //if user is a temp
            Log.v(TAG, " > User is not logged in");
            if(mItemList.size() == 0){
                mRecyclerView.setVisibility(View.INVISIBLE);
                displayUserItems();
            } else {
                mFeedAdapter.notifyDataSetChanged();
                mRecyclerView.setVisibility(View.VISIBLE);
            }
        }
    } //onResume

    //----------------------------------------------
    //LIST ITEM REQUEST + UPDATE VIEW
    //----------------------------------------------

    private void displayUserItems() {
        JSONArray itemIds;

        if(!(mCurrentUser.isTempUser())) { //IF USER IS NOT A TEMP
            mRequestMethods.getUserItems(new RequestMethods.ResponseCallback() {
                @Override
                public void onSuccess(JSONArray response) {
                    Log.v(TAG , "> getUserItems > onSuccess: " + response.toString());
                    mItemList.clear();


                    for(int i=0; i < response.length(); i++) {
                        try {
                            JSONObject singleListItem = response.getJSONObject(i);
                            //Only show items in the user’s list that have not been completed
                            if (singleListItem.getInt(ApiConstants.ITEM_COMPLETED) == 0) {
                                MainListItem listItem = new MainListItem();
                                listItem.setItemName
                                        (singleListItem.getString(ApiConstants.ITEM_NAME));
                                listItem.setMakerName
                                        (singleListItem.getString(ApiConstants.MAKER_NAME));
                                listItem.setItemID
                                        (singleListItem.getString(ApiConstants.ITEM_ID));
                                mItemList.add(listItem);
                            } else if(singleListItem.getInt(ApiConstants.ITEM_COMPLETED) == 1) {
                                MainListItem listItem = new MainListItem();
                                listItem.setItemName
                                        (singleListItem.getString(ApiConstants.ITEM_NAME));
                                listItem.setMakerName
                                        (singleListItem.getString(ApiConstants.MAKER_NAME));
                                listItem.setItemID
                                        (singleListItem.getString(ApiConstants.ITEM_ID));
                                listItem.setError(true);
                                //TODO: QA (add error items to the top)
                                mItemList.add(0, listItem);
                            } else {
                                continue;
                            }
                        } catch (JSONException e) {
                            Log.v(TAG, e.getMessage());
                        }
                    }
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mFab.show();
                    mFab.setVisibility(View.VISIBLE);

                    if(mItemList.size() == 0){
                        //TODO: show textView
                        mEmptyView.setVisibility(View.VISIBLE);

                    } else {
                        //TODO: hide textView
                        mEmptyView.setVisibility(View.GONE);
                        Collections.reverse(mItemList);
                        mFeedAdapter.notifyDataSetChanged();
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                } //onSuccess
                @Override
                public void onFail(VolleyError error) {
                    Log.d(TAG , "> getUserItems > onFail: " + error.toString());
                }
            });
        }
        else { //IF USER IS A TEMP
            mItemList.clear();
            mEmptyView.setVisibility(View.GONE);
            //Get items selected from SharedPref
            itemIds = mSharedPref.getUserItemPreference();

            if (itemIds != null && itemIds.length() > 0) {
                for (int i = 0; i < itemIds.length(); i++) {
                    //TODO: do I need to set ItemID here?
                    MainListItem listItem = new MainListItem();
                    try {
                        listItem.setItemID(String.valueOf(itemIds.getInt(i)));
                        listItem.setMessageHelper(mMessageHelper);
                        //TODO: needs to be mainactivity in the MainListItem class
                        listItem.setMainListActivity(getActivity());
                        listItem.setMyListFragment(this);
                        listItem.createNewUserListItem();
                    } catch (JSONException e) {
                        Log.v(TAG, e.getMessage());
                    }
                    mItemList.add(listItem);
                }
                Collections.reverse(mItemList);
                mFeedAdapter.notifyDataSetChanged();
                mSwipeRefreshLayout.setRefreshing(false);
            } else {
                mProgressBar.setVisibility(View.INVISIBLE);
                mEmptyView.setVisibility(View.VISIBLE);
                mFab.show();
                mFab.setVisibility(View.VISIBLE);
            }
        }
    } //displayUserItems

    //For temp users displayUserItems:
    // Check if all items have been returned from API before displaying list
    public void CheckComplete() {
        Log.d(TAG, " > CheckComplete");
        for(int i = 0; i < mItemList.size(); i++) {
            if (!mItemList.get(i).completed) {
                return;
            }
        }
        mProgressBar.setVisibility(View.INVISIBLE);
        mFab.show();
        mFab.setVisibility(View.VISIBLE);
        mFeedAdapter.notifyDataSetChanged();
        mRecyclerView.setVisibility(View.VISIBLE);
    } //CheckComplete

    //----------------------------------------------
    //RECYCLERVIEW – LIST ITEM INTERACTION
    //----------------------------------------------

    private void initRecyclerView(){

        SwipeDismissRecyclerViewTouchListener touchListener = new SwipeDismissRecyclerViewTouchListener(
                mRecyclerView, mSwipeRefreshLayout, new SwipeDismissRecyclerViewTouchListener.DismissCallbacks() {
            @Override
            public boolean canDismiss(int position) {
                return true;
            }
            @Override
            public void onDismiss(RecyclerView recyclerView, int[] reverseSortedPositions) {
                for (int position : reverseSortedPositions) {
                    // TODO: this is temp solution for preventing blinking item onDismiss <-- OMG DEATH
                    mLayoutManager.findViewByPosition(position).setVisibility(View.GONE);
                    //Get item details for UNDO
                    lastDismissedItemPosition = position;
                    mLastDismissedItem = mItemList.get(position);

                    //What happens when item is swiped offscreen
                    mItemList.remove(mLastDismissedItem);
                    //TODO: should this be after snackbar removal?
                    mCurrentUser.removeItemFromUserList(mLastDismissedItem.getItemID());

                    // do not call notifyItemRemoved for every item, it will cause gaps on deleting items
                    mFeedAdapter.notifyDataSetChanged();

                    //Snackbar message
                    showSnackbar();
                }
            }
        });
        mRecyclerView.setOnTouchListener(touchListener);
        // Setting this scroll listener is required to ensure that during ListView scrolling,
        // we don't look for swipes.
        LinearLayoutManager llm = (LinearLayoutManager)mRecyclerView.getLayoutManager();
        mRecyclerView.setOnScrollListener(touchListener.makeScrollListener(llm, mFab));
    } //initRecyclerView

    //----------------------------------------------
    //SNACKBAR – UNDO ITEM DELETION
    //----------------------------------------------

    public void showSnackbar(){
        SnackbarManager.show(
                //also includes duration: SHORT, LONG, INDEFINITE
                Snackbar.with(mContext)
                        .text("Item deleted") //text to display
                        .actionColor(getResources().getColor(R.color.colorSecondary))
                        .actionLabel("undo".toUpperCase())
                        .actionListener(new ActionClickListener() {
                            @Override
                            public void onActionClicked(Snackbar snackbar) {
                                /*NOTE: item does not need to be re-added here because it is only
                                removed when the snackbar is actually dismissed*/

                                //What happens when item is swiped offscreen
                                mItemList.add(0, mLastDismissedItem);
                                //re-add item to user’s list in DB
                                mCurrentUser.addItemToUserList(mLastDismissedItem.getItemID());
                                mFeedAdapter.notifyDataSetChanged();
                                mLayoutManager.scrollToPosition(0);
                                mFab.show();
                            }
                        }) //action button’s listener
                        .eventListener(new EventListener() {
                            Interpolator interpolator = new MaterialInterpolator();

                            @Override
                            public void onShow(Snackbar snackbar) {
                                TranslateAnimation tsa = new TranslateAnimation(0, 0, 0,
                                        -snackbar.getHeight());
                                tsa.setInterpolator(interpolator);
                                tsa.setFillAfter(true);
                                tsa.setFillEnabled(true);
                                tsa.setDuration(300);
                                mFab.startAnimation(tsa);
                            }

                            @Override
                            public void onShown(Snackbar snackbar) {
                            }

                            @Override
                            public void onDismiss(Snackbar snackbar) {

                                TranslateAnimation tsa2 = new TranslateAnimation(0, 0,
                                        -snackbar.getHeight(), 0);
                                tsa2.setInterpolator(interpolator);
                                tsa2.setFillAfter(true);
                                tsa2.setFillEnabled(true);
                                tsa2.setStartOffset(100);
                                tsa2.setDuration(300);
                                mFab.startAnimation(tsa2);
                            }

                            @Override
                            public void onDismissed(Snackbar snackbar) {
                                //TODO: QA
                                //If no more items
                                if (mItemList.isEmpty()) {
                                    mEmptyView.setVisibility(View.VISIBLE);
                                }
                                //If fab is hidden (bug fix?)
                                if (!mFab.isVisible()) {
                                    mFab.show();
                                }
                            }
                        }) //event listener
                , getActivity());
    } //showSnackbar

    //----------------------------------------------
    //TAKING PHOTO/PHOTO SELECTION
    //----------------------------------------------

    //Show dialog when List Item is tapped
    public DialogInterface.OnClickListener mDialogListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch(which) {
                        case 0:
                            Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            mMediaUri = getOutputMediaFileUri(PhotoConstants.MEDIA_TYPE_IMAGE);
                            if (mMediaUri == null) {
                                // Display an error
                                Toast.makeText(getActivity(), R.string.error_external_storage,
                                        Toast.LENGTH_LONG).show();
                            }
                            else {
                                takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mMediaUri);
                                startActivityForResult(takePhotoIntent, PhotoConstants.TAKE_PHOTO_REQUEST);
                            }
                            break;
                        case 1: // Choose picture
                            Intent choosePhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
                            choosePhotoIntent.setType("image/*");
                            //mMediaUri = getOutputMediaFileUri(PhotoConstants.MEDIA_TYPE_IMAGE);
                            startActivityForResult(choosePhotoIntent,PhotoConstants.PICK_PHOTO_REQUEST);
                            break;
                    }
                }
                private Uri getOutputMediaFileUri(int mediaType) {
                    // To be safe, you should check that the SDCard is mounted
                    // using Environment.getExternalStorageState() before doing this.
                    if (isExternalStorageAvailable()) {
                        // get the URI

                        // 1. Get the external storage directory
                        String appName = getActivity().getString(R.string.app_name);
                        File mediaStorageDir = new File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                                appName);

                        // 2. Create our subdirectory
                        if (! mediaStorageDir.exists()) {
                            if (! mediaStorageDir.mkdirs()) {
                                Log.e(TAG, "Failed to create directory.");
                                return null;
                            }
                        }
                        // 3. Create a file name
                        // 4. Create the file
                        File mediaFile;
                        Date now = new Date();
                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now);

                        String path = mediaStorageDir.getPath() + File.separator;
                        if (mediaType == PhotoConstants.MEDIA_TYPE_IMAGE) {
                            mediaFile = new File(path + "IMG_" + timestamp + ".jpg");
                        }
                        else {
                            return null;
                        }
                        Log.d(TAG, "File: " + Uri.fromFile(mediaFile));

                        // 5. Return the file's URI
                        return Uri.fromFile(mediaFile);
                    }
                    else {
                        return null;
                    }
                }
                //Check if external storage is available
                private boolean isExternalStorageAvailable() {
                    String state = Environment.getExternalStorageState();
                    if (state.equals(Environment.MEDIA_MOUNTED)) {
                        return true;
                    }
                    else {
                        return false;
                    }
                }
            };

    //Once photo taken or selected then do this:
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode){
            case PhotoConstants.PICK_PHOTO_REQUEST:
            case PhotoConstants.TAKE_PHOTO_REQUEST:
                if(resultCode == Activity.RESULT_OK) {
                    //photoToBeUploaded = true;
                    mItemToBeUploaded = mCurrentItem;
                    uploadItemPosition = activeItemPosition;

                    if(data == null) {
                        //Toast.makeText(this,getString(R.string.general_error),Toast.LENGTH_LONG).show();
                        Log.d(TAG, "> onActivityResult > data == null");
                    }
                    else {
                        mMediaUri = data.getData();
                    }
                    Log.i(TAG,"Media URI:" + mMediaUri);

                    //TODO: make sure for sure auth will exist for this to happen
                    //Add photo to the Gallery (listen for broadcast and let gallery take action)
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(mMediaUri);
                    getActivity().sendBroadcast(mediaScanIntent);

                    startPhotoUpload();
                } //RESULT OK
                else if(resultCode != Activity.RESULT_CANCELED) { //result other than ok or cancelled
                    //Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "> onActivityResult > resultCode != canceled");
                }
                break;
        } //switch
    } //onActivityResult

    //----------------------------------------------
    //PHOTO UPLOAD
    //----------------------------------------------

    //Start Upload + Respond
    public void startPhotoUpload(){
        if(!(mCurrentUser.isTempUser())){ //IF NOT TEMP USER
            mCurrentUser.getToken(new ListUser.AuthCallback() { //getToken
                @Override
                public void onSuccess(String authtoken) {
                    Log.v(TAG, "> startPhotoUpload > getToken, token received: " + authtoken);

                    mItemList.remove(mItemToBeUploaded);
                    mFeedAdapter.notifyDataSetChanged();
                    performUpload();
                }
            });
        } else {
            mCurrentUser.addNewAccount(AccountGeneral.ACCOUNT_TYPE,
                    AccountGeneral.AUTHTOKEN_TYPE_FULL_ACCESS, new ListUser.AuthCallback() { //addNewAccount
                        @Override
                        public void onSuccess(String authtoken) {
                            Log.d(TAG, "> addNewAccount > onSuccess, authtoken: " + authtoken);
                            try {
                                mItemList.remove(mItemToBeUploaded);
                                mFeedAdapter.notifyDataSetChanged();
                                performUpload();
                            } catch (Exception e) {
                                Log.d(TAG,"addAccount > " + e.getMessage());
                            }
                        }
                    });
        }
    } //startPhotoUpload

    public void performUpload(){
        mUploadProgressBar.setVisibility(View.VISIBLE);
        mRequestMethods.uploadPhoto(mItemToBeUploaded.getItemID(), mMediaUri,
                new RequestMethods.RequestCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "On Upload Success");

                        mMessageHelper.notifyUploadSuccess(mItemToBeUploaded.getItemName());
                        mItemToBeUploaded = null;
                        //photoToBeUploaded = false;
                        displayUserItems();

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mUploadProgressBar.setVisibility(View.GONE);
                            }
                        }, 500); //could add a time check from visible to invisible, heh.
                    }
                    @Override
                    public void onFail() {
                        Log.d(TAG, "On Upload Fail");

                        mMessageHelper.notifyUploadFail(mItemToBeUploaded.getItemName());
                        //photoToBeUploaded = false;
                        //mItemlist.add(uploadItemPosition);

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mUploadProgressBar.setVisibility(View.GONE);
                                displayUserItems();
                                //TODO: add visual indication that item failed
                            }
                        }, 500);
                    }
                });
    } //performUpload

} //MyListFragment