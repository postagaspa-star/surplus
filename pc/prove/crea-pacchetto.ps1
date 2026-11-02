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
param()
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

Write-Host "Creo lo zip..." -ForegroundColor Cyan
Remove-Item $zip -ErrorAction SilentlyContinue
# -Path sulla cartella (senza \*): lo zip contiene la cartella Surplus\ con tutto dentro.
Compress-Archive -Path $staging -DestinationPath $zip -CompressionLevel Optimal

$mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
$nFile = (Get-ChildItem $staging -Recurse -File).Count
Write-Host ""
Write-Host "Fatto: Surplus $versione" -ForegroundColor Green
Write-Host "  zip:  $zip  ($mb MB, $nFile file nella cartella Surplus\)"
Write-Host ""
Write-Host "Prova rapida (cartella dati temporanea, niente avvio automatico, si chiude dopo 10 secondi):" -ForegroundColor DarkGray
Write-Host "  & '$exe' --no-installa --dati `"`$env:TEMP\surplus-prova`" --esci-dopo 10"
