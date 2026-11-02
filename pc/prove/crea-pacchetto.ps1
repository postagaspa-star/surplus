<#
.SINOSSI
    Costruisce il pacchetto del programma per il computer: pc/dist/surplus-pc.zip.

    Fa `dotnet publish` self-contained per win-x64 A CARTELLA (non single-file): niente
    eseguibile che si scompatta da solo, che gli antivirus guardano male. Lo zip contiene
    una cartella `Surplus\` con `Surplus.exe`, le dll del runtime .NET e la cartella `ui`.
    Il programma gira da lì: non si installa da nessuna parte e i dati stanno altrove
    (%LOCALAPPDATA%\Surplus), così scompattare un aggiornamento sopra non tocca il diario.

    Usa lo SDK .NET 8 scompattato in %LOCALAPPDATA%\Microsoft\dotnet-sdk-8 (o quello nel PATH).

    Questo file è salvato in UTF-8 con BOM: PowerShell 5.1 legge i .ps1 senza BOM come ANSI.

.ESEMPIO
    powershell -ExecutionPolicy Bypass -File pc\prove\crea-pacchetto.ps1
#>
[CmdletBinding()]
param(
    # Dopo aver creato lo zip lo manda al NAS, da dove le app si aggiornano da sole.
    [switch]$Pubblica,
    [string]$Nas = $env:SURPLUS_NAS,
    [string]$UtenteNas = $env:SURPLUS_NAS_UTENTE,
    [string]$CartellaNas = '/volume1/docker/surplus-nas/pacchetti'
)
$ErrorActionPreference = 'Stop'

$radicePc = Split-Path -Parent $PSScriptRoot            # ...\pc
$progetto = Join-Path $radicePc 'Surplus\Surplus.csproj'
$dist = Join-Path $radicePc 'dist'
$staging = Join-Path $dist 'Surplus'                    # diventa la cartella dentro lo zip
$zip = Join-Path $dist 'surplus-pc.zip'

$dotnet = Join-Path $env:LOCALAPPDATA 'Microsoft\dotnet-sdk-8\dotnet.exe'
if (-not (Test-Path $dotnet)) {
    $c = Get-Command dotnet -ErrorAction SilentlyContinue
    if (-not $c) { throw "Non trovo dotnet. Scompatta lo SDK .NET 8 in $env:LOCALAPPDATA\Microsoft\dotnet-sdk-8." }
    $dotnet = $c.Source
}

foreach ($f in 'ui\index.html', 'ui\app.js', 'ui\schermate.js', 'ui\logica.js', 'ui\api.js', 'ui\stile.css') {
    # $($f) e non $f: in PowerShell "$f:" sarebbe una variabile con lo scope "f".
    if (-not (Test-Path (Join-Path $radicePc $f))) { throw "Manca pc\$($f): senza interfaccia il pacchetto non serve." }
}

Write-Host "Prove della logica (node)..." -ForegroundColor Cyan
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    # I file uno per uno: a `node --test` una cartella su Windows va di traverso
    # (prova a caricarla come se fosse un modulo e fallisce senza eseguire niente).
    $prove = @(Get-ChildItem (Join-Path $radicePc 'prove\js') -Filter '*.test.js' | ForEach-Object { $_.FullName })
    if ($prove.Count -eq 0) { throw 'Nessuna prova trovata in pc\prove\js.' }
    & node --test $prove
    if ($LASTEXITCODE -ne 0) { throw "le prove della logica non passano (codice $LASTEXITCODE)" }
} else {
    Write-Warning 'node non trovato: salto le prove della logica.'
}

Write-Host "Prove del programma (dotnet)..." -ForegroundColor Cyan
& $dotnet test (Join-Path $radicePc 'Surplus.Tests\Surplus.Tests.csproj') --nologo
if ($LASTEXITCODE -ne 0) { throw "le prove del programma non passano (codice $LASTEXITCODE)" }

$cartellaServer = Join-Path (Split-Path -Parent $radicePc) 'server'
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python -and (Test-Path (Join-Path $cartellaServer 'tests'))) {
    Write-Host "Prove del server del NAS (pytest)..." -ForegroundColor Cyan
    Push-Location $cartellaServer
    try {
        & $python.Source -m pytest -q
        if ($LASTEXITCODE -ne 0) { throw "le prove del server non passano (codice $LASTEXITCODE)" }
    } finally { Pop-Location }
} else {
    Write-Warning 'python non trovato: salto le prove del server.'
}

Write-Host "Pulizia..." -ForegroundColor Cyan
Remove-Item -Recurse -Force $staging -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $dist | Out-Null

$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_NOLOGO = '1'

Write-Host "Pubblicazione (self-contained, a cartella)..." -ForegroundColor Cyan
& $dotnet publish $progetto -c Release -r win-x64 "-p:PubblicazioneSurplus=true" -o $staging
if ($LASTEXITCODE -ne 0) { throw "publish fallito (codice $LASTEXITCODE)" }

$exe = Join-Path $staging 'Surplus.exe'
if (-not (Test-Path $exe)) { throw "Surplus.exe non prodotto." }
if (-not (Test-Path (Join-Path $staging 'ui\index.html'))) { throw "La cartella ui non è finita accanto all'exe." }
if (-not (Test-Path (Join-Path $staging 'coreclr.dll'))) { throw "Manca coreclr.dll: la pubblicazione non è self-contained a cartella." }
$versione = (Get-Item $exe).VersionInfo.FileVersion

# versione.json dentro la cartella: lo legge l'aggiornamento automatico per sapere
# qual è davvero la versione del pacchetto (sta dentro lo zip, quindi è firmato anche lui).
$sorgenteVersione = Get-Content (Join-Path $radicePc 'Surplus\Versione.cs') -Raw -Encoding UTF8
if ($sorgenteVersione -notmatch 'Nome\s*=\s*"([^"]+)"') { throw 'Non trovo il nome della versione in Versione.cs.' }
$nomeVersione = $Matches[1]
if ($sorgenteVersione -notmatch 'Codice\s*=\s*(\d+)') { throw 'Non trovo il codice della versione in Versione.cs.' }
$codiceVersione = [int]$Matches[1]
# Il numero sta scritto in due posti (Versione.cs e il .csproj): se non coincidono
# il programma direbbe una versione e le proprieta' del file un'altra.
if ($versione -notlike "$nomeVersione*") {
    throw "Versione.cs dice $nomeVersione ma l'eseguibile dice $versione : allinea <Version> nel .csproj."
}
$json = '{"codice":' + $codiceVersione + ',"nome":"' + $nomeVersione + '"}'
[System.IO.File]::WriteAllText((Join-Path $staging 'versione.json'), $json, (New-Object System.Text.UTF8Encoding $false))

Write-Host "Creo lo zip..." -ForegroundColor Cyan
Remove-Item $zip -ErrorAction SilentlyContinue
# -Path sulla cartella (senza \*): lo zip contiene la cartella Surplus\ con tutto dentro.
Compress-Archive -Path $staging -DestinationPath $zip -CompressionLevel Optimal

# La firma: senza di questa il programma installato SCARTA il pacchetto. La chiave
# privata non sta nel repository ne' sul NAS: solo sul computer di chi pubblica.
$fileChiave = if ($env:SURPLUS_FIRMA) { $env:SURPLUS_FIRMA } else { Join-Path $env:USERPROFILE 'surplus-keys\firma-pc.json' }
$firma = $null
if (Test-Path $fileChiave) {
    Write-Host "Firmo il pacchetto..." -ForegroundColor Cyan
    $k = Get-Content $fileChiave -Raw | ConvertFrom-Json
    $parametri = New-Object System.Security.Cryptography.ECParameters
    $parametri.Curve = [System.Security.Cryptography.ECCurve]::CreateFromFriendlyName('nistP256')
    $punto = New-Object System.Security.Cryptography.ECPoint
    $punto.X = [Convert]::FromBase64String($k.x)
    $punto.Y = [Convert]::FromBase64String($k.y)
    $parametri.Q = $punto
    $parametri.D = [Convert]::FromBase64String($k.d)
    $ecdsa = [System.Security.Cryptography.ECDsa]::Create($parametri)
    $flusso = [System.IO.File]::OpenRead($zip)
    try {
        $firma = [Convert]::ToBase64String($ecdsa.SignData($flusso, [System.Security.Cryptography.HashAlgorithmName]::SHA256))
    } finally { $flusso.Dispose() }
    [System.IO.File]::WriteAllText("$zip.sig", $firma, (New-Object System.Text.ASCIIEncoding))
} else {
    Write-Warning "Chiave di firma non trovata in $fileChiave : il pacchetto NON sara' firmato e i Surplus installati lo scarteranno."
}

if ($Pubblica) {
    if (-not $firma) { throw 'Senza firma non pubblico niente: i programmi installati lo scarterebbero.' }
    # Indirizzo e utente del NAS non stanno nel repository: li passi tu, una volta.
    if (-not $Nas -or -not $UtenteNas) {
        throw "Dimmi dov'e' il NAS: -Nas <indirizzo> -UtenteNas <nome>, oppure metti SURPLUS_NAS e SURPLUS_NAS_UTENTE fra le variabili d'ambiente."
    }
    Write-Host "Mando il pacchetto al NAS..." -ForegroundColor Cyan
    $versioni = Join-Path $dist 'versioni.json'
    # Anche la versione dell'app Android, letta dal suo build.gradle.kts: questo file
    # le contiene tutte e due, e scriverne solo una cancellerebbe l'altra sul NAS.
    $gradle = Get-Content (Join-Path (Split-Path -Parent $radicePc) 'app\build.gradle.kts') -Raw -Encoding UTF8
    $android = ''
    if ($gradle -match 'versionCode\s*=\s*(\d+)' ) {
        $codiceAndroid = [int]$Matches[1]
        if ($gradle -match 'versionName\s*=\s*"([^"]+)"') {
            $android = '"android":{"codice":' + $codiceAndroid + ',"nome":"' + $Matches[1] + '"},'
        }
    }
    $testoVersioni = '{"server":"1.0.0","pc":{"codice":' + $codiceVersione + ',"nome":"' + $nomeVersione + '"},' +
        $android +
        '"nota":"Gli aggiornamenti si installano da soli: sul computer quando chiudi Surplus, sul telefono quando lasci l''app."}'
    [System.IO.File]::WriteAllText($versioni, $testoVersioni, (New-Object System.Text.UTF8Encoding $false))
    # -O: il NAS parla il protocollo scp vecchio, con quello nuovo dice "No such file or directory".
    & scp -O -q "$zip" "$zip.sig" "$versioni" "$UtenteNas@${Nas}:$CartellaNas/"
    if ($LASTEXITCODE -ne 0) { throw "il pacchetto non e' arrivato sul NAS (codice $LASTEXITCODE)" }
    Write-Host "  pubblicato: versione $nomeVersione (codice $codiceVersione) su $Nas" -ForegroundColor Green
}

$mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
$nFile = (Get-ChildItem $staging -Recurse -File).Count
Write-Host ""
Write-Host "Fatto: Surplus $versione" -ForegroundColor Green
Write-Host "  zip:  $zip  ($mb MB, $nFile file nella cartella Surplus\)"
Write-Host ""
Write-Host "Prova rapida (cartella dati temporanea, niente avvio automatico, si chiude dopo 10 secondi):" -ForegroundColor DarkGray
Write-Host "  & '$exe' --no-installa --dati `"`$env:TEMP\surplus-prova`" --esci-dopo 10"
