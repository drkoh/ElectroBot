Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class CredReaderElectro {
    [DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredRead(string target, int type, int reservedFlag, out IntPtr credentialPtr);
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public int Flags;
        public int Type;
        public string TargetName;
        public string Comment;
        public long LastWritten;
        public int CredentialBlobSize;
        public IntPtr CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }
    public static string GetPassword(string target) {
        IntPtr credPtr;
        if (CredRead(target, 1, 0, out credPtr)) {
            CREDENTIAL cred = (CREDENTIAL)Marshal.PtrToStructure(credPtr, typeof(CREDENTIAL));
            byte[] b = new byte[cred.CredentialBlobSize];
            Marshal.Copy(cred.CredentialBlob, b, 0, cred.CredentialBlobSize);
            return System.Text.Encoding.Unicode.GetString(b);
        }
        return null;
    }
}
"@

$token = [CredReaderElectro]::GetPassword("git:https://github.com")
if (-not $token) {
    Write-Error "Could not retrieve GitHub token from Windows Credential Manager."
    exit 1
}

$repoOwner = "drkoh"
$repoName = "ElectroBot"
$repo = "$repoOwner/$repoName"

$headers = @{
    "Authorization" = "token $token"
    "User-Agent" = "ElectroBot-Publisher"
    "Accept" = "application/vnd.github.v3+json"
}

Write-Output "Step 0: Checking if remote repository '$repo' exists on GitHub..."
try {
    $existingRepo = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo" -Headers $headers -Method Get -ErrorAction Stop
    Write-Output "Repository '$repo' exists (HTML URL: $($existingRepo.html_url))"
} catch {
    Write-Output "Repository '$repo' not found. Creating repository via GitHub API..."
    $createRepoBody = @{
        name = $repoName
        description = "ElectroBot — Інтелектуальний моніторинг відключень світла та аварійних робіт у Харкові (Desktop + Android)"
        private = $false
        has_issues = $true
        has_projects = $true
        has_wiki = $false
    } | ConvertTo-Json

    $newRepo = Invoke-RestMethod -Uri "https://api.github.com/user/repos" -Headers $headers -Method Post -Body $createRepoBody -ContentType "application/json; charset=utf-8"
    Write-Output "Repository created successfully: $($newRepo.html_url)"
}

Write-Output "Step 1: Preparing local Git repository..."
Set-Location "c:\Development\ElectroBot"
if (-not (Test-Path ".git")) {
    git init -b master
}
git config user.name "drkoh"
git config user.email "drkoh@users.noreply.github.com"

# Setup remote origin
$originUrl = "https://$($token)@github.com/$repo.git"
$remotes = git remote
if ($remotes -contains "origin") {
    git remote set-url origin $originUrl
} else {
    git remote add origin $originUrl
}

git add .
$status = git status --porcelain
if ($status) {
    git commit -m "ElectroBot v1.1: Реліз з оновленням через GitHub, сервісом 1562, Material Design 3 та Android APK"
} else {
    Write-Output "Working tree clean, nothing new to commit."
}

Write-Output "Step 2: Pushing master branch to GitHub..."
git push -u origin master --force

Write-Output "Step 3: Creating and pushing tag v1.1..."
git tag -f v1.1
git push origin v1.1 --force

Write-Output "Step 4: Checking if release v1.1 already exists on GitHub..."
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/v1.1" -Headers $headers -Method Get -ErrorAction Stop
    Write-Output "Found existing release for v1.1 (ID: $($release.id))"
} catch {
    Write-Output "Release v1.1 does not exist yet. Creating new release..."
}

$releaseNotes = @"
# ElectroBot v1.1 — Інтелектуальний моніторинг Харкова (Desktop + Android)

### 🚀 Що нового у версії 1.1:
1. **Автоматичне оновлення через GitHub (In-App GitHub Updater)**:
   - Вбудована перевірка та завантаження релізів з `drkoh/ElectroBot` в один клік як для Windows Desktop, так і для Android APK.
2. **Інтеграція АСДС «1562» Харкова**:
   - Повноцінний моніторинг оперативних робіт КП «Харківські теплові мережі», АТ «Харківобленерго», КП «Харківводоканал».
   - Виправлено розпізнавання служб (коректне найменування організацій замість скорочень).
