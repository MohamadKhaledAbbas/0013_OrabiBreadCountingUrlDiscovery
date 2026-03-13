#Requires -Version 5.1
<#
.SYNOPSIS
    Orabi Bread Counting - Board URL Discovery Script
    أداة اكتشاف عنوان لوحة عدّ عيش عرابي

.DESCRIPTION
    Replicates the exact 3-step discovery flow of the KMP app:
      Step 1 – Local network scan  (البحث في الشبكة المحلية)
      Step 2 – Cached tunnel URL   (الاتصال المحفوظ)
      Step 3 – Cloud discovery     (الاتصال السحابي)

    Compatible with PowerShell 5.1+ (Windows built-in). No external dependencies.

.NOTES
    Launch by double-clicking OrabiBreadCountingDiscovery.cmd (recommended).
    The .cmd file calls PowerShell with -ExecutionPolicy Bypass so the script
    runs on any Windows machine without additional configuration.
    The console window stays open after completion so the user can read the output.
#>

# ── UTF-8 output so Arabic renders correctly in the console ──────────────────
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# ─────────────────────────────────────────────────────────────────────────────
# Configuration  (mirrors DiscoveryConfig.kt exactly)
# ─────────────────────────────────────────────────────────────────────────────
$CLOUD_BASE_URL              = 'https://tunnel-publish.mh-khaled-abas.workers.dev'
$BOARD_PORT                  = 8000
$CLOUD_TIMEOUT_MS            = 5000    # request timeout for cloud / cached probes
$CLOUD_CONNECT_TIMEOUT_MS    = 3000    # connect timeout for cloud / cached probes
$LOCAL_SCAN_TIMEOUT_MS       = 2000    # request timeout for each local probe
$LOCAL_SCAN_CONNECT_TIMEOUT_MS = 1000  # connect timeout for each local probe

# Cache file path  (matches the problem-spec: {user.home}/.orabi-discovery/tunnel-url.txt)
$CACHE_DIR  = Join-Path $env:USERPROFILE '.orabi-discovery'
$CACHE_FILE = Join-Path $CACHE_DIR 'tunnel-url.txt'

# Arabic step labels  (mirrors ArabicLabels object in App.kt)
$STEP_LOCAL  = 'البحث في الشبكة المحلية'
$STEP_CACHED = 'الاتصال المحفوظ'
$STEP_CLOUD  = 'الاتصال السحابي'

# ─────────────────────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────────────────────

# Global run timer (start of the current discovery run)
$script:RunStartMs = 0

