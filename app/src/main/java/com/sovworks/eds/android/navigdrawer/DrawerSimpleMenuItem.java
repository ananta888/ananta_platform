package com.sovworks.eds.android.navigdrawer;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Toast;
import com.sovworks.eds.android.R;

public class DrawerSimpleMenuItem extends DrawerMenuItemBase {
    private final int titleResId;
    private final int iconResId;
    private final Class<?> activityClass;

    public DrawerSimpleMenuItem(DrawerControllerBase drawerController, int titleResId, int iconResId) {
        this(drawerController, titleResId, iconResId, null);
    }

    public DrawerSimpleMenuItem(DrawerControllerBase drawerController, int titleResId, int iconResId, Class<?> activityClass) {
        super(drawerController);
        this.titleResId = titleResId;
        this.iconResId = iconResId;
        this.activityClass = activityClass;
    }

    @Override
    public String getTitle() {
        return getContext().getString(titleResId);
    }

    @Override
    public Drawable getIcon() {
        return getContext().getResources().getDrawable(iconResId);
    }

    @Override
    public void onClick(View view, int position) {
        super.onClick(view, position);
        if (activityClass != null) {
            getContext().startActivity(new Intent(getContext(), activityClass));
        } else {
            Toast.makeText(getContext(), getTitle() + " - Coming soon", Toast.LENGTH_SHORT).show();
        }
    }
}
