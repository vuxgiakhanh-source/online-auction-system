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

Write-Host "Building auction-server..."
mvn -pl auction-server -am compile -DskipTests -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Packaging auction-server..."
mvn -pl auction-server -am package -DskipTests -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting ServerMain (JAR)..."
java -jar auction-server\target\auction-server-1.0-SNAPSHOT.jar
