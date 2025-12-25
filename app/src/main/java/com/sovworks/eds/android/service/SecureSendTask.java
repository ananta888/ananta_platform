package com.sovworks.eds.android.service;

import android.content.Context;
import android.content.Intent;

import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.crypto.age.AgeEncryptionEngine;
import com.sovworks.eds.android.helpers.TempFilesMonitor;
import com.sovworks.eds.android.settings.UserSettings;
import com.sovworks.eds.fs.Directory;
import com.sovworks.eds.fs.File;
import com.sovworks.eds.fs.Path;
import com.sovworks.eds.fs.util.SrcDstCollection;
import com.sovworks.eds.fs.util.SrcDstGroup;
import com.sovworks.eds.fs.util.SrcDstSingle;
import com.sovworks.eds.locations.Location;
import com.sovworks.eds.locations.LocationsManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

class SecureSendTask extends CopyFilesTask {
    public static class Param extends CopyFilesTaskParam {
        private final Context _context;
        private final String passphrase;
        private final List<String> publicKeys;

        Param(Intent i, Context context) {
            super(i);
            _context = context;
            passphrase = i.getStringExtra("passphrase");
            publicKeys = i.getStringArrayListExtra("public_keys");
        }

        @Override
        protected SrcDstCollection loadRecords(Intent i) {
            ArrayList<Path> paths = new ArrayList<>();
            Location loc = LocationsManager.getLocationsManager(_context).getFromIntent(i, paths);
            String wd = UserSettings.getSettings(_context).getWorkDir();
            ArrayList<SrcDstCollection> cols = new ArrayList<>();
            try {
                for (Path srcPath : paths) {
                    Location srcLoc = loc.copy();
                    srcLoc.setCurrentPath(srcPath);
                    // Wir teilen immer in den sicheren Temp-Ordner
                    Location dstLoc = FileOpsService.getSecTempFolderLocation(wd, _context);
                    SrcDstSingle sds = new SrcDstSingle(srcLoc, dstLoc);
                    cols.add(sds);
                }
                return new SrcDstGroup(cols);
            } catch (IOException e) {
                Logger.log(e);
            }
            return null;
        }

        @Override
        public boolean forceOverwrite() {
            return true;
        }
    }

    private final List<Location> _tempFilesList = new ArrayList<>();

    @Override
    public Object doWork(Context context, Intent i) throws Throwable {
        super.doWork(context, i);
        return _tempFilesList;
    }

    @Override
    protected CopyFilesTaskParam initParam(Intent i) {
        return new Param(i, _context);
    }

    @Override
    protected boolean copyFile(File srcFile, Directory targetFolder) throws IOException {
        String srcName = srcFile.getName() + ".age";
        _currentStatus.fileName = srcName;
        updateUIOnTime();
        
        // Immer neue Datei erstellen
        File dstFile = targetFolder.createFile(srcName);
        return copyFile(srcFile, dstFile);
    }

    @Override
    protected boolean copyFile(File srcFile, File dstFile) throws IOException {
        Param p = (Param) getParam();
        InputStream fin = null;
        OutputStream fout = null;
        try {
            fin = srcFile.getInputStream();
            fout = dstFile.getOutputStream();
            
            if (p.passphrase != null && !p.passphrase.isEmpty()) {
                AgeEncryptionEngine.encryptWithPassphrase(fin, fout, p.passphrase.toCharArray());
            } else if (p.publicKeys != null && !p.publicKeys.isEmpty()) {
                AgeEncryptionEngine.encryptWithPublicKeys(fin, fout, p.publicKeys);
            } else {
                throw new IOException("No encryption parameters provided");
            }
            
            // Da age-Verschlüsselung die Größe ändert, können wir incProcessedSize hier nicht genau währenddessen machen,
            // aber wir können die Originalgröße als Fortschritt nehmen.
            incProcessedSize((int) srcFile.getSize());
        } catch (Exception e) {
            throw new IOException("Encryption failed: " + e.getMessage(), e);
        } finally {
            if (fin != null) fin.close();
            if (fout != null) fout.close();
        }
        
        Location dstLoc = ((Param)getParam()).getOverwriteTargetsStorage().get(0).getDstLocation().copy();
        dstLoc.setCurrentPath(dstFile.getPath());
        _tempFilesList.add(dstLoc);
        
        return true;
    }

    @Override
    public void onCompleted(Result result) {
        try {
            @SuppressWarnings("unchecked") List<Location> tmpFilesList = (List<Location>) result.getResult();
            ActionSendTask.sendFiles(_context, tmpFilesList, "application/octet-stream");
        } catch (CancellationException ignored) {
        } catch (Throwable e) {
            reportError(e);
        } finally {
            // Wir löschen die Temp-Files NICHT sofort, da der Share-Intent sie braucht.
            // EDS löscht den Temp-Ordner beim Beenden.
        }
    }
}