function Write-Trace {
    <#
    .SYNOPSIS Emits a [DiscoveryTrace] line matching the KMP app format. #>
    param(
        [string]$Event,
        [string]$Detail = ''
    )
    $elapsedMs = [long]([System.Diagnostics.Stopwatch]::GetTimestamp() / [System.Diagnostics.Stopwatch]::Frequency * 1000) `
                 - $script:RunStartMs
    $suffix = if ($Detail) { ", $Detail" } else { '' }
    Write-Host "[DiscoveryTrace] $Event | elapsedMs=$elapsedMs$suffix" -ForegroundColor DarkGray
}

function Write-Step {
    <#
    .SYNOPSIS Prints a coloured step-status line. #>
    param(
        [string]$Icon,
        [string]$Label,
        [string]$Detail,
        [System.ConsoleColor]$Color = 'White'
    )
    Write-Host "$Icon $Label" -ForegroundColor $Color -NoNewline
    if ($Detail) { Write-Host "  — $Detail" -ForegroundColor $Color } else { Write-Host '' }
}

function Test-LocalNetworkUrl {
    <#
    .SYNOPSIS
        Returns $true when the URL's host is a private/local IPv4 address.
        Mirrors isLocalNetworkUrl() in App.kt.
    #>
    param([string]$Url)
    try {
        $hostname = $Url -replace '^https?://', '' -replace '[/:].*$', ''
        $octets = $hostname -split '\.'
        if ($octets.Count -ne 4) { return $false }
        $a = [int]$octets[0]; $b = [int]$octets[1]
        if ($a -eq 10)                           { return $true }  # 10.0.0.0/8
        if ($a -eq 172 -and $b -ge 16 -and $b -le 31) { return $true }  # 172.16.0.0/12
        if ($a -eq 192 -and $b -eq 168)          { return $true }  # 192.168.0.0/16
        if ($a -eq 169 -and $b -eq 254)          { return $true }  # 169.254.0.0/16 link-local
    } catch { }
    return $false
}

function Get-LocalPrivateIp {
    <#
    .SYNOPSIS
        Returns the first private (RFC 1918) IPv4 address of this machine.
        Mirrors NetworkUtils.jvm.kt getLocalIpAddress().
    #>
    $adapters = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()
    foreach ($iface in $adapters) {
        if ($iface.OperationalStatus -ne 'Up') { continue }
        if ($iface.NetworkInterfaceType -eq 'Loopback') { continue }
        foreach ($addr in $iface.GetIPProperties().UnicastAddresses) {
            if ($addr.Address.AddressFamily -ne 'InterNetwork') { continue }
            $ip = $addr.Address.ToString()
            if (Test-LocalNetworkUrl "http://$ip") { return $ip }
        }
    }
    return $null
}

function Invoke-WebProbe {
    <#
    .SYNOPSIS
        Sends a GET request to $Url and returns $true when the HTTP status is 200.
        Uses pure .NET WebRequest so it works on PS 5.1 without Invoke-WebRequest quirks.
    #>
    param(
        [string]$Url,
        [int]$TimeoutMs    = $CLOUD_TIMEOUT_MS
    )
    try {
        $req = [System.Net.HttpWebRequest]::Create($Url)
        $req.Method          = 'GET'
        $req.Timeout         = $TimeoutMs
        $req.ReadWriteTimeout = $TimeoutMs
        $req.AllowAutoRedirect = $true
        $resp = $req.GetResponse()
        $statusCode = [int]$resp.StatusCode
        $resp.Close()
        return ($statusCode -eq 200)
    } catch [System.Net.WebException] {
        # Non-200 responses come here as well; treat as failure
        return $false
    } catch {
        return $false
    }
}

function Get-CloudTunnelUrl {
    <#
    .SYNOPSIS
        Fetches the tunnel URL from the Cloudflare Worker (/current endpoint).
        Returns the tunnel URL string, or $null on failure.
        Mirrors DiscoveryEngine.fetchTunnelUrl().
    #>
    $requestUrl = "$CLOUD_BASE_URL/current"
    Write-Trace 'cloud_fetch_start' "url=$requestUrl"
    try {
        $req = [System.Net.HttpWebRequest]::Create($requestUrl)
        $req.Method           = 'GET'
        $req.Timeout          = $CLOUD_TIMEOUT_MS
        $req.ReadWriteTimeout = $CLOUD_TIMEOUT_MS
        $req.AllowAutoRedirect = $true
        $resp    = $req.GetResponse()
        $stream  = $resp.GetResponseStream()
        $reader  = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        $body    = $reader.ReadToEnd()
        $reader.Close(); $resp.Close()

        Write-Trace 'cloud_fetch_response' "status=200, bodyLength=$($body.Length)"

        # Parse JSON  { "tunnelUrl": "...", "updatedAt": "..." }
        # Use .NET JavaScriptSerializer (available in PS 5.1) or simple regex
        $match = [regex]::Match($body, '"tunnelUrl"\s*:\s*"([^"]+)"')
        if ($match.Success -and $match.Groups[1].Value.Trim()) {
            return $match.Groups[1].Value.Trim()
        }
        Write-Trace 'cloud_fetch_parse' 'empty_tunnel_url=true'
        return $null
    } catch [System.Net.WebException] {
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        Write-Trace 'cloud_fetch_exception' "type=WebException, status=$statusCode, message=$($_.Exception.Message)"
        return $null
    } catch {
        Write-Trace 'cloud_fetch_exception' "type=$($_.Exception.GetType().Name), message=$($_.Exception.Message)"
        return $null
    }
}

# ─── Cache helpers ────────────────────────────────────────────────────────────

function Get-CachedUrl {
    if (Test-Path $CACHE_FILE) {
        $url = (Get-Content $CACHE_FILE -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -First 1).Trim()
        if ($url) { return $url }
    }
    return $null
}

function Save-CachedUrl {
    param([string]$Url)
    if (-not (Test-Path $CACHE_DIR)) {
        New-Item -ItemType Directory -Path $CACHE_DIR -Force | Out-Null
    }
    Set-Content -Path $CACHE_FILE -Value $Url -Encoding UTF8 -Force
}

# ─────────────────────────────────────────────────────────────────────────────
# Step 1 – Local network scan
# ─────────────────────────────────────────────────────────────────────────────

function Invoke-LocalNetworkScan {
    <#
    .SYNOPSIS
        Scans all 254 IPs in the local /24 subnet in parallel using runspaces.
        Returns the first base URL that responds HTTP 200 to /whoami, or $null.
        Mirrors DiscoveryEngine.scanLocalNetwork() with Kotlin coroutines.
    #>
    param([int]$Port = $BOARD_PORT)

    $localIp = Get-LocalPrivateIp
    if (-not $localIp) {
        Write-Trace 'local_scan_no_local_ip' 'result=no_private_ip_found'
        return $null
    }

    $parts  = $localIp -split '\.'
    $prefix = "$($parts[0]).$($parts[1]).$($parts[2])"
    $total  = 254

    Write-Trace 'local_scan_start' "localIp=$localIp, prefix=$prefix, total=$total, port=$Port"

    # Script block executed by each runspace
    $probeScript = {
        param([string]$BaseUrl, [int]$TimeoutMs)
        try {
            $req = [System.Net.HttpWebRequest]::Create("$BaseUrl/whoami")
            $req.Method           = 'GET'
            $req.Timeout          = $TimeoutMs
            $req.ReadWriteTimeout = $TimeoutMs
            $req.AllowAutoRedirect = $true
            $resp = $req.GetResponse()
            $code = [int]$resp.StatusCode
            $resp.Close()
            if ($code -eq 200) { return $BaseUrl }
        } catch { }
        return $null
    }

    # Create a runspace pool (limit concurrency to avoid socket exhaustion)
    $maxConcurrency = [Math]::Min($total, 100)
    $pool = [System.Management.Automation.Runspaces.RunspaceFactory]::CreateRunspacePool(1, $maxConcurrency)
    $pool.Open()

    # Launch all probes
    $jobs = [System.Collections.Generic.List[hashtable]]::new()
    for ($i = 1; $i -le $total; $i++) {
        $baseUrl = if ($Port -eq 80) { "http://$prefix.$i" } else { "http://$prefix.${i}:$Port" }
        $ps = [System.Management.Automation.PowerShell]::Create()
        $ps.RunspacePool = $pool
        [void]$ps.AddScript($probeScript)
        [void]$ps.AddArgument($baseUrl)
        [void]$ps.AddArgument($LOCAL_SCAN_TIMEOUT_MS)
        $handle = $ps.BeginInvoke()
        $jobs.Add(@{ PS = $ps; Handle = $handle; Url = $baseUrl })
    }

    # Collect results with progress reporting
    $scanned    = 0
    $foundUrl   = $null
    $lastReport = 0

    foreach ($job in $jobs) {
        $result = $job.PS.EndInvoke($job.Handle)
        $job.PS.Dispose()
        $scanned++

        # Progress trace at milestones (mirrors the KMP onProgress callback)
        if ($scanned -eq 1 -or ($scanned % 25 -eq 0) -or $scanned -eq $total) {
            Write-Trace 'local_scan_progress' "scanned=$scanned, total=$total"
            $pct = [int](($scanned / $total) * 100)
            Write-Host "`r  ⏳ فحص الشبكة المحلية… $scanned / $total  ($pct%)" `
                -ForegroundColor Yellow -NoNewline
        }

        if ($null -ne $result -and $result.Count -gt 0 -and $result[0]) {
            $foundUrl = $result[0]
            break
        }
    }
    Write-Host ''  # newline after progress line

    # Clean up remaining jobs and the pool
    foreach ($job in $jobs) {
        try {
            if (-not $job.Handle.IsCompleted) { $job.PS.Stop() }
            $job.PS.Dispose()
        } catch { }
    }
    $pool.Close()
    $pool.Dispose()

    return $foundUrl
}

