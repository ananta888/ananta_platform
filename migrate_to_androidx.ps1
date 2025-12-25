$mappings = @{
    "android.support.annotation" = "androidx.annotation"
    "android.support.v4.app" = "androidx.fragment.app"
    "android.support.v4.content.ContextCompat" = "androidx.core.content.ContextCompat"
    "android.support.v4.content.LocalBroadcastManager" = "androidx.localbroadcastmanager.content.LocalBroadcastManager"
    "android.support.v4.view" = "androidx.core.view"
    "android.support.v4.util" = "androidx.core.util"
    "android.support.v4.provider.DocumentFile" = "androidx.documentfile.provider.DocumentFile"
    "android.support.v4.graphics.drawable.DrawableCompat" = "androidx.core.graphics.drawable.DrawableCompat"
    "android.support.v4.widget" = "androidx.drawerlayout.widget"
    "android.support.v7.app" = "androidx.appcompat.app"
    "android.support.v7.widget" = "androidx.appcompat.widget"
    "android.support.v7.preference" = "androidx.preference"
    "android.support.design" = "com.google.android.material"
    "android.support.multidex" = "androidx.multidex"
    "android.support.v13.app" = "androidx.legacy.app"
}

Get-ChildItem -Recurse -Include *.java,*.xml | ForEach-Object {
    $file = $_.FullName
    $content = Get-Content $file -Raw
    $changed = $false

    foreach ($key in $mappings.Keys) {
        if ($content -match [regex]::Escape($key)) {
            $content = $content -replace [regex]::Escape($key), $mappings[$key]
            $changed = $true
        }
    }

    if ($changed) {
        Set-Content $file $content
        Write-Host "Migrated: $file"
    }
}
