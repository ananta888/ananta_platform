package com.sovworks.eds.android.navigdrawer;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.activities.PairingActivity;
import java.util.ArrayList;
import java.util.Collection;

public class DrawerExchangeMenu extends DrawerSubMenuBase
{
    @Override
    public String getTitle()
    {
        return getContext().getString(R.string.secure_share);
    }

    public DrawerExchangeMenu(DrawerControllerBase drawerController)
    {
        super(drawerController);
    }

    @Override
    protected Collection<DrawerMenuItemBase> getSubItems()
    {
        ArrayList<DrawerMenuItemBase> res = new ArrayList<>();
        res.add(new DrawerSimpleMenuItem(getDrawerController(), R.string.start_exchange, R.drawable.ic_menu_share));
        res.add(new DrawerSimpleMenuItem(getDrawerController(), R.string.device_pairing, R.drawable.ic_menu_fullscreen, PairingActivity.class));
        return res;
    }
}
