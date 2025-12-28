package com.sovworks.eds.android.filemanager.activities;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import com.sovworks.eds.android.filemanager.fragments.FileListViewFragment;
import com.sovworks.eds.android.navigdrawer.DrawerController;
import com.sovworks.eds.locations.Location;
import com.sovworks.eds.locations.LocationsManager;

public class FileManagerActivity extends FileManagerActivityBase
{
    public static void openFileManager(AppCompatActivity activity, Location location, int scrollPosition)
    {
        if (activity instanceof FileManagerActivity)
        {
            ((FileManagerActivity)activity).goTo(location, scrollPosition);
        }
        else
        {
            Intent intent = new Intent(activity, FileManagerActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            LocationsManager.storePathsInIntent(intent, location, null);
            intent.putExtra(FileListViewFragment.ARG_SCROLL_POSITION, scrollPosition);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivity(intent);
        }
    }

    @Override
    protected DrawerController createDrawerController()
    {
        return new DrawerController(this);
    }

    @Override
    protected void showPromoDialogIfNeeded()
    {
        if(_settings.getLastViewedPromoVersion() < 211)
            super.showPromoDialogIfNeeded();
    }
}
