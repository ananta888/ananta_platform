package com.sovworks.eds.android.helpers;

import io.reactivex.rxjava3.core.CompletableTransformer;
import io.reactivex.rxjava3.core.FlowableTransformer;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.core.ObservableTransformer;
import io.reactivex.rxjava3.core.SingleTransformer;

public interface RxLifecycleProvider {
    <T> ObservableTransformer<T, T> bindToLifecycle();
    <T> FlowableTransformer<T, T> bindToLifecycleFlowable();
    <T> SingleTransformer<T, T> bindToLifecycleSingle();
    <T> MaybeTransformer<T, T> bindToLifecycleMaybe();
    CompletableTransformer bindToLifecycleCompletable();
}
