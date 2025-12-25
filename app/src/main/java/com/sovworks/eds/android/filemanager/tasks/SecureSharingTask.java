package com.sovworks.eds.android.filemanager.tasks;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.fragments.TaskFragment;
import com.sovworks.eds.android.service.FileOpsService;
import com.sovworks.eds.fs.Path;
import com.sovworks.eds.locations.Location;
import com.sovworks.eds.locations.LocationsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SecureSharingTask extends TaskFragment {
    public static final String TAG = "SecureSharingTask";
    public static final String EXTRA_PASSPHRASE = "passphrase";
    public static final String EXTRA_PUBLIC_KEYS = "public_keys";

    public static SecureSharingTask newInstance(Location loc, Collection<? extends Path> paths, String passphrase, List<String> publicKeys) {
        Bundle args = new Bundle();
        LocationsManager.storePathsInBundle(args, loc, paths);
        args.putString(EXTRA_PASSPHRASE, passphrase);
        if (publicKeys != null) {
            args.putStringArrayList(EXTRA_PUBLIC_KEYS, new ArrayList<>(publicKeys));
        }
        SecureSharingTask f = new SecureSharingTask();
        f.setArguments(args);
        return f;
    }

    @Override
    public void initTask(Activity activity) {
        _context = activity.getApplicationContext();
    }

    protected Context _context;

    @Override
    protected void doWork(TaskState state) throws Exception {
        ArrayList<Path> paths = new ArrayList<>();
        Location location = LocationsManager.getLocationsManager(_context).getFromBundle(getArguments(), paths);
        
        String passphrase = getArguments().getString(EXTRA_PASSPHRASE);
        ArrayList<String> publicKeys = getArguments().getStringArrayList(EXTRA_PUBLIC_KEYS);

        SecureSharingResult result = new SecureSharingResult();
        result.location = location;
        result.paths = paths;
        result.passphrase = passphrase;
        result.publicKeys = publicKeys;
        
        state.setResult(result);
    }

    @Override
    protected TaskCallbacks getTaskCallbacks(final Activity activity) {
        return new TaskCallbacks() {
            @Override
            public void onUpdateUI(Object state) {}

            @Override
            public void onPrepare(Bundle args) {}

            @Override
            public void onResumeUI(Bundle args) {}

            @Override
            public void onSuspendUI(Bundle args) {}

            @Override
            public void onCompleted(Bundle args, Result result) {
                try {
                    SecureSharingResult res = (SecureSharingResult) result.getResult();
                    // Hier rufen wir den Service auf, um die Verschlüsselung und das Senden durchzuführen
                    // Da wir FileOpsServiceBase nicht sofort ändern wollen, können wir einen neuen Intent-Typ definieren
                    // oder eine neue statische Methode in FileOpsService hinzufügen.
                    FileOpsService.secureSendFile(activity, res.location, res.paths, res.passphrase, res.publicKeys);
                } catch (Throwable e) {
                    Logger.showAndLog(activity, e);
                }
            }
        };
    }

    private static class SecureSharingResult {
        public Location location;
        public ArrayList<Path> paths;
        public String passphrase;
        public ArrayList<String> publicKeys;
    }
}
