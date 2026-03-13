#Requires -Version 5.1

<#
.SYNOPSIS
    Orabi Bread Counting - Board URL Discovery Script

.DESCRIPTION
    Replicates the 3-step discovery flow:
      1. Local network scan
      2. Cached URL
      3. Cloud discovery

    Compatible with Windows PowerShell 5.1+ with no external dependencies.
#>

# Relaunch in a visible console if needed
if ($Host.Name -ne 'ConsoleHost') {
    $argList = '-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $PSCommandPath
    Start-Process powershell.exe -ArgumentList $argList
    exit
}

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ============================================================================
# Configuration
# ============================================================================
$CLOUD_BASE_URL = 'https://tunnel-publish.mh-khaled-abas.workers.dev'
$BOARD_PORT = 8000

# Keep cloud/cached similar to app
$CLOUD_TIMEOUT_MS = 5000

# Faster local scan tuning for Windows PowerShell
$LOCAL_CONNECT_TIMEOUT_MS = 250
$LOCAL_HTTP_TIMEOUT_MS = 700
$LOCAL_MAX_CONCURRENCY = 64

$CACHE_DIR = Join-Path $env:USERPROFILE '.orabi-discovery'
$CACHE_FILE = Join-Path $CACHE_DIR 'tunnel-url.txt'

$STEP_LOCAL = 'Local network discovery'
$STEP_CACHED = 'Cached URL'
$STEP_CLOUD = 'Cloud discovery'

$script:RunStart = [System.Diagnostics.Stopwatch]::StartNew()

# ============================================================================
# Helpers
# ============================================================================
function Write-Trace {
    param(
        [Parameter(Mandatory = $true)][string]$Event,
        [string]$Detail = ''
    )

    $elapsedMs = [int64]$script:RunStart.ElapsedMilliseconds
    if ([string]::IsNullOrWhiteSpace($Detail)) {
        Write-Host ("[DiscoveryTrace] {0} | elapsedMs={1}" -f $Event, $elapsedMs) -ForegroundColor DarkGray
    }
    else {
        Write-Host ("[DiscoveryTrace] {0} | elapsedMs={1}, {2}" -f $Event, $elapsedMs, $Detail) -ForegroundColor DarkGray
    }
}

function Write-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Icon,
        [Parameter(Mandatory = $true)][string]$Label,
        [string]$Detail = '',
        [ConsoleColor]$Color = [ConsoleColor]::White
    )

    if ([string]::IsNullOrWhiteSpace($Detail)) {
        Write-Host ("{0} {1}" -f $Icon, $Label) -ForegroundColor $Color
    }
    else {
        Write-Host ("{0} {1} - {2}" -f $Icon, $Label, $Detail) -ForegroundColor $Color
    }
}

function Ensure-CacheDir {
    if (-not (Test-Path -LiteralPath $CACHE_DIR)) {
        New-Item -ItemType Directory -Path $CACHE_DIR -Force | Out-Null
    }
}

function Test-LocalNetworkUrl {
    param(
        [Parameter(Mandatory = $true)][string]$Url
    )

    try {
        $hostName = $Url -replace '^https?://', '' -replace '[/:].*$', ''
        $octets = $hostName -split '\.'
        if ($octets.Count -ne 4) { return $false }

        $a = [int]$octets[0]
        $b = [int]$octets[1]

        if ($a -eq 10) { return $true }
        if ($a -eq 172 -and $b -ge 16 -and $b -le 31) { return $true }
        if ($a -eq 192 -and $b -eq 168) { return $true }
        if ($a -eq 169 -and $b -eq 254) { return $true }
    }
    catch {
    }

    return $false
}

function Get-LocalPrivateIp {
    $adapters = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()

    foreach ($iface in $adapters) {
        if ($iface.OperationalStatus -ne [System.Net.NetworkInformation.OperationalStatus]::Up) { continue }
        if ($iface.NetworkInterfaceType -eq [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback) { continue }

        foreach ($addr in $iface.GetIPProperties().UnicastAddresses) {
            if ($addr.Address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { continue }

            $ip = $addr.Address.ToString()
            if (Test-LocalNetworkUrl -Url ("http://{0}" -f $ip)) {
                return $ip
            }
        }
    }

    return $null
}

function Get-CachedUrl {
    if (Test-Path -LiteralPath $CACHE_FILE) {
        $line = Get-Content -Path $CACHE_FILE -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $line) {
            $line = $line.Trim()
            if (-not [string]::IsNullOrWhiteSpace($line)) {
                return $line
            }
        }
    }

    return $null
}

function Save-CachedUrl {
    param(
        [Parameter(Mandatory = $true)][string]$Url
    )

    Ensure-CacheDir
    Set-Content -Path $CACHE_FILE -Value $Url -Encoding UTF8 -Force
}

function Invoke-WebProbe {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][int]$TimeoutMs
    )

    try {
        $req = [System.Net.HttpWebRequest]::Create($Url)
        $req.Method = 'GET'
        $req.Timeout = $TimeoutMs
        $req.ReadWriteTimeout = $TimeoutMs
        $req.AllowAutoRedirect = $true

        $resp = [System.Net.HttpWebResponse]$req.GetResponse()
        try {
            return ([int]$resp.StatusCode -eq 200)
        }
        finally {
            $resp.Close()
        }
    }
    catch {
        return $false
    }
}

