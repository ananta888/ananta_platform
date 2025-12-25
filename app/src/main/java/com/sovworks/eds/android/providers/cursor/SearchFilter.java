package com.sovworks.eds.android.providers.cursor;

import com.sovworks.eds.android.helpers.CachedPathInfo;
import com.sovworks.eds.locations.Location;

import io.reactivex.rxjava3.functions.Predicate;

public interface SearchFilter
{
    String getName();
    Predicate<CachedPathInfo> getChecker(Location location, String arg);
}
