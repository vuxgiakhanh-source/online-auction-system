# Chạy ServerMain (IDE hoặc Maven). Cần Docker Desktop.
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

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

Write-Host "Building auction-server..."
mvn -pl auction-server -am compile -DskipTests -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Packaging auction-server..."
mvn -pl auction-server -am package -DskipTests -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting ServerMain (JAR)..."
java -jar auction-server\target\auction-server-1.0-SNAPSHOT.jar