# ─────────────────────────────────────────────────────────────────────────────
# Main discovery flow  (Local → Cached → Cloud)
# ─────────────────────────────────────────────────────────────────────────────

function Invoke-Discovery {
    # Record start time for trace timestamps
    $script:RunStartMs = [long]([System.Diagnostics.Stopwatch]::GetTimestamp() /
                                [System.Diagnostics.Stopwatch]::Frequency * 1000)

    Write-Host ''
    Write-Host '════════════════════════════════════════════════════' -ForegroundColor Cyan
    Write-Host '   🍞  اكتشاف لوحة عدّ عيش عرابي' -ForegroundColor Cyan
    Write-Host '════════════════════════════════════════════════════' -ForegroundColor Cyan
    Write-Host ''

    Write-Trace 'run_start' 'trigger=script_launch'

    $completed = [System.Collections.Generic.List[hashtable]]::new()

    # ── Step 1: Local network scan ───────────────────────────────────────────
    Write-Trace 'step_start' 'step=local_scan'
    Write-Step '⏳' $STEP_LOCAL 'جارٍ فحص عناوين الشبكة المحلية…' Yellow

    $localUrl = Invoke-LocalNetworkScan -Port $BOARD_PORT
    Write-Trace 'local_scan_result' $(if ($localUrl) { "result=found, url=$localUrl" } else { 'result=not_found' })

    if ($localUrl) {
        Save-CachedUrl $localUrl
        Write-Trace 'cache_save' "source=local_scan, url=$localUrl"
        $completed.Add(@{ Label = $STEP_LOCAL; Success = $true; Detail = 'تم العثور على لوحة العدّ في الشبكة المحلية' })
        Write-Step '✅' $STEP_LOCAL 'تم العثور على لوحة العدّ في الشبكة المحلية' Green
        Write-Trace 'state_update' "Connected via=local_scan, url=$localUrl"
        return $localUrl
    }
    $completed.Add(@{ Label = $STEP_LOCAL; Success = $false; Detail = 'لم يتم العثور على لوحة العدّ في الشبكة المحلية' })
    Write-Step '❌' $STEP_LOCAL 'لم يتم العثور على لوحة العدّ في الشبكة المحلية' Red

    # ── Step 2: Cached tunnel URL ────────────────────────────────────────────
    Write-Trace 'step_start' 'step=cached'
    $cachedUrl      = Get-CachedUrl
    $cachedIsLocal  = $cachedUrl -and (Test-LocalNetworkUrl $cachedUrl)

    $cacheTraceDetail = switch ($true) {
        { -not $cachedUrl }   { 'result=miss'; break }
        { $cachedIsLocal }    { "result=hit_local_skipped, url=$cachedUrl"; break }
        default               { "result=hit_remote, url=$cachedUrl" }
    }
    Write-Trace 'cache_lookup' $cacheTraceDetail

    if ($cachedUrl -and -not $cachedIsLocal) {
        Write-Step '⏳' $STEP_CACHED 'جارٍ محاولة الاتصال بالعنوان المحفوظ سابقاً…' Yellow
        Write-Trace 'state_update' 'Discovering step=cached'

        $ok = Invoke-WebProbe "$cachedUrl/whoami" -TimeoutMs $CLOUD_TIMEOUT_MS
        Write-Trace 'verify_cached' "url=$cachedUrl, success=$ok"

        if ($ok) {
            $completed.Add(@{ Label = $STEP_CACHED; Success = $true; Detail = 'تم الاتصال بالعنوان المحفوظ بنجاح' })
            Write-Step '✅' $STEP_CACHED 'تم الاتصال بالعنوان المحفوظ بنجاح' Green
            Write-Trace 'state_update' "Connected via=cached, url=$cachedUrl"
            return $cachedUrl
        }
        $completed.Add(@{ Label = $STEP_CACHED; Success = $false; Detail = 'العنوان المحفوظ غير متاح حالياً' })
        Write-Step '❌' $STEP_CACHED 'العنوان المحفوظ غير متاح حالياً' Red
    } elseif ($cachedIsLocal) {
        $completed.Add(@{ Label = $STEP_CACHED; Success = $false; Detail = 'العنوان المحفوظ محلي — تم فحصه بالفعل' })
        Write-Step '❌' $STEP_CACHED 'العنوان المحفوظ محلي — تم فحصه بالفعل' Red
    } else {
        $completed.Add(@{ Label = $STEP_CACHED; Success = $false; Detail = 'لا يوجد عنوان محفوظ مسبقاً' })
        Write-Step '❌' $STEP_CACHED 'لا يوجد عنوان محفوظ مسبقاً' Red
    }

    # ── Step 3: Cloud discovery ──────────────────────────────────────────────
    Write-Trace 'step_start' 'step=cloud'
    Write-Step '⏳' $STEP_CLOUD 'جارٍ جلب عنوان النفق من الخادم السحابي…' Yellow
    Write-Trace 'state_update' 'Discovering step=cloud fetch'

    $tunnelUrl = Get-CloudTunnelUrl
    Write-Trace 'cloud_fetch' $(if ($tunnelUrl) { "result=success, url=$tunnelUrl" } else { 'result=failed' })

    if ($tunnelUrl) {
        Write-Step '⏳' $STEP_CLOUD 'جارٍ التحقق من اتصال النفق السحابي…' Yellow
        Write-Trace 'state_update' 'Discovering step=cloud verify'

        $ok = Invoke-WebProbe "$tunnelUrl/whoami" -TimeoutMs $CLOUD_TIMEOUT_MS
        Write-Trace 'verify_cloud' "url=$tunnelUrl, success=$ok"

        if ($ok) {
            Save-CachedUrl $tunnelUrl
            Write-Trace 'cache_save' "source=cloud, url=$tunnelUrl"
            $completed.Add(@{ Label = $STEP_CLOUD; Success = $true; Detail = 'تم الاتصال عبر النفق السحابي بنجاح' })
            Write-Step '✅' $STEP_CLOUD 'تم الاتصال عبر النفق السحابي بنجاح' Green
            Write-Trace 'state_update' "Connected via=cloud, url=$tunnelUrl"
            return $tunnelUrl
        }
        $completed.Add(@{ Label = $STEP_CLOUD; Success = $false; Detail = 'تم العثور على النفق لكن لوحة العدّ لا تستجيب' })
        Write-Step '❌' $STEP_CLOUD 'تم العثور على النفق لكن لوحة العدّ لا تستجيب' Red
    } else {
        $completed.Add(@{ Label = $STEP_CLOUD; Success = $false; Detail = 'تعذّر الوصول إلى الخادم السحابي' })
        Write-Step '❌' $STEP_CLOUD 'تعذّر الوصول إلى الخادم السحابي' Red
    }

    Write-Trace 'state_update' "Failed completedSteps=$($completed.Count)"
    return $null
}

# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────

try {
    $boardUrl = Invoke-Discovery

    Write-Host ''
    Write-Host '────────────────────────────────────────────────────' -ForegroundColor DarkGray

    if ($boardUrl) {
        Write-Host "✅ تم العثور على لوحة العدّ: $boardUrl" -ForegroundColor Green
        Write-Host '   جارٍ فتح المتصفح…' -ForegroundColor Green
        Write-Trace 'run_finish' "success=true, url=$boardUrl"
        Start-Sleep -Seconds 1
        Start-Process $boardUrl
        Write-Host ''
        Write-Host '   تم فتح اللوحة في المتصفح. يمكنك إغلاق هذه النافذة.' -ForegroundColor Cyan
    } else {
        Write-Host '❌ فشل الاتصال بلوحة العدّ في جميع المحاولات.' -ForegroundColor Red
        Write-Host '   تأكد من:' -ForegroundColor Yellow
        Write-Host '     • أن اللوحة متصلة بالشبكة أو بالإنترنت' -ForegroundColor Yellow
        Write-Host '     • أن الخادم السحابي يعمل' -ForegroundColor Yellow
        Write-Trace 'run_finish' 'success=false'
    }
} catch {
    Write-Host ''
    Write-Host "❌ خطأ غير متوقع: $($_.Exception.Message)" -ForegroundColor Red
    Write-Trace 'run_error' "type=$($_.Exception.GetType().Name), message=$($_.Exception.Message)"
}

Write-Host ''
Write-Host 'اضغط أي مفتاح للإغلاق…' -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')
