package com.sovworks.eds.android.navigdrawer;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.filemanager.activities.FileManagerActivityBase;
import com.sovworks.eds.android.filemanager.fragments.SearchFragment;

public class DrawerSearchMenuItem extends DrawerSimpleMenuItem
{
    public DrawerSearchMenuItem(DrawerControllerBase drawerController)
    {
        super(drawerController, R.string.file_search, R.drawable.ic_menu_search);
    }

    @Override
    public void onClick(View view, int position)
    {
        super.onClick(view, position);
        AppCompatActivity activity = getDrawerController().getMainActivity();
        if (activity instanceof FileManagerActivityBase)
        {
            ((FileManagerActivityBase)activity).showSecondaryFragment(SearchFragment.newInstance(), "search_fragment");
        }
    }
}
