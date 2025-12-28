package com.sovworks.eds.android.helpers;


import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.dialogs.MasterPasswordDialog;
import com.sovworks.eds.android.errors.UserException;
import com.sovworks.eds.android.filemanager.fragments.ExtStorageWritePermisisonCheckFragment;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.CancellationException;

import io.reactivex.rxjava3.core.CompletableEmitter;

public class AppInitHelper extends AppInitHelperBase
{
    AppInitHelper(AppCompatActivity activity, CompletableEmitter emitter)
    {
        super(activity, emitter);
    }

    void startInitSequence()
    {
        MasterPasswordDialog.getObservable(_activity).
                flatMapCompletable(isValidPassword ->
                {
                    if (isValidPassword)
                        return ExtStorageWritePermisisonCheckFragment.getObservable(_activity);
                    throw new UserException(_activity, R.string.invalid_master_password);
                }).

                compose(((RxLifecycleProvider)_activity).bindToLifecycleCompletable()).
                subscribe(() -> {
                    convertLegacySettings();
                    _initFinished.onComplete();
                }, err ->
                {
                    if(!(err instanceof CancellationException))
                        Logger.log(err);
                    _initFinished.onError(err);
                });
    }
}
