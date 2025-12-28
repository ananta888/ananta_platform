package com.sovworks.eds.android.navigdrawer;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.activities.PeersActivity;
import java.util.ArrayList;
import java.util.Collection;

public class DrawerTrustMenu extends DrawerSubMenuBase
{
    @Override
    public String getTitle()
    {
        return getContext().getString(R.string.trust_chain);
    }

    public DrawerTrustMenu(DrawerControllerBase drawerController)
    {
        super(drawerController);
    }

    @Override
    protected Collection<DrawerMenuItemBase> getSubItems()
    {
        ArrayList<DrawerMenuItemBase> res = new ArrayList<>();
        res.add(new DrawerSimpleMenuItem(getDrawerController(), R.string.trust_chain, R.drawable.ic_menu_protect));
        res.add(new DrawerSimpleMenuItem(getDrawerController(), R.string.peers_devices, R.drawable.ic_menu_info, PeersActivity.class));
        return res;
    }
}
