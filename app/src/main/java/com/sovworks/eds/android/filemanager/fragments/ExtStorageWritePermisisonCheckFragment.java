package com.sovworks.eds.android.filemanager.fragments;

import android.Manifest;
import android.annotation.TargetApi;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.sovworks.eds.android.dialogs.AskPrimaryStoragePermissionDialog;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.subjects.CompletableSubject;


public class ExtStorageWritePermisisonCheckFragment extends Fragment
{
    public static final String TAG = "com.sovworks.eds.android.filemanager.fragments.ExtStorageWritePermisisonCheckFragment";

    public static Completable getObservable(AppCompatActivity activity)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (
                ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED)
        )
            return Completable.complete();

        FragmentManager fm = activity.getSupportFragmentManager();
        ExtStorageWritePermisisonCheckFragment f = (ExtStorageWritePermisisonCheckFragment) fm.findFragmentByTag(TAG);
        if(f == null)
        {
            f = new ExtStorageWritePermisisonCheckFragment();
            activity.getSupportFragmentManager().beginTransaction().add(f, TAG).commit();
        }
        return f._extStoragePermissionCheckSubject;
    }

    private boolean _permissionRequested = false;

    @Override
    public void onResume()
    {
        super.onResume();
        if(!_permissionRequested)
        {
            _permissionRequested = true;
            requestExtStoragePermission();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void requestExtStoragePermission()
    {
        requestPermissions(
                new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_EXT_STORAGE_PERMISSIONS);
    }

    public void cancelExtStoragePermissionRequest()
    {
        _extStoragePermissionCheckSubject.onComplete();
        getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_EXT_STORAGE_PERMISSIONS)
        {
            if((grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) ||
                    !requestExtStoragePermissionWithRationale())
            {
                _extStoragePermissionCheckSubject.onComplete();
                getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
            }

        }
    }

    private static final int REQUEST_EXT_STORAGE_PERMISSIONS = 1;
    private final CompletableSubject _extStoragePermissionCheckSubject = CompletableSubject.create();


    @TargetApi(Build.VERSION_CODES.M)
    private boolean requestExtStoragePermissionWithRationale()
    {
        if (shouldShowRequestPermissionRationale(
                Manifest.permission.READ_EXTERNAL_STORAGE)
                || shouldShowRequestPermissionRationale(
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
            AskPrimaryStoragePermissionDialog.showDialog(getParentFragmentManager());
            return true;
        }
        return false;
    }
}