3. **Ергономіка та Google Material Design 3**:
   - Контрастні тональні поверхні без блідих шрифтів на рожевому тлі.
   - Першим екраном за замовчуванням відкривається **«Графік та таймлайн»** з наочним відліком до зміни стану електропостачання.
   - Повна підтримка контекстного меню (миша) та системного буфера обміну (`Ctrl+C`, `Ctrl+V`, `Ctrl+X`).
4. **Нативні сповіщення Windows (Zero-Console Architecture)**:
   - Сповіщення більше не показують заголовок «Python» — тепер використовується нативний Windows Toast з ідентифікатором додатку **ElectroBot**.
   - Нативний бінарний GUI-лаунчер `ElectroBot.exe` без спалахів чорних вікон консолі.
5. **Повнофункціональний мобільний додаток Android**:
   - Підтримка Android 8.0+ з Jetpack Compose, Material 3, пігулками підчерг та оновленням через FileProvider.
"@

if (-not $release) {
    $bodyJson = @{
        tag_name = "v1.1"
        target_commitish = "master"
        name = "ElectroBot v1.1 (Desktop & Android)"
        body = $releaseNotes
        draft = $false
        prerelease = $false
    } | ConvertTo-Json

    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Headers $headers -Body $bodyJson -ContentType "application/json; charset=utf-8"
    Write-Output "Created release v1.1 with ID: $($release.id)"
}

$releaseId = $release.id

Write-Output "Step 5: Cleaning up previous assets in release $releaseId if any..."
if ($release.assets) {
    foreach ($a in $release.assets) {
        if ($a.name -like "*ElectroBot*") {
            Write-Output "Deleting previous asset $($a.name) (ID: $($a.id))..."
            Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/assets/$($a.id)" -Method Delete -Headers $headers
        }
    }
}

# Upload APK asset
$apkPath = "c:\Development\ElectroBot\ElectroBot-v1.1-mobile.apk"
if (Test-Path $apkPath) {
    Write-Output "Step 6a: Uploading Android APK asset ($((Get-Item $apkPath).Length / 1MB | ForEach-Object { '{0:N2} MB' -f $_ }))..."
    $uploadUrlApk = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=ElectroBot-v1.1-mobile.apk"
    $bytesApk = [System.IO.File]::ReadAllBytes($apkPath)
    $uploadHeadersApk = @{
        "Authorization" = "token $token"
        "User-Agent" = "ElectroBot-Publisher"
        "Content-Type" = "application/vnd.android.package-archive"
    }
    $respApk = Invoke-RestMethod -Uri $uploadUrlApk -Method Post -Headers $uploadHeadersApk -Body $bytesApk
    Write-Output "APK uploaded successfully! Download URL: $($respApk.browser_download_url)"
} else {
    Write-Warning "APK file not found at $apkPath"
}

# Upload Windows ZIP asset
$zipPath = "c:\Development\ElectroBot\ElectroBot-v1.1-windows.zip"
if (Test-Path $zipPath) {
    Write-Output "Step 6b: Uploading Windows Desktop ZIP asset ($((Get-Item $zipPath).Length / 1MB | ForEach-Object { '{0:N2} MB' -f $_ }))..."
    $uploadUrlZip = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=ElectroBot-v1.1-windows.zip"
    $bytesZip = [System.IO.File]::ReadAllBytes($zipPath)
    $uploadHeadersZip = @{
        "Authorization" = "token $token"
        "User-Agent" = "ElectroBot-Publisher"
        "Content-Type" = "application/zip"
    }
    $respZip = Invoke-RestMethod -Uri $uploadUrlZip -Method Post -Headers $uploadHeadersZip -Body $bytesZip
    Write-Output "ZIP uploaded successfully! Download URL: $($respZip.browser_download_url)"
}

Write-Output "=========================================================="
Write-Output "🎉 ВСІ ПРОЦЕСИ УСПІШНО ЗАВЕРШЕНО!"
Write-Output "Репозиторій: https://github.com/$repo"
Write-Output "Реліз:       https://github.com/$repo/releases/tag/v1.1"
Write-Output "=========================================================="
