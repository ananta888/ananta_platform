package com.sovworks.eds.android.navigdrawer;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.activities.LogActivity;

public class DrawerLogMenuItem extends DrawerMenuItemBase
{
    public DrawerLogMenuItem(DrawerControllerBase drawerController)
    {
        super(drawerController);
    }

    @Override
    public String getTitle()
    {
        return getContext().getString(R.string.log_terminal);
    }

    @Override
    public void onClick(View view, int position)
    {
        super.onClick(view, position);
        getContext().startActivity(new Intent(getContext(), LogActivity.class));
    }

    @Override
    public Drawable getIcon()
    {
        return getIcon(getContext());
    }

    private synchronized static Drawable getIcon(Context context)
    {
        if(_icon == null)
        {
            // We use info icon for logs
            _icon = context.getResources().getDrawable(R.drawable.ic_menu_info);
        }
        return _icon;
    }

    private static Drawable _icon;
}
