$files = Get-ChildItem -Path app\src\main\java -Recurse -Include *.java
foreach ($file in $files) {
    $content = Get-Content $file.FullName
    $content = $content -replace 'import io.reactivex.Observable;', 'import io.reactivex.rxjava3.core.Observable;'
    $content = $content -replace 'import io.reactivex.Flowable;', 'import io.reactivex.rxjava3.core.Flowable;'
    $content = $content -replace 'import io.reactivex.Single;', 'import io.reactivex.rxjava3.core.Single;'
    $content = $content -replace 'import io.reactivex.Maybe;', 'import io.reactivex.rxjava3.core.Maybe;'
    $content = $content -replace 'import io.reactivex.Completable;', 'import io.reactivex.rxjava3.core.Completable;'
    $content = $content -replace 'import io.reactivex.disposables.', 'import io.reactivex.rxjava3.disposables.'
    $content = $content -replace 'import io.reactivex.functions.', 'import io.reactivex.rxjava3.functions.'
    $content = $content -replace 'import io.reactivex.schedulers.Schedulers;', 'import io.reactivex.rxjava3.schedulers.Schedulers;'
    $content = $content -replace 'import io.reactivex.android.schedulers.AndroidSchedulers;', 'import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;'
    $content = $content -replace 'import io.reactivex.subjects.', 'import io.reactivex.rxjava3.subjects.'
    $content = $content -replace 'import io.reactivex.processors.', 'import io.reactivex.rxjava3.processors.'
    $content = $content -replace 'import io.reactivex.observers.', 'import io.reactivex.rxjava3.observers.'
    $content = $content -replace 'import io.reactivex.annotations.', 'import io.reactivex.rxjava3.annotations.'
    $content = $content -replace 'import io.reactivex.exceptions.', 'import io.reactivex.rxjava3.exceptions.'
    $content = $content -replace 'import io.reactivex.ObservableTransformer;', 'import io.reactivex.rxjava3.core.ObservableTransformer;'
    $content = $content -replace 'import io.reactivex.ObservableSource;', 'import io.reactivex.rxjava3.core.ObservableSource;'
    $content = $content -replace 'import io.reactivex.ObservableOnSubscribe;', 'import io.reactivex.rxjava3.core.ObservableOnSubscribe;'
    $content = $content -replace 'import io.reactivex.ObservableEmitter;', 'import io.reactivex.rxjava3.core.ObservableEmitter;'
    $content = $content -replace 'import io.reactivex.SingleSource;', 'import io.reactivex.rxjava3.core.SingleSource;'
    $content = $content -replace 'import io.reactivex.SingleOnSubscribe;', 'import io.reactivex.rxjava3.core.SingleOnSubscribe;'
    $content = $content -replace 'import io.reactivex.SingleEmitter;', 'import io.reactivex.rxjava3.core.SingleEmitter;'
    $content = $content -replace 'import io.reactivex.BackpressureStrategy;', 'import io.reactivex.rxjava3.core.BackpressureStrategy;'
    Set-Content $file.FullName $content
}
