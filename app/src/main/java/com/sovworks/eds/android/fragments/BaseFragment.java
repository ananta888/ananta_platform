package com.sovworks.eds.android.fragments;

import androidx.fragment.app.Fragment;
import com.sovworks.eds.android.helpers.RxLifecycleProvider;
import io.reactivex.rxjava3.core.ObservableTransformer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public abstract class BaseFragment extends Fragment implements RxLifecycleProvider {
    protected final CompositeDisposable _disposables = new CompositeDisposable();

    @Override
    public <T> ObservableTransformer<T, T> bindToLifecycle() {
        return upstream -> upstream.doOnSubscribe(_disposables::add);
    }

    @Override
    public void onDestroy() {
        _disposables.clear();
        super.onDestroy();
    }
}