function Get-CloudTunnelUrl {
    $requestUrl = '{0}/current' -f $CLOUD_BASE_URL.TrimEnd('/')

    Write-Trace -Event 'cloud_fetch_start' -Detail ("url={0}" -f $requestUrl)

    try {
        $req = [System.Net.HttpWebRequest]::Create($requestUrl)
        $req.Method = 'GET'
        $req.Timeout = $CLOUD_TIMEOUT_MS
        $req.ReadWriteTimeout = $CLOUD_TIMEOUT_MS
        $req.AllowAutoRedirect = $true

        $resp = [System.Net.HttpWebResponse]$req.GetResponse()
        try {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
            try {
                $body = $reader.ReadToEnd()
            }
            finally {
                $reader.Close()
            }
        }
        finally {
            $resp.Close()
        }

        Write-Trace -Event 'cloud_fetch_response' -Detail ("status=200, bodyLength={0}" -f $body.Length)

        $json = $null
        try {
            $json = $body | ConvertFrom-Json
        }
        catch {
            Write-Trace -Event 'cloud_fetch_parse' -Detail 'invalid_json=true'
            return $null
        }

        if ($null -ne $json -and $json.PSObject.Properties.Name -contains 'tunnelUrl') {
            $tunnelUrl = [string]$json.tunnelUrl
            if (-not [string]::IsNullOrWhiteSpace($tunnelUrl)) {
                return $tunnelUrl.Trim()
            }
        }

        Write-Trace -Event 'cloud_fetch_parse' -Detail 'empty_tunnel_url=true'
        return $null
    }
    catch {
        Write-Trace -Event 'cloud_fetch_exception' -Detail ("type={0}, message={1}" -f $_.Exception.GetType().Name, $_.Exception.Message)
        return $null
    }
}

