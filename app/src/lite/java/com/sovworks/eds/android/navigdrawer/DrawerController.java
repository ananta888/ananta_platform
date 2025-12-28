package com.sovworks.eds.android.navigdrawer;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.sovworks.eds.android.filemanager.activities.FileManagerActivity;
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase;

import java.util.ArrayList;
import java.util.List;

public class DrawerController extends DrawerControllerBase
{
    public DrawerController(AppCompatActivity activity)
    {
        super(activity);
    }

    @Override
    protected List<DrawerMenuItemBase> fillDrawer()
    {
        Intent i = getMainActivity().getIntent();
        boolean isSelectAction = (getMainActivity() instanceof Host) && ((Host)getMainActivity()).isSelectAction();
        ArrayList<DrawerMenuItemBase> list = new ArrayList<>();
        DrawerAdapter adapter = new DrawerAdapter(list);
        if(i.getBooleanExtra(FileManagerActivityBase.EXTRA_ALLOW_BROWSE_CONTAINERS, true))
            adapter.add(new DrawerContainersMenu(this));
        if(i.getBooleanExtra(FileManagerActivityBase.EXTRA_ALLOW_BROWSE_DEVICE, true))
            adapter.add(new DrawerLocalFilesMenu(this));
        if(!isSelectAction)
        {
            adapter.add(new DrawerSettingsMenuItem(this));
            adapter.add(new DrawerHelpMenuItem(this));
            adapter.add(new DrawerAboutMenuItem(this));
            adapter.add(new DrawerExitMenuItem(this));
        }
        getDrawerListView().setAdapter(adapter);
        return list;
    }
}
