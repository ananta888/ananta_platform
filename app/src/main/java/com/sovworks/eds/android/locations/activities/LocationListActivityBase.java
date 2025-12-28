package com.sovworks.eds.android.locations.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.os.Bundle;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.activities.DrawerActivityBase;
import com.sovworks.eds.android.helpers.CompatHelper;
import com.sovworks.eds.android.helpers.Util;
import com.sovworks.eds.android.locations.ContainerBasedLocation;
import com.sovworks.eds.android.locations.DocumentTreeLocation;
import com.sovworks.eds.android.locations.fragments.ContainerListFragment;
import com.sovworks.eds.android.locations.fragments.DocumentTreeLocationsListFragment;
import com.sovworks.eds.android.locations.fragments.LocationListBaseFragment;
import com.sovworks.eds.android.settings.UserSettings;

public abstract class LocationListActivityBase extends DrawerActivityBase
{
    public static final String EXTRA_LOCATION_TYPE = "com.sovworks.eds.android.LOCATION_TYPE";

    @Override
	public void onCreate(Bundle savedInstanceState)
    {
        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        if(UserSettings.getSettings(this).isFlagSecureEnabled())
            CompatHelper.setWindowFlagSecure(this);
        if(savedInstanceState == null)
            getSupportFragmentManager().
                beginTransaction().
                add(R.id.content_frame, getCreateLocationFragment(), LocationListBaseFragment.TAG).
                commit();
    }

    protected Fragment getCreateLocationFragment()
    {
        switch (getIntent().getStringExtra(EXTRA_LOCATION_TYPE))
        {
            case ContainerBasedLocation.URI_SCHEME:
                return new ContainerListFragment();
            case DocumentTreeLocation.URI_SCHEME:
                return new DocumentTreeLocationsListFragment();
            default:
                throw new RuntimeException("Unknown location type");
        }
    }

}
