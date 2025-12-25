package com.sovworks.eds.android.helpers;

import io.reactivex.rxjava3.core.ObservableTransformer;

public interface RxLifecycleProvider {
    <T> ObservableTransformer<T, T> bindToLifecycle();
}
