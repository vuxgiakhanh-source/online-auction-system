# Chạy ServerMain (IDE hoặc Maven). Cần Docker Desktop.
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

function Use-Jdk17 {
    $candidates = @()
    if ($env:JAVA_17_HOME) { $candidates += $env:JAVA_17_HOME }
    $candidates += @(
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-17"
    )
    $candidates += (Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jdk-17*" } | ForEach-Object { $_.FullName })
    $candidates += (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jdk-17*" } | ForEach-Object { $_.FullName })

    foreach ($jdkHome in ($candidates | Select-Object -Unique)) {
        $javaExe = Join-Path $jdkHome "bin\java.exe"
        if (Test-Path $javaExe) {
            $env:JAVA_HOME = $jdkHome
            $env:Path = "$jdkHome\bin;$env:Path"
            Write-Host "Using JDK 17: $jdkHome"
            return
        }
    }

    Write-Error "Không tìm thấy JDK 17. Hãy cài JDK 17 hoặc đặt biến JAVA_17_HOME trỏ tới thư mục JDK 17."
    exit 1
}

Use-Jdk17

Write-Host "Starting MySQL (docker compose up db -d)..."
docker compose up db -d

Write-Host "Waiting for MySQL on localhost:3307..."
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("127.0.0.1", 3307)
        $tcp.Close()
        $ready = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $ready) {
    Write-Error "MySQL chưa lên cổng 3307. Kiểm tra: docker ps"
    exit 1
}

# Neu loi thieu cot DB: docker compose down -v ; docker compose up db -d

function Test-ServerPortsFree {
    $blocked = @()
    foreach ($p in @(8080, 8081)) {
        $conn = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn) { $blocked += [PSCustomObject]@{ Port = $p; Pid = $conn.OwningProcess } }
    }
    if ($blocked.Count -eq 0) { return }

    Write-Host ""
    Write-Host "Cannot start server - ports already in use:" -ForegroundColor Red
    foreach ($b in $blocked) {
        $proc = Get-Process -Id $b.Pid -ErrorAction SilentlyContinue
        $name = if ($proc) { $proc.ProcessName } else { "?" }
        Write-Host "  Port $($b.Port) -> PID $($b.Pid) ($name)"
    }
    Write-Host "Stop the old server (often a previous java -jar):" -ForegroundColor Yellow
    Write-Host "  Stop-Process -Id $($blocked[0].Pid) -Force"
    Write-Host "Or use other ports:" -ForegroundColor Yellow
    Write-Host '  $env:SERVER_PORT=8082; $env:IMAGE_SERVER_PORT=8083; .\scripts\run-server-main.ps1'
    Write-Host ""
    exit 1
}

Test-ServerPortsFree

# Maven -D flag must be quoted for PowerShell (see $mvnSkipTests below)
$mvnSkipTests = "-Dmaven.test.skip=true"

Write-Host "Building auction-server..."
mvn -pl auction-server -am compile $mvnSkipTests -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Packaging auction-server..."
mvn -pl auction-server -am package $mvnSkipTests -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting ServerMain (JAR)..."
java -jar auction-server\target\auction-server-1.0-SNAPSHOT.jar