# ============================================================================
# Local scan
# ============================================================================
function Invoke-LocalNetworkScan {
    param(
        [int]$Port = $BOARD_PORT
    )

    $localIp = Get-LocalPrivateIp
    if (-not $localIp) {
        Write-Trace -Event 'local_scan_no_local_ip' -Detail 'result=no_private_ip_found'
        return $null
    }

    $parts = $localIp -split '\.'
    $prefix = '{0}.{1}.{2}' -f $parts[0], $parts[1], $parts[2]
    $total = 254

    Write-Trace -Event 'local_scan_start' -Detail ("localIp={0}, prefix={1}, total={2}, port={3}" -f $localIp, $prefix, $total, $Port)

    $pool = [RunspaceFactory]::CreateRunspacePool(1, $LOCAL_MAX_CONCURRENCY)
    $pool.Open()

    $jobs = New-Object System.Collections.Generic.List[object]

    $probeScript = {
        param(
            [string]$BaseUrl,
            [int]$Port,
            [int]$ConnectTimeoutMs,
            [int]$HttpTimeoutMs
        )

        function Test-FastTcp {
            param(
                [string]$Host,
                [int]$Port,
                [int]$TimeoutMs
            )

            $tcp = New-Object System.Net.Sockets.TcpClient
            try {
                $async = $tcp.BeginConnect($Host, $Port, $null, $null)
                $ok = $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
                if (-not $ok) { return $false }
                $tcp.EndConnect($async)
                return $true
            }
            catch {
                return $false
            }
            finally {
                $tcp.Close()
            }
        }

        $uri = [Uri]$BaseUrl
        if (-not (Test-FastTcp -Host $uri.Host -Port $Port -TimeoutMs $ConnectTimeoutMs)) {
            return $null
        }

        try {
            $probeUrl = '{0}/whoami' -f $BaseUrl.TrimEnd('/')

            $req = [System.Net.HttpWebRequest]::Create($probeUrl)
            $req.Method = 'GET'
            $req.Timeout = $HttpTimeoutMs
            $req.ReadWriteTimeout = $HttpTimeoutMs
            $req.AllowAutoRedirect = $true

            $resp = [System.Net.HttpWebResponse]$req.GetResponse()
            try {
                if ([int]$resp.StatusCode -eq 200) {
                    return $BaseUrl
                }
            }
            finally {
                $resp.Close()
            }
        }
        catch {
        }

        return $null
    }

    for ($i = 1; $i -le $total; $i++) {
        $baseUrl = if ($Port -eq 80) {
            'http://{0}.{1}' -f $prefix, $i
        }
        else {
            'http://{0}.{1}:{2}' -f $prefix, $i, $Port
        }

        $ps = [PowerShell]::Create()
        $ps.RunspacePool = $pool
        [void]$ps.AddScript($probeScript)
        [void]$ps.AddArgument($baseUrl)
        [void]$ps.AddArgument($Port)
        [void]$ps.AddArgument($LOCAL_CONNECT_TIMEOUT_MS)
        [void]$ps.AddArgument($LOCAL_HTTP_TIMEOUT_MS)

        $async = $ps.BeginInvoke()

        $job = [pscustomobject]@{
            PowerShell = $ps
            Async      = $async
            BaseUrl    = $baseUrl
            Done       = $false
        }

        [void]$jobs.Add($job)
    }

    $scanned = 0
    $foundUrl = $null
    $remaining = $total

    try {
        while ($remaining -gt 0 -and -not $foundUrl) {
            foreach ($job in $jobs) {
                if ($job.Done) { continue }

                if ($job.Async.IsCompleted) {
                    $result = $null
                    try {
                        $result = $job.PowerShell.EndInvoke($job.Async)
                    }
                    catch {
                        $result = $null
                    }
                    finally {
                        $job.PowerShell.Dispose()
                        $job.Done = $true
                        $scanned++
                        $remaining--
                    }

                    if ($scanned -eq 1 -or ($scanned % 25 -eq 0) -or $scanned -eq $total) {
                        Write-Trace -Event 'local_scan_progress' -Detail ("scanned={0}, total={1}" -f $scanned, $total)
                        $pct = [int](($scanned * 100) / $total)
                        Write-Host ("`r  [SCAN] {0}/{1} ({2}%)" -f $scanned, $total, $pct) -NoNewline -ForegroundColor Yellow
                    }

                    if ($null -ne $result -and $result.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace([string]$result[0])) {
                        $foundUrl = [string]$result[0]
                        break
                    }
                }
            }

            if (-not $foundUrl) {
                Start-Sleep -Milliseconds 20
            }
        }

        Write-Host ''

        return $foundUrl
    }
    finally {
        foreach ($job in $jobs) {
            try {
                if (-not $job.Done) {
                    $job.PowerShell.Stop()
                    $job.PowerShell.Dispose()
                }
            }
            catch {
            }
        }

        $pool.Close()
        $pool.Dispose()
    }
}

