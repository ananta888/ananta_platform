$files = Get-ChildItem -Path app\src\main\java -Recurse -Include *.java
foreach ($file in $files) {
    $content = Get-Content $file.FullName
    $content = $content -replace 'import io.reactivex\.(Observable|Flowable|Single|Maybe|Completable|ObservableTransformer|ObservableSource|ObservableOnSubscribe|ObservableEmitter|SingleSource|SingleOnSubscribe|SingleEmitter|CompletableSource|CompletableOnSubscribe|CompletableEmitter|MaybeSource|MaybeOnSubscribe|MaybeEmitter|FlowableSource|FlowableOnSubscribe|FlowableEmitter|BackpressureStrategy|Observer|SingleObserver|MaybeObserver|CompletableObserver|FlowableSubscriber);', 'import io.reactivex.rxjava3.core.$1;'
    $content = $content -replace 'import io.reactivex\.disposables\.', 'import io.reactivex.rxjava3.disposables.'
    $content = $content -replace 'import io.reactivex\.functions\.', 'import io.reactivex.rxjava3.functions.'
    $content = $content -replace 'import io.reactivex\.schedulers\.', 'import io.reactivex.rxjava3.schedulers.'
    $content = $content -replace 'import io.reactivex\.android\.', 'import io.reactivex.rxjava3.android.'
    $content = $content -replace 'import io.reactivex\.subjects\.', 'import io.reactivex.rxjava3.subjects.'
    $content = $content -replace 'import io.reactivex\.processors\.', 'import io.reactivex.rxjava3.processors.'
    $content = $content -replace 'import io.reactivex\.observers\.', 'import io.reactivex.rxjava3.observers.'
    $content = $content -replace 'import io.reactivex\.annotations\.', 'import io.reactivex.rxjava3.annotations.'
    $content = $content -replace 'import io.reactivex\.exceptions\.', 'import io.reactivex.rxjava3.exceptions.'
    $content = $content -replace 'import io.reactivex\.parallel\.', 'import io.reactivex.rxjava3.parallel.'
    $content = $content -replace 'import io.reactivex\.plugins\.', 'import io.reactivex.rxjava3.plugins.'
    Set-Content $file.FullName $content
}
