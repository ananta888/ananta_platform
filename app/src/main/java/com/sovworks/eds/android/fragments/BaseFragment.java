package com.sovworks.eds.android.fragments;

import androidx.fragment.app.Fragment;
import com.sovworks.eds.android.helpers.RxLifecycleProvider;
import io.reactivex.rxjava3.core.CompletableTransformer;
import io.reactivex.rxjava3.core.FlowableTransformer;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.core.ObservableTransformer;
import io.reactivex.rxjava3.core.SingleTransformer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import io.reactivex.rxjava3.disposables.Disposable;

public abstract class BaseFragment extends Fragment implements RxLifecycleProvider {
    protected final CompositeDisposable _disposables = new CompositeDisposable();

    @Override
    public <T> ObservableTransformer<T, T> bindToLifecycle() {
        return upstream -> upstream.doOnSubscribe(_disposables::add);
    }

    @Override
    public <T> FlowableTransformer<T, T> bindToLifecycleFlowable() {
        return upstream -> upstream.doOnSubscribe(s -> _disposables.add(Disposable.fromAction(s::cancel)));
    }

    @Override
    public <T> SingleTransformer<T, T> bindToLifecycleSingle() {
        return upstream -> upstream.doOnSubscribe(_disposables::add);
    }

    @Override
    public <T> MaybeTransformer<T, T> bindToLifecycleMaybe() {
        return upstream -> upstream.doOnSubscribe(_disposables::add);
    }

    @Override
    public CompletableTransformer bindToLifecycleCompletable() {
        return upstream -> upstream.doOnSubscribe(_disposables::add);
    }

    @Override
    public void onDestroy() {
        _disposables.clear();
        super.onDestroy();
    }
}