# ============================================================================
# Main flow
# ============================================================================
function Invoke-Discovery {
    Write-Host ''
    Write-Host '====================================================' -ForegroundColor Cyan
    Write-Host '   Orabi Bread Counting - Board Discovery' -ForegroundColor Cyan
    Write-Host '====================================================' -ForegroundColor Cyan
    Write-Host ''

    Write-Trace -Event 'run_start' -Detail 'trigger=script_launch'

    $completed = New-Object System.Collections.Generic.List[object]

    # Step 1: Local
    Write-Trace -Event 'step_start' -Detail 'step=local_scan'
    Write-Step -Icon '[...]' -Label $STEP_LOCAL -Detail 'Scanning local subnet' -Color Yellow

    $localUrl = Invoke-LocalNetworkScan -Port $BOARD_PORT
    if ($localUrl) {
        Write-Trace -Event 'local_scan_result' -Detail ("result=found, url={0}" -f $localUrl)
        Save-CachedUrl -Url $localUrl
        Write-Trace -Event 'cache_save' -Detail ("source=local_scan, url={0}" -f $localUrl)
        Write-Step -Icon '[OK]' -Label $STEP_LOCAL -Detail 'Board found on local network' -Color Green
        Write-Trace -Event 'state_update' -Detail ("Connected via=local_scan, url={0}" -f $localUrl)
        return $localUrl
    }

    Write-Trace -Event 'local_scan_result' -Detail 'result=not_found'
    Write-Step -Icon '[X]' -Label $STEP_LOCAL -Detail 'Board not found on local network' -Color Red

    # Step 2: Cached
    Write-Trace -Event 'step_start' -Detail 'step=cached'
    $cachedUrl = Get-CachedUrl
    $cachedIsLocal = $false
    if ($cachedUrl) {
        $cachedIsLocal = Test-LocalNetworkUrl -Url $cachedUrl
    }

    if ($cachedUrl -and -not $cachedIsLocal) {
        Write-Trace -Event 'cache_lookup' -Detail ("result=hit_remote, url={0}" -f $cachedUrl)
        Write-Step -Icon '[...]' -Label $STEP_CACHED -Detail 'Trying cached URL' -Color Yellow

        $ok = Invoke-WebProbe -Url ('{0}/whoami' -f $cachedUrl.TrimEnd('/')) -TimeoutMs $CLOUD_TIMEOUT_MS
        Write-Trace -Event 'verify_cached' -Detail ("url={0}, success={1}" -f $cachedUrl, $ok)

        if ($ok) {
            Write-Step -Icon '[OK]' -Label $STEP_CACHED -Detail 'Connected using cached URL' -Color Green
            Write-Trace -Event 'state_update' -Detail ("Connected via=cached, url={0}" -f $cachedUrl)
            return $cachedUrl
        }

        Write-Step -Icon '[X]' -Label $STEP_CACHED -Detail 'Cached URL is unavailable' -Color Red
    }
    elseif ($cachedIsLocal) {
        Write-Trace -Event 'cache_lookup' -Detail ("result=hit_local_skipped, url={0}" -f $cachedUrl)
        Write-Step -Icon '[X]' -Label $STEP_CACHED -Detail 'Cached URL is local and was already scanned' -Color Red
    }
    else {
        Write-Trace -Event 'cache_lookup' -Detail 'result=miss'
        Write-Step -Icon '[X]' -Label $STEP_CACHED -Detail 'No cached URL found' -Color Red
    }

    # Step 3: Cloud
    Write-Trace -Event 'step_start' -Detail 'step=cloud'
    Write-Step -Icon '[...]' -Label $STEP_CLOUD -Detail 'Fetching tunnel URL from cloud' -Color Yellow

    $tunnelUrl = Get-CloudTunnelUrl
    if ($tunnelUrl) {
        Write-Trace -Event 'cloud_fetch' -Detail ("result=success, url={0}" -f $tunnelUrl)
        Write-Step -Icon '[...]' -Label $STEP_CLOUD -Detail 'Verifying cloud tunnel' -Color Yellow

        $ok = Invoke-WebProbe -Url ('{0}/whoami' -f $tunnelUrl.TrimEnd('/')) -TimeoutMs $CLOUD_TIMEOUT_MS
        Write-Trace -Event 'verify_cloud' -Detail ("url={0}, success={1}" -f $tunnelUrl, $ok)

        if ($ok) {
            Save-CachedUrl -Url $tunnelUrl
            Write-Trace -Event 'cache_save' -Detail ("source=cloud, url={0}" -f $tunnelUrl)
            Write-Step -Icon '[OK]' -Label $STEP_CLOUD -Detail 'Connected using cloud tunnel' -Color Green
            Write-Trace -Event 'state_update' -Detail ("Connected via=cloud, url={0}" -f $tunnelUrl)
            return $tunnelUrl
        }

        Write-Step -Icon '[X]' -Label $STEP_CLOUD -Detail 'Tunnel found but board did not respond' -Color Red
    }
    else {
        Write-Trace -Event 'cloud_fetch' -Detail 'result=failed'
        Write-Step -Icon '[X]' -Label $STEP_CLOUD -Detail 'Could not fetch cloud tunnel URL' -Color Red
    }

    Write-Trace -Event 'state_update' -Detail 'Failed'
    return $null
}

# ============================================================================
# Entry point
# ============================================================================
try {
    $boardUrl = Invoke-Discovery

    Write-Host ''
    Write-Host '----------------------------------------------------' -ForegroundColor DarkGray

    if ($boardUrl) {
        Write-Host ('[OK] Board found: {0}' -f $boardUrl) -ForegroundColor Green
        Write-Host 'Opening browser...' -ForegroundColor Green
        Write-Trace -Event 'run_finish' -Detail ("success=true, url={0}" -f $boardUrl)
        Start-Sleep -Seconds 1
        Start-Process $boardUrl
        Write-Host ''
        Write-Host 'You can close this window now.' -ForegroundColor Cyan
    }
    else {
        Write-Host '[X] Discovery failed in all steps.' -ForegroundColor Red
        Write-Host 'Please verify:' -ForegroundColor Yellow
        Write-Host '  - Board is connected to LAN or Internet' -ForegroundColor Yellow
        Write-Host '  - Cloud worker is available' -ForegroundColor Yellow
        Write-Trace -Event 'run_finish' -Detail 'success=false'
    }
}
catch {
    Write-Host ''
    Write-Host ('[X] Unexpected error: {0}' -f $_.Exception.Message) -ForegroundColor Red
    Write-Trace -Event 'run_error' -Detail ("type={0}, message={1}" -f $_.Exception.GetType().Name, $_.Exception.Message)
}

Write-Host ''
Write-Host 'Press any key to close...' -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
